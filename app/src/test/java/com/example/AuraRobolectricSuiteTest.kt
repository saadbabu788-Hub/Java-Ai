package com.example

import android.content.Context
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.database.ActionLogEntity
import com.example.database.AuraDatabase
import com.example.database.AutomationTaskEntity
import com.example.database.ChatMessageEntity
import com.example.domain.model.AiSettings
import com.example.domain.model.ConnectionStatus
import com.example.presentation.SettingsUiState
import com.example.presentation.screens.SettingsScreen
import com.example.settings.EncryptedPayload
import com.example.settings.SecretCipher
import com.example.settings.SettingsRepository
import com.example.ui.theme.AuraPilotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraRobolectricSuiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `room database persists chat messages tasks and action logs`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            db.chatDao().insertMessage(
                ChatMessageEntity(
                    sessionId = "s1",
                    role = "USER",
                    content = "Open YouTube and search AI news"
                )
            )
            val messages = db.chatDao().observeSessionMessages("s1").first()
            assertEquals(1, messages.size)
            assertEquals("Open YouTube and search AI news", messages[0].content)

            val taskId = db.automationTaskDao().insertTask(
                AutomationTaskEntity(
                    title = "YouTube Search",
                    userCommand = "Open YouTube and search AI news",
                    status = "COMPLETED",
                    plannedStepsJson = """["Open YouTube","Search AI news"]""",
                    currentStepIndex = 2
                )
            )
            db.actionLogDao().insertLog(
                ActionLogEntity(
                    taskId = taskId,
                    stepIndex = 1,
                    actionType = "OPEN_APP",
                    targetDescription = "Opened YouTube",
                    packageName = "com.google.android.youtube",
                    success = true
                )
            )
            val logs = db.actionLogDao().observeLogsForTask(taskId).first()
            assertEquals(1, logs.size)
            assertTrue(logs[0].success)
        } finally {
            db.close()
        }
    }

    @Test
    fun `settings repository encrypts saves and removes api key`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeCipher = object : SecretCipher {
            override fun encrypt(plaintext: String): EncryptedPayload {
                val encoded = Base64.encodeToString(plaintext.toByteArray(), Base64.NO_WRAP)
                return EncryptedPayload(encoded, "fake-iv")
            }

            override fun decrypt(payload: EncryptedPayload): String {
                return String(Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP))
            }
        }
        val repo = SettingsRepository(context, fakeCipher)

        repo.saveApiKey("AIzaSyFakeKeyForUnitTesting1234")
        assertEquals("AIzaSyFakeKeyForUnitTesting1234", repo.getDecryptedApiKey())
        assertTrue(repo.settingsFlow.first().hasApiKey)

        repo.removeApiKey()
        assertNull(repo.getDecryptedApiKey())
        assertFalse(repo.settingsFlow.first().hasApiKey)
    }

    @Test
    fun `settings screen renders all mandatory gemini configuration controls`() {
        composeRule.setContent {
            AuraPilotTheme {
                SettingsScreen(
                    uiState = SettingsUiState(
                        settings = AiSettings(
                            hasApiKey = true,
                            maskedApiKey = "AIzaSy••••1234",
                            connectionStatus = ConnectionStatus.CONNECTED,
                            connectionMessage = "Connected (4 Gemini models available)"
                        ),
                        apiKeyDraft = "AIzaTest"
                    ),
                    onApiKeyDraftChange = {},
                    onSaveApiKey = {},
                    onTestConnection = {},
                    onRemoveApiKey = {},
                    onSelectModel = {},
                    onTemperatureChange = {},
                    onSaveSystemInstructions = {},
                    onToggleVoiceFeedback = {},
                    onSelectVoiceLanguage = {},
                    onToggleSensitiveConfirmation = {},
                    onResetTokens = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AI Provider: Google Gemini").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gemini_api_key_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_api_key_visibility_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save_api_key_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("test_api_connection_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("remove_api_key_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("connection_status_indicator").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gemini_model_dropdown").performScrollTo().assertIsDisplayed()
    }
}
