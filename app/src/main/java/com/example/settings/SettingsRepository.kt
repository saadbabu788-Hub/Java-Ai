package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AiSettings
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.GeminiModelOption
import com.example.domain.model.VoiceLanguage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aurapilot_ai_settings"
)

class SettingsRepository(
    private val context: Context,
    private val cryptoManager: SecretCipher
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val modelsListType = Types.newParameterizedType(
        List::class.java,
        GeminiModelOption::class.java
    )
    private val modelsAdapter = moshi.adapter<List<GeminiModelOption>>(modelsListType)

    private object Keys {
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val API_KEY_IV = stringPreferencesKey("api_key_iv")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val AVAILABLE_MODELS_JSON = stringPreferencesKey("available_models_json")
        val CONNECTION_STATUS = stringPreferencesKey("connection_status")
        val CONNECTION_MESSAGE = stringPreferencesKey("connection_message")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val SYSTEM_INSTRUCTIONS = stringPreferencesKey("system_instructions")
        val VOICE_FEEDBACK = booleanPreferencesKey("voice_feedback")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val REQUIRE_CONFIRMATION = booleanPreferencesKey("require_confirmation")
        val SCREEN_CONTEXT_CONSENT = booleanPreferencesKey("screen_context_consent_accepted")
        val TOTAL_PROMPT_TOKENS = longPreferencesKey("total_prompt_tokens")
        val TOTAL_COMPLETION_TOKENS = longPreferencesKey("total_completion_tokens")
    }

    val settingsFlow: Flow<AiSettings> = context.settingsDataStore.data.map { prefs ->
        val enc = prefs[Keys.ENCRYPTED_API_KEY]
        val iv = prefs[Keys.API_KEY_IV]
        val decrypted = if (!enc.isNullOrBlank() && !iv.isNullOrBlank()) {
            cryptoManager.decrypt(EncryptedPayload(enc, iv))
        } else {
            null
        }
        val hasKey = !decrypted.isNullOrBlank()
        val masked = if (hasKey && decrypted != null) maskApiKey(decrypted) else ""

        val modelsJson = prefs[Keys.AVAILABLE_MODELS_JSON]
        val models = if (!modelsJson.isNullOrBlank()) {
            runCatching { modelsAdapter.fromJson(modelsJson) }.getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: AiSettings.DEFAULT_MODELS
        } else {
            AiSettings.DEFAULT_MODELS
        }

        val status = prefs[Keys.CONNECTION_STATUS]?.let { raw ->
            runCatching { ConnectionStatus.valueOf(raw) }.getOrNull()
        } ?: if (hasKey) ConnectionStatus.NOT_CONFIGURED else ConnectionStatus.NOT_CONFIGURED

        val voiceLang = prefs[Keys.VOICE_LANGUAGE]?.let { raw ->
            runCatching { VoiceLanguage.valueOf(raw) }.getOrNull()
        } ?: VoiceLanguage.AUTO

        AiSettings(
            hasApiKey = hasKey,
            maskedApiKey = masked,
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: AiSettings.DEFAULT_MODEL_ID,
            availableModels = models,
            connectionStatus = if (!hasKey) ConnectionStatus.NOT_CONFIGURED else status,
            connectionMessage = prefs[Keys.CONNECTION_MESSAGE]
                ?: if (hasKey) "Key saved. Tap Test Connection to verify." else "No Gemini API key configured",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.2f,
            systemInstructions = prefs[Keys.SYSTEM_INSTRUCTIONS] ?: AiSettings.DEFAULT_SYSTEM_INSTRUCTIONS,
            voiceFeedbackEnabled = prefs[Keys.VOICE_FEEDBACK] ?: true,
            voiceLanguage = voiceLang,
            requireSensitiveConfirmation = prefs[Keys.REQUIRE_CONFIRMATION] ?: true,
            screenContextConsentAccepted = prefs[Keys.SCREEN_CONTEXT_CONSENT] ?: false,
            totalPromptTokens = prefs[Keys.TOTAL_PROMPT_TOKENS] ?: 0L,
            totalCompletionTokens = prefs[Keys.TOTAL_COMPLETION_TOKENS] ?: 0L
        )
    }

    suspend fun getDecryptedApiKey(): String? {
        val prefs = context.settingsDataStore.data.first()
        val enc = prefs[Keys.ENCRYPTED_API_KEY] ?: return null
        val iv = prefs[Keys.API_KEY_IV] ?: return null
        return cryptoManager.decrypt(EncryptedPayload(enc, iv))?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be empty" }
        val payload = cryptoManager.encrypt(trimmed)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_API_KEY] = payload.ciphertextBase64
            prefs[Keys.API_KEY_IV] = payload.ivBase64
            prefs[Keys.CONNECTION_STATUS] = ConnectionStatus.NOT_CONFIGURED.name
            prefs[Keys.CONNECTION_MESSAGE] = "API key saved encrypted. Tap Test Connection to verify."
        }
    }

    suspend fun removeApiKey() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.ENCRYPTED_API_KEY)
            prefs.remove(Keys.API_KEY_IV)
            prefs[Keys.CONNECTION_STATUS] = ConnectionStatus.NOT_CONFIGURED.name
            prefs[Keys.CONNECTION_MESSAGE] = "API key removed. Enter your Gemini API key to continue."
        }
    }

    suspend fun updateConnectionResult(
        status: ConnectionStatus,
        message: String,
        fetchedModels: List<GeminiModelOption>? = null
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CONNECTION_STATUS] = status.name
            prefs[Keys.CONNECTION_MESSAGE] = message
            if (!fetchedModels.isNullOrEmpty()) {
                prefs[Keys.AVAILABLE_MODELS_JSON] = modelsAdapter.toJson(fetchedModels)
                val currentSelected = prefs[Keys.SELECTED_MODEL]
                if (currentSelected.isNullOrBlank() || fetchedModels.none { it.id == currentSelected }) {
                    val preferred = fetchedModels.firstOrNull { it.id.contains("2.5-flash") }
                        ?: fetchedModels.firstOrNull { it.id.contains("flash") }
                        ?: fetchedModels.first()
                    prefs[Keys.SELECTED_MODEL] = preferred.id
                }
            }
        }
    }

    suspend fun updateSelectedModel(modelId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODEL] = modelId
        }
    }

    suspend fun updateTemperature(temperature: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TEMPERATURE] = temperature.coerceIn(0f, 1f)
        }
    }

    suspend fun updateSystemInstructions(instructions: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SYSTEM_INSTRUCTIONS] = instructions.ifBlank { AiSettings.DEFAULT_SYSTEM_INSTRUCTIONS }
        }
    }

    suspend fun updateVoiceFeedback(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.VOICE_FEEDBACK] = enabled
        }
    }

    suspend fun updateVoiceLanguage(language: VoiceLanguage) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.VOICE_LANGUAGE] = language.name
        }
    }

    suspend fun updateRequireConfirmation(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.REQUIRE_CONFIRMATION] = enabled
        }
    }

    suspend fun updateScreenContextConsent(accepted: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SCREEN_CONTEXT_CONSENT] = accepted
        }
    }

    suspend fun recordTokenUsage(promptTokens: Int, completionTokens: Int) {
        if (promptTokens <= 0 && completionTokens <= 0) return
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TOTAL_PROMPT_TOKENS] = (prefs[Keys.TOTAL_PROMPT_TOKENS] ?: 0L) + promptTokens
            prefs[Keys.TOTAL_COMPLETION_TOKENS] =
                (prefs[Keys.TOTAL_COMPLETION_TOKENS] ?: 0L) + completionTokens
        }
    }

    suspend fun resetTokenUsage() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TOTAL_PROMPT_TOKENS] = 0L
            prefs[Keys.TOTAL_COMPLETION_TOKENS] = 0L
        }
    }

    companion object {
        fun maskApiKey(key: String): String {
            if (key.length <= 10) return "••••••••"
            return "${key.take(6)}••••••••••••${key.takeLast(4)}"
        }
    }
}
