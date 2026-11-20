package com.example.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<ContentDto>,
    val systemInstruction: ContentDto? = null,
    val generationConfig: GenerationConfigDto? = null
)

@JsonClass(generateAdapter = true)
data class ContentDto(
    val role: String? = null,
    val parts: List<PartDto>
)

@JsonClass(generateAdapter = true)
data class PartDto(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfigDto(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<CandidateDto>? = null,
    val usageMetadata: UsageMetadataDto? = null,
    val error: GeminiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class CandidateDto(
    val content: ContentDto? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class UsageMetadataDto(
    val promptTokenCount: Int? = 0,
    val candidatesTokenCount: Int? = 0,
    val totalTokenCount: Int? = 0
)

@JsonClass(generateAdapter = true)
data class ModelsListResponse(
    val models: List<ModelInfoDto>? = null
)

@JsonClass(generateAdapter = true)
data class ModelInfoDto(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorEnvelope(
    val error: GeminiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class AgentDecisionDto(
    val thought: String = "",
    @Json(name = "plan_steps") val planSteps: List<String> = emptyList(),
    val action: String = "FAIL",
    @Json(name = "target_app") val targetApp: String? = null,
    @Json(name = "node_index") val nodeIndex: Int? = null,
    @Json(name = "target_text") val targetText: String? = null,
    @Json(name = "input_text") val inputText: String? = null,
    @Json(name = "submit_after_input") val submitAfterInput: Boolean = false,
    val url: String? = null,
    @Json(name = "spoken_message") val spokenMessage: String? = null,
    @Json(name = "requires_confirmation") val requiresConfirmation: Boolean = false,
    @Json(name = "confirmation_reason") val confirmationReason: String? = null
)
