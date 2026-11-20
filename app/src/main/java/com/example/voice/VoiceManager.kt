package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.VoiceLanguage
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialTranscript: String = "",
    val rmsLevel: Float = 0f,
    val isRecognizerAvailable: Boolean = true,
    val lastError: String? = null
)

class VoiceManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    private val _voiceState = MutableStateFlow(
        VoiceState(isRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        mainHandler.post {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                ttsInitialized = status == TextToSpeech.SUCCESS
                if (ttsInitialized) {
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _voiceState.value = _voiceState.value.copy(isSpeaking = true)
                        }

                        override fun onDone(utteranceId: String?) {
                            _voiceState.value = _voiceState.value.copy(isSpeaking = false)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _voiceState.value = _voiceState.value.copy(isSpeaking = false)
                        }
                    })
                }
            }
        }
    }

    fun startListening(
        language: VoiceLanguage,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            stopSpeaking()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                val msg = "Speech recognition service is not available on this device."
                _voiceState.value = _voiceState.value.copy(
                    isRecognizerAvailable = false,
                    isListening = false,
                    lastError = msg
                )
                onError(msg)
                return@post
            }

            runCatching { speechRecognizer?.destroy() }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = _voiceState.value.copy(
                        isListening = true,
                        partialTranscript = "",
                        lastError = null
                    )
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _voiceState.value = _voiceState.value.copy(rmsLevel = normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _voiceState.value = _voiceState.value.copy(isListening = false, rmsLevel = 0f)
                }

                override fun onError(error: Int) {
                    val message = mapSpeechError(error)
                    _voiceState.value = _voiceState.value.copy(
                        isListening = false,
                        rmsLevel = 0f,
                        lastError = message
                    )
                    if (error != SpeechRecognizer.ERROR_CLIENT) {
                        onError(message)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim().orEmpty()
                    _voiceState.value = _voiceState.value.copy(
                        isListening = false,
                        partialTranscript = transcript,
                        rmsLevel = 0f
                    )
                    if (transcript.isNotEmpty()) {
                        onResult(transcript)
                    } else {
                        onError("Could not hear speech clearly. Please try again.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _voiceState.value = _voiceState.value.copy(partialTranscript = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.bcp47)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.bcp47)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            _voiceState.value = _voiceState.value.copy(isListening = false, rmsLevel = 0f)
        }
    }

    fun cancelListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
            _voiceState.value = _voiceState.value.copy(
                isListening = false,
                partialTranscript = "",
                rmsLevel = 0f
            )
        }
    }

    fun speak(text: String, language: VoiceLanguage) {
        if (text.isBlank()) return
        mainHandler.post {
            val tts = textToSpeech ?: return@post
            if (!ttsInitialized) return@post

            val targetLocale = selectLocaleForText(text, language)
            val langResult = tts.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.language = Locale.US
            }
            tts.setSpeechRate(1.03f)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            runCatching { textToSpeech?.stop() }
            _voiceState.value = _voiceState.value.copy(isSpeaking = false)
        }
    }

    fun release() {
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
            textToSpeech = null
        }
    }

    private fun selectLocaleForText(text: String, preferred: VoiceLanguage): Locale {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        return when {
            hasDevanagari -> Locale("hi", "IN")
            preferred == VoiceLanguage.HINDI -> Locale("hi", "IN")
            preferred == VoiceLanguage.HINGLISH || preferred == VoiceLanguage.AUTO -> Locale("en", "IN")
            else -> Locale.US
        }
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network unavailable for speech recognition"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try speaking again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy, retrying..."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
        else -> "Speech recognition unavailable (code $code)"
    }
}
