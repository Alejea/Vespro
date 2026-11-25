package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceStreamState(
    val isListening: Boolean = false,
    val isRecognizerAvailable: Boolean = false,
    val partialSegment: String = "",
    val rmsLevel: Float = 0f,
    val statusHint: String? = null
)

class VoiceStreamManager(
    private val context: Context,
    private val onSegmentCommitted: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var shouldKeepListening = false

    private val _state = MutableStateFlow(
        VoiceStreamState(
            isRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        )
    )
    val state: StateFlow<VoiceStreamState> = _state.asStateFlow()

    fun startListening() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = _state.value.copy(
                    isListening = false,
                    isRecognizerAvailable = false,
                    statusHint = "Riconoscimento vocale integrato non disponibile su questo dispositivo: usa il pulsante Dettatura di sistema o scrivi."
                )
                return@post
            }

            shouldKeepListening = true
            ensureRecognizer()
            _state.value = _state.value.copy(
                isListening = true,
                partialSegment = "",
                statusHint = "In ascolto… parla pure a ruota libera."
            )
            beginRecognitionSession()
        }
    }

    fun stopListening() {
        mainHandler.post {
            shouldKeepListening = false
            val leftover = _state.value.partialSegment.trim()
            if (leftover.isNotEmpty()) {
                onSegmentCommitted(leftover)
            }
            _state.value = _state.value.copy(
                isListening = false,
                partialSegment = "",
                rmsLevel = 0f,
                statusHint = null
            )
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
        }
    }

    fun createFallbackSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Racconta com'è andata la giornata…")
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(
                        isListening = true,
                        statusHint = "Microfono attivo — ti ascolto"
                    )
                }

                override fun onBeginningOfSpeech() {
                    _state.value = _state.value.copy(statusHint = "Trascrizione in corso…")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _state.value = _state.value.copy(rmsLevel = normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(rmsLevel = 0f)
                }

                override fun onError(error: Int) {
                    val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (shouldKeepListening && recoverable) {
                        val leftover = _state.value.partialSegment.trim()
                        if (leftover.isNotEmpty()) {
                            onSegmentCommitted(leftover)
                        }
                        _state.value = _state.value.copy(
                            partialSegment = "",
                            statusHint = "Pausa breve… continua pure quando vuoi"
                        )
                        mainHandler.postDelayed({
                            if (shouldKeepListening) beginRecognitionSession()
                        }, 180L)
                    } else {
                        shouldKeepListening = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Permesso microfono necessario per il flusso vocale."
                            SpeechRecognizer.ERROR_AUDIO ->
                                "Errore audio dal microfono. Puoi continuare scrivendo."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Connessione assente per la trascrizione vocale."
                            else -> "Dettatura interrotta (codice $error)."
                        }
                        _state.value = _state.value.copy(
                            isListening = false,
                            partialSegment = "",
                            rmsLevel = 0f,
                            statusHint = message
                        )
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val finalized = matches?.firstOrNull()?.trim().orEmpty()
                    if (finalized.isNotEmpty()) {
                        onSegmentCommitted(finalized)
                    }
                    _state.value = _state.value.copy(partialSegment = "", rmsLevel = 0f)
                    if (shouldKeepListening) {
                        mainHandler.postDelayed({
                            if (shouldKeepListening) beginRecognitionSession()
                        }, 120L)
                    } else {
                        _state.value = _state.value.copy(isListening = false, statusHint = null)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        _state.value = _state.value.copy(partialSegment = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun beginRecognitionSession() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            shouldKeepListening = false
            _state.value = _state.value.copy(
                isListening = false,
                statusHint = "Impossibile avviare il microfono: ${e.localizedMessage ?: "errore"}"
            )
        }
    }

    fun release() {
        mainHandler.post {
            shouldKeepListening = false
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
        }
    }
}
