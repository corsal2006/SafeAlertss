package com.siddesh.smsalerts.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import java.util.Locale
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android Speech Recognizer manager for detecting emergency voice phrases ("HELP", "HELP ME", etc.).
 * Strictly adheres to privacy principles: does NOT record audio to disk or upload raw mic files.
 * Uses official Android SpeechRecognizer with proper error handling and continuous loop protection.
 */
class VoiceEmergencyDetector(
    private val context: Context,
    private val onEmergencyPhraseDetected: (phrase: String) -> Unit,
    private val onError: (String) -> Unit = {},
    var isTestMode: Boolean = false,
    var onTestResult: ((spoken: String, matchedPhrase: String?) -> Unit)? = null
) {

    companion object {
        private const val TAG = "VoiceEmergencyDetector"

        val DEFAULT_EMERGENCY_PHRASES = listOf("HELP ME")
        val EMERGENCY_PHRASES = DEFAULT_EMERGENCY_PHRASES

        fun matchPhrase(spoken: String, phrases: List<String> = DEFAULT_EMERGENCY_PHRASES): String? {
            fun normalize(value: String) = value.uppercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex("\\s+"), " ")
            val text = " ${normalize(spoken)} "
            return phrases.sortedByDescending { it.length }.firstOrNull {
                val phrase = normalize(it)
                phrase.isNotEmpty() && text.contains(" $phrase ")
            }
        }
        fun normalizePhrase(value: String): String = value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        fun validCustomPhrase(value: String): Boolean {
            val clean = normalizePhrase(value)
            return clean.length in 3..64 && clean.split(' ').size in 2..8 &&
                clean.matches(Regex("[\\p{L}\\p{N} ]+"))
        }
    }

    private var customPhrases: List<String> = emptyList()

    fun setCustomPhrases(phrases: List<String>) {
        customPhrases = phrases.map { it.trim().uppercase(Locale.getDefault()) }.filter { it.isNotBlank() }
    }

    fun getAllPhrases(): List<String> {
        return (DEFAULT_EMERGENCY_PHRASES + customPhrases).distinct()
    }

    private val _status = kotlinx.coroutines.flow.MutableStateFlow("Voice off")
    val status = _status.asStateFlow()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var requested = false
    private var lastActivation = 0L
    private var failures = 0
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    fun isSupported(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            handler.post { startListening() }
            return
        }
        requested = true
        if (isListening) return
        if (!com.siddesh.smsalerts.utils.PermissionManager.hasRecordAudioPermission(context)) {
            _status.value = "Microphone permission required"
            requested = false
            onError(_status.value)
            return
        }
        if (!isSupported()) {
            _status.value = "Voice recognition unavailable on this device"
            requested = false
            onError(_status.value)
            return
        }

        try {
            destroyRecognizer()
            speechRecognizer = try {
                if (android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (_: Exception) {
                SpeechRecognizer.createSpeechRecognizer(context)
            }?.apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            _status.value = "Listening on this screen: say help me"
            Log.d(TAG, "Voice recognition started")
        } catch (e: Exception) {
            isListening = false
            requested = false
            _status.value = "Voice unavailable: ${e.javaClass.simpleName}"
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            onError("Unable to initialize speech recognition: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            handler.post { stopListening() }
            return
        }
        requested = false
        handler.removeCallbacksAndMessages(null)
        _status.value = "Voice off"
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer: ${e.message}")
        } finally {
            isListening = false
            destroyRecognizer()
            Log.d(TAG, "Voice recognition stopped")
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignored
        } finally {
            speechRecognizer = null
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                isListening = false
                val reason = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client-side recognition error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network required for speech recognition"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition busy"
                    SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Recognition error ($error)"
                }
                Log.w(TAG, "Speech error code=$error: $reason")
                if (error in listOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    _status.value = "Listening: say \"help me\""
                    if (requested) {
                        failures = 0
                        restart()
                    }
                } else {
                    _status.value = reason
                    if (requested && failures++ < 3 && error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        restart()
                    }
                }
                // Only propagate critical permission errors
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    onError("Microphone permission was denied.")
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    checkMatches(matches)
                } else {
                    _status.value = "Speech unclear; no emergency triggered"
                }
                failures = 0
                if (requested) restart()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Only final recognition results can trigger an incident.
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun matchesEmergencyPhrase(spokenText: String): String? = matchPhrase(spokenText, getAllPhrases())

    private fun restart() {
        handler.postDelayed({ if (requested && com.siddesh.smsalerts.SafeAlertApp.isVisible) startListening() }, 1500)
    }

    private fun checkMatches(matches: ArrayList<String>?) {
        if (matches.isNullOrEmpty()) return

        for (candidate in matches) {
            val matchedPhrase = matchesEmergencyPhrase(candidate)
            if (isTestMode) {
                onTestResult?.invoke(candidate, matchedPhrase)
                if (matchedPhrase != null) {
                    stopListening()
                    return
                }
            } else if (matchedPhrase != null) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (lastActivation > 0L && now - lastActivation < 30_000) return
                lastActivation = now
                stopListening()
                onEmergencyPhraseDetected(matchedPhrase)
                return
            }
        }
    }
}
