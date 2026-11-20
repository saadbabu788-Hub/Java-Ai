package com.example

import com.example.accessibility.BoundsRect
import com.example.accessibility.ScreenNode
import com.example.accessibility.ScreenSnapshot
import com.example.ai.ErrorType
import com.example.ai.GeminiApiService
import com.example.ai.GeminiClient
import com.example.ai.GeminiResult
import com.example.ai.ModelInfoDto
import com.example.ai.ModelsListResponse
import com.example.automation.SafetyGuard
import com.example.domain.model.ActionType
import com.example.domain.model.AgentDecision
import com.example.domain.model.AiSettings
import com.example.domain.model.SafetyCheckResult
import com.example.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AuraCoreUnitTest {

    @Test
    fun `safety guard blocks password input`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.app",
            className = "LoginActivity",
            timestamp = 1L,
            nodes = listOf(
                ScreenNode(
                    index = 0,
                    text = "",
                    contentDescription = "Password",
                    viewId = "et_password",
                    className = "android.widget.EditText",
                    isClickable = true,
                    isEditable = true,
                    isScrollable = false,
                    isFocused = true,
                    isPassword = true,
                    bounds = BoundsRect(0, 0, 100, 50)
                )
            ),
            visibleTextLines = emptyList()
        )

        val decision = AgentDecision(
            thought = "Enter password",
            planSteps = listOf("Enter password"),
            action = ActionType.TYPE_TEXT,
            nodeIndex = 0,
            inputText = "secret"
        )

        val result = SafetyGuard.evaluate(decision, snapshot, requireConfirmation = true)
        assertTrue(result is SafetyCheckResult.Blocked)
    }

    @Test
    fun `safety guard blocks financial transactions`() {
        val decision = AgentDecision(
            thought = "Tap Pay Now to complete purchase",
            planSteps = listOf("Pay"),
            action = ActionType.TAP_TEXT,
            targetText = "Pay Now"
        )
        val result = SafetyGuard.evaluate(decision, null, requireConfirmation = true)
        assertTrue(result is SafetyCheckResult.Blocked)
    }

    @Test
    fun `safety guard requires confirmation before sending message`() {
        val decision = AgentDecision(
            thought = "Tap Send button",
            planSteps = listOf("Send"),
            action = ActionType.TAP_TEXT,
            targetText = "Send"
        )
        val result = SafetyGuard.evaluate(decision, null, requireConfirmation = true)
        assertTrue(result is SafetyCheckResult.RequiresConfirmation)
    }

    @Test
    fun `gemini client returns missing key failure when empty`() = runTest {
        val client = GeminiClient(service = FakeGeminiApiService())
        val res = client.testConnection("   ")
        assertTrue(res is GeminiResult.Failure)
        assertEquals(ErrorType.MISSING_API_KEY, (res as GeminiResult.Failure).type)
    }

    @Test
    fun `gemini client filters supported generateContent models on testConnection`() = runTest {
        val client = GeminiClient(service = FakeGeminiApiService())
        val res = client.testConnection("AIzaTestKey123456")
        assertTrue(res is GeminiResult.Success)
        val models = (res as GeminiResult.Success).data
        assertEquals(2, models.size)
        assertEquals("gemini-2.5-flash", models[0].id)
        assertEquals("gemini-2.5-pro", models[1].id)
    }

    @Test
    fun `gemini client maps HTTP 429 to QUOTA_EXCEEDED`() = runTest {
        val client = GeminiClient(
            service = FakeGeminiApiService(
                listModelsResponse = Response.error(
                    429,
                    """{"error":{"code":429,"message":"Quota exceeded"}}"""
                        .toResponseBody("application/json".toMediaType())
                )
            )
        )
        val res = client.testConnection("AIzaTestKey123456")
        assertTrue(res is GeminiResult.Failure)
        assertEquals(ErrorType.QUOTA_EXCEEDED, (res as GeminiResult.Failure).type)
    }

    @Test
    fun `maskApiKey obscures middle characters`() {
        val masked = SettingsRepository.maskApiKey("AIzaSyD1234567890abcdWXYZ")
        assertTrue(masked.startsWith("AIzaSy"))
        assertTrue(masked.endsWith("WXYZ"))
        assertTrue(masked.contains("••••"))
    }

    private class FakeGeminiApiService(
        private val listModelsResponse: Response<ModelsListResponse> = Response.success(
            ModelsListResponse(
                models = listOf(
                    ModelInfoDto(
                        name = "models/gemini-2.5-flash",
                        displayName = "Gemini 2.5 Flash",
                        supportedGenerationMethods = listOf("generateContent")
                    ),
                    ModelInfoDto(
                        name = "models/gemini-2.5-pro",
                        displayName = "Gemini 2.5 Pro",
                        supportedGenerationMethods = listOf("generateContent")
                    ),
                    ModelInfoDto(
                        name = "models/text-embedding-004",
                        displayName = "Embedding 004",
                        supportedGenerationMethods = listOf("embedContent")
                    )
                )
            )
        )
    ) : GeminiApiService {
        override suspend fun listModels(apiKey: String): Response<ModelsListResponse> =
            listModelsResponse

        override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: com.example.ai.GenerateContentRequest
        ): Response<com.example.ai.GenerateContentResponse> =
            Response.success(com.example.ai.GenerateContentResponse())
    }
}
