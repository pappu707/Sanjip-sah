package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onSpeechStateChanged: (Boolean) -> Unit,
    private val onSpeechPartialResult: (String) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    var isTtsReady = false
        private set
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
            setupSpeechRecognizer()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error initializing VoiceManager: ${e.message}")
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onSpeechStateChanged(true)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onSpeechStateChanged(false)
                    }

                    override fun onError(error: Int) {
                        Log.e("VoiceManager", "Speech recognition error: $error")
                        onSpeechStateChanged(false)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onSpeechResult(matches[0])
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onSpeechPartialResult(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            try {
                val voices = tts?.voices
                if (voices != null) {
                    val fallbackVoices = listOf("en-US-language", "en-GB-language")
                    // In Android Google TTS, female voices often have specific names or feature tags
                    val preferredVoice = voices.firstOrNull { it.name.contains("en-us-x-sfg", ignoreCase = true) }
                        ?: voices.firstOrNull { it.name.contains("en-us-x-tpf", ignoreCase = true) }
                        ?: voices.firstOrNull { it.name.contains("female", ignoreCase = true) && it.locale.language == "en" }
                        ?: voices.firstOrNull { it.name.endsWith("-local") && it.locale.language == "en" }

                    if (preferredVoice != null) {
                        tts?.voice = preferredVoice
                    }
                }
            } catch (ignore: Exception) {}
            
            isTtsReady = true
        } else {
            Log.e("VoiceManager", "TTS initialization failed: $status")
        }
    }

    fun speak(text: String, pitch: Float = 1.0f, speed: Float = 1.0f) {
        if (isTtsReady && tts != null) {
            try {
                tts?.setPitch(pitch)
                tts?.setSpeechRate(speed)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AvaUtteranceId")
            } catch (e: Exception) {
                Log.e("VoiceManager", "TTS speak failed: ${e.message}")
            }
        }
    }

    fun stopSpeaking() {
        if (isTtsReady) {
            tts?.stop()
        }
    }

    fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Speech recognition failed to start: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Speech recognition stop failure: ${e.message}")
        }
        onSpeechStateChanged(false)
    }

    fun destroy() {
        try {
            tts?.shutdown()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error calling destroy: ${e.message}")
        }
    }
}
