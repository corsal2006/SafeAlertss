package com.siddesh.smsalerts.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

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

        val DEFAULT_EMERGENCY_PHRASES = listOf(
            "HELP",
            "HELP ME",
            "I NEED HELP",
            "EMERGENCY",
            "CALL FOR HELP",
            "SAVE ME",
            "ASSISTANCE"
        )
        val EMERGENCY_PHRASES = DEFAULT_EMERGENCY_PHRASES
    }

    private var customPhrases: List<String> = emptyList()

    fun setCustomPhrases(phrases: List<String>) {
        customPhrases = phrases.map { it.trim().uppercase(Locale.getDefault()) }.filter { it.isNotBlank() }
    }

    fun getAllPhrases(): List<String> {
        return (DEFAULT_EMERGENCY_PHRASES + customPhrases).distinct()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    fun isSupported(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        if (isListening) return
        if (!isSupported()) {
            onError("Speech recognition is unavailable on this device.")
            return
        }

        try {
            destroyRecognizer()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(TAG, "Voice recognition started")
        } catch (e: Exception) {
            isListening = false
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            onError("Unable to initialize speech recognition: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        if (!isListening) return
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
                // Only propagate critical permission or client errors
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    onError("Microphone permission was denied.")
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkMatches(matches)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                checkMatches(matches)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun matchesEmergencyPhrase(spokenText: String): String? {
        val normalized = spokenText.trim().uppercase(Locale.getDefault())
        val cleaned = normalized.replace(Regex("[^A-Z0-9 ]"), " ").trim()
        val tokens = cleaned.split("\\s+".toRegex())

        val sortedPhrases = getAllPhrases().sortedByDescending { it.length }
        for (phrase in sortedPhrases) {
            val phraseTokens = phrase.split(" ")
            if (phraseTokens.size == 1) {
                if (tokens.contains(phrase)) return phrase
            } else {
                if (cleaned.contains(phrase)) return phrase
            }
        }
        return null
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
                Log.i(TAG, "VOICE EMERGENCY COMMAND DETECTED: '$matchedPhrase' in '$candidate'")
                stopListening()
                onEmergencyPhraseDetected(matchedPhrase)
                return
            }
        }
    }
}
