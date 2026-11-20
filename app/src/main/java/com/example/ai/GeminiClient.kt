package com.example.ai

import com.example.accessibility.ScreenSnapshot
import com.example.domain.model.ActionType
import com.example.domain.model.AgentDecision
import com.example.domain.model.AiSettings
import com.example.domain.model.GeminiModelOption
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.VoiceLanguage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

sealed class GeminiResult<out T> {
    data class Success<T>(
        val data: T,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0
    ) : GeminiResult<T>()

    data class Failure(
        val type: ErrorType,
        val message: String
    ) : GeminiResult<Nothing>()
}

enum class ErrorType {
    MISSING_API_KEY,
    INVALID_API_KEY,
    QUOTA_EXCEEDED,
    NETWORK_ERROR,
    PARSE_ERROR,
    API_ERROR
}

class GeminiClient(
    private val service: GeminiApiService = createDefaultService(),
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) {
    private val errorAdapter = moshi.adapter(GeminiErrorEnvelope::class.java)
    private val decisionAdapter = moshi.adapter(AgentDecisionDto::class.java)

    suspend fun testConnection(apiKey: String): GeminiResult<List<GeminiModelOption>> {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return GeminiResult.Failure(
                ErrorType.MISSING_API_KEY,
                "Please enter a Gemini API key before testing."
            )
        }
        return try {
            val response = service.listModels(trimmed)
            if (response.isSuccessful) {
                val rawModels = response.body()?.models.orEmpty()
                val supported = rawModels
                    .filter { model ->
                        val id = model.name.removePrefix("models/")
                        val methods = model.supportedGenerationMethods.orEmpty()
                        id.startsWith("gemini-") &&
                            "generateContent" in methods &&
                            !id.contains("embedding") &&
                            !id.contains("vision-only")
                    }
                    .map { model ->
                        val id = model.name.removePrefix("models/")
                        GeminiModelOption(
                            id = id,
                            displayName = model.displayName ?: id,
                            description = model.description.orEmpty().take(120)
                        )
                    }
                    .sortedWith(
                        compareByDescending<GeminiModelOption> { it.id.contains("2.5") }
                            .thenByDescending { it.id.contains("2.0") }
                            .thenByDescending { it.id.contains("flash") }
                            .thenBy { it.id }
                    )

                if (supported.isEmpty()) {
                    GeminiResult.Success(AiSettings.DEFAULT_MODELS)
                } else {
                    GeminiResult.Success(supported)
                }
            } else {
                mapHttpFailure(response)
            }
        } catch (e: Exception) {
            mapException(e)
        }
    }

    suspend fun planNextStep(
        apiKey: String,
        settings: AiSettings,
        userCommand: String,
        stepIndex: Int,
        stepHistory: List<String>,
        screenSnapshot: ScreenSnapshot?,
        installedApps: List<InstalledAppInfo>
    ): GeminiResult<AgentDecision> {
        if (apiKey.isBlank()) {
            return GeminiResult.Failure(
                ErrorType.MISSING_API_KEY,
                "Gemini API key is not configured. Open AI Settings to add your key."
            )
        }

        val systemPrompt = buildPlannerSystemPrompt(settings.systemInstructions, settings.voiceLanguage)
        val userPrompt = buildPlannerUserPrompt(
            userCommand = userCommand,
            stepIndex = stepIndex,
            stepHistory = stepHistory,
            screenSnapshot = screenSnapshot,
            installedApps = installedApps
        )

        val request = GenerateContentRequest(
            systemInstruction = ContentDto(parts = listOf(PartDto(text = systemPrompt))),
            contents = listOf(
                ContentDto(
                    role = "user",
                    parts = listOf(PartDto(text = userPrompt))
                )
            ),
            generationConfig = GenerationConfigDto(
                temperature = settings.temperature,
                maxOutputTokens = 1024,
                responseMimeType = "application/json"
            )
        )

        return try {
            val cleanModel = settings.selectedModel.removePrefix("models/")
            val response = service.generateContent(cleanModel, apiKey.trim(), request)
            if (!response.isSuccessful) {
                return mapHttpFailure(response)
            }
            val body = response.body()
            val text = body?.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull { !it.text.isNullOrBlank() }
                ?.text
                ?.trim()

            if (text.isNullOrBlank()) {
                return GeminiResult.Failure(
                    ErrorType.API_ERROR,
                    "Gemini returned an empty decision. Try again or switch model."
                )
            }

            val cleanedJson = stripMarkdownFences(text)
            val dto = runCatching { decisionAdapter.fromJson(cleanedJson) }.getOrNull()
                ?: return GeminiResult.Failure(
                    ErrorType.PARSE_ERROR,
                    "Could not parse structured action plan from Gemini."
                )

            val decision = dto.toDomain()
            val promptTokens = body.usageMetadata?.promptTokenCount ?: 0
            val completionTokens = body.usageMetadata?.candidatesTokenCount ?: 0
            GeminiResult.Success(
                data = decision,
                promptTokens = promptTokens,
                completionTokens = completionTokens
            )
        } catch (e: Exception) {
            mapException(e)
        }
    }

    suspend fun summarizeContent(
        apiKey: String,
        settings: AiSettings,
        userGoal: String,
        screenContent: String
    ): GeminiResult<String> {
        if (apiKey.isBlank()) {
            return GeminiResult.Failure(
                ErrorType.MISSING_API_KEY,
                "Gemini API key is not configured."
            )
        }

        val prompt = """
            User request: $userGoal
            
            Visible screen text captured from the device:
            $screenContent
            
            Provide a concise, accurate, natural response summarizing the visible information that answers the user's request.
            Match the user's language preference (${settings.voiceLanguage.label}). If the user spoke in Hindi or Hinglish, reply naturally in that language/style.
            Keep the answer clear enough to be spoken aloud in 2-4 sentences.
        """.trimIndent()

        val request = GenerateContentRequest(
            systemInstruction = ContentDto(parts = listOf(PartDto(text = settings.systemInstructions))),
            contents = listOf(ContentDto(role = "user", parts = listOf(PartDto(text = prompt)))),
            generationConfig = GenerationConfigDto(
                temperature = settings.temperature,
                maxOutputTokens = 512
            )
        )

        return try {
            val cleanModel = settings.selectedModel.removePrefix("models/")
            val response = service.generateContent(cleanModel, apiKey.trim(), request)
            if (!response.isSuccessful) {
                return mapHttpFailure(response)
            }
            val body = response.body()
            val text = body?.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.joinToString("\n") { it.text.orEmpty() }
                ?.trim()

            if (text.isNullOrBlank()) {
                GeminiResult.Failure(ErrorType.API_ERROR, "Gemini returned an empty summary.")
            } else {
                GeminiResult.Success(
                    data = text,
                    promptTokens = body.usageMetadata?.promptTokenCount ?: 0,
                    completionTokens = body.usageMetadata?.candidatesTokenCount ?: 0
                )
            }
        } catch (e: Exception) {
            mapException(e)
        }
    }

    private fun buildPlannerSystemPrompt(
        customInstructions: String,
        voiceLanguage: VoiceLanguage
    ): String {
        return """
            $customInstructions
            
            Language preference: ${voiceLanguage.label}. Respond in the same language (English, Hindi, or Hinglish) as the user's command.
            
            You control an Android phone through an Accessibility Service. Given the user's command, step history, available apps, and the current screen UI tree, output ONLY a single JSON object matching this exact schema:
            {
              "thought": "Brief reasoning about current screen state and next step",
              "plan_steps": ["Step 1...", "Step 2...", "..."],
              "action": "ONE OF: OPEN_APP | OPEN_URL | TAP_NODE | TAP_TEXT | TYPE_TEXT | SCROLL_DOWN | SCROLL_UP | PRESS_BACK | PRESS_HOME | WAIT | READ_AND_SUMMARIZE | ASK_CONFIRMATION | COMPLETE | FAIL",
              "target_app": "App name or package name when action is OPEN_APP, else null",
              "node_index": 0,
              "target_text": "Visible text/description to tap when TAP_TEXT, or null",
              "input_text": "Text to type when TYPE_TEXT, or null",
              "submit_after_input": true,
              "url": "https://... when OPEN_URL, else null",
              "spoken_message": "Short natural progress or final message for the user",
              "requires_confirmation": false,
              "confirmation_reason": "Reason if requires_confirmation is true, else null"
            }
            
            STRICT RULES:
            1. Never blindly assume an app opened — check current_package and nodes in the screen snapshot.
            2. If the goal is already achieved on screen (e.g. search results are visible for a simple search, or Settings opened), choose COMPLETE or READ_AND_SUMMARIZE.
            3. If the user asks to summarize, read, or explain screen content, choose READ_AND_SUMMARIZE once the target page is loaded.
            4. For TYPE_TEXT, always provide the editable node_index from the screen nodes list. Set submit_after_input=true for search bars.
            5. For TAP_NODE, provide the exact node_index of a clickable node. Use TAP_TEXT only as a fallback when matching by label.
            6. Set requires_confirmation=true before sending any message/email, deleting anything, or any sensitive/irreversible action. Never automate payments or password entry.
            7. If a step failed previously, recover intelligently (scroll, press back, or pick an alternate node).
        """.trimIndent()
    }

    private fun buildPlannerUserPrompt(
        userCommand: String,
        stepIndex: Int,
        stepHistory: List<String>,
        screenSnapshot: ScreenSnapshot?,
        installedApps: List<InstalledAppInfo>
    ): String {
        val historyBlock = if (stepHistory.isEmpty()) {
            "None yet (Step 1)"
        } else {
            stepHistory.mapIndexed { idx, item -> "${idx + 1}. $item" }.joinToString("\n")
        }

        val matchingApps = installedApps
            .filter { app ->
                val lowerCmd = userCommand.lowercase()
                lowerCmd.contains(app.appName.lowercase()) ||
                    app.appName.lowercase() in setOf(
                        "youtube", "chrome", "settings", "messages", "whatsapp",
                        "gmail", "maps", "clock", "calculator", "camera", "contacts", "phone"
                    )
            }
            .take(25)
            .joinToString(", ") { "${it.appName} (${it.packageName})" }

        val screenBlock = screenSnapshot?.toPromptText()
            ?: "Accessibility snapshot unavailable (either service is off or screen is transitioning)."

        return """
            USER COMMAND: "$userCommand"
            CURRENT STEP NUMBER: $stepIndex
            
            RECENT STEP HISTORY:
            $historyBlock
            
            RELEVANT INSTALLED APPS:
            ${matchingApps.ifBlank { "Standard system apps (Settings, Browser, etc.)" }}
            
            CURRENT SCREEN STATE:
            $screenBlock
        """.trimIndent()
    }

    private fun stripMarkdownFences(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
            if (s.endsWith("```")) {
                s = s.removeSuffix("```").trim()
            }
        }
        return s
    }

    private fun AgentDecisionDto.toDomain(): AgentDecision {
        val parsedAction = runCatching { ActionType.valueOf(action.uppercase().trim()) }
            .getOrElse { ActionType.FAIL }
        return AgentDecision(
            thought = thought,
            planSteps = planSteps,
            action = parsedAction,
            targetApp = targetApp,
            nodeIndex = nodeIndex,
            targetText = targetText,
            inputText = inputText,
            submitAfterInput = submitAfterInput,
            url = url,
            spokenMessage = spokenMessage,
            requiresConfirmation = requiresConfirmation,
            confirmationReason = confirmationReason
        )
    }

    private fun <T> mapHttpFailure(response: Response<T>): GeminiResult.Failure {
        val rawErr = response.errorBody()?.string().orEmpty()
        val parsedMsg = runCatching { errorAdapter.fromJson(rawErr)?.error?.message }.getOrNull()
        val code = response.code()
        return when (code) {
            400, 401, 403 -> GeminiResult.Failure(
                ErrorType.INVALID_API_KEY,
                (parsedMsg ?: "Invalid or unauthorized Gemini API key (HTTP $code).") +
                    " Ensure you are using an active Google Gemini API key from Google AI Studio (starts with 'AIza...')."
            )
            429 -> GeminiResult.Failure(
                ErrorType.QUOTA_EXCEEDED,
                parsedMsg ?: "Gemini API quota or rate limit exceeded (HTTP 429). Please wait or check billing."
            )
            else -> GeminiResult.Failure(
                ErrorType.API_ERROR,
                parsedMsg ?: "Gemini API error (HTTP $code)."
            )
        }
    }

    private fun mapException(e: Exception): GeminiResult.Failure {
        return when (e) {
            is UnknownHostException, is SocketTimeoutException, is IOException -> {
                GeminiResult.Failure(
                    ErrorType.NETWORK_ERROR,
                    "Network error reaching Google Gemini API: ${e.localizedMessage ?: "Check internet connection"}"
                )
            }
            else -> {
                GeminiResult.Failure(
                    ErrorType.API_ERROR,
                    e.localizedMessage ?: "Unexpected Gemini client error."
                )
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"

        fun createDefaultService(): GeminiApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GeminiApiService::class.java)
        }
    }
}
