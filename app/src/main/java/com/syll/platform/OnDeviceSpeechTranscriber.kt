package com.syll.platform

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.WavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import kotlin.coroutines.resume

/**
 * iOS `OnDeviceSpeechTranscriber`: recognizes a finished take with the device's own model, so no audio leaves the
 * phone. Needs Android 13 (the recognizer reads the recorded samples instead of the microphone) and an installed
 * English model; without them [requestAccess] answers false, which the store treats as iOS does an unsupported
 * recognizer (free plan: the task is `unavailable`).
 */
class OnDeviceSpeechTranscriber(private val context: Context, private val permission: MicrophonePermission) : SpeechTranscribing {
    override suspend fun requestAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context) || !permission.isGranted) return false
        return withTimeoutOrNull(SUPPORT_TIMEOUT_MS) { hasEnglishModel() } ?: false
    }

    override suspend fun transcribe(audio: RecordedAudio): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
        val pcm = WavFile.pcmData(audio.data) ?: return emptyList()
        return withTimeoutOrNull(RECOGNITION_TIMEOUT_MS) { recognize(pcm) } ?: emptyList()
    }

    /** True when an English model is installed; one that is only downloadable starts downloading for next time. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun hasEnglishModel(): Boolean = suspendCancellableCoroutine { continuation ->
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = recognizerIntent()
        continuation.invokeOnCancellation { ContextCompat.getMainExecutor(context).execute { recognizer.destroy() } }
        recognizer.checkRecognitionSupport(
            intent,
            ContextCompat.getMainExecutor(context),
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val installed = support.installedOnDeviceLanguages.any(::isEnglishUs)
                    if (!installed && (support.supportedOnDeviceLanguages + support.pendingOnDeviceLanguages).any(::isEnglishUs)) {
                        runCatching { recognizer.triggerModelDownload(intent) }
                    }
                    recognizer.destroy()
                    if (continuation.isActive) continuation.resume(installed)
                }

                override fun onError(error: Int) {
                    recognizer.destroy()
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognize(pcm: ByteArray): List<String> = coroutineScope {
        val (source, sink) = ParcelFileDescriptor.createPipe()
        // The samples go in on a background thread; a trailing silence lets the recognizer find the end of speech.
        launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(sink).use { stream ->
                    stream.write(pcm)
                    stream.write(ByteArray(TRAILING_SILENCE_BYTES))
                }
            } catch (_: IOException) {
                // The recognizer closed its end early (an error); its callback reports it.
            }
        }
        try {
            suspendCancellableCoroutine { continuation ->
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                fun finish(result: List<String>) {
                    recognizer.destroy()
                    if (continuation.isActive) continuation.resume(result)
                }
                continuation.invokeOnCancellation { ContextCompat.getMainExecutor(context).execute { recognizer.destroy() } }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) {
                        finish(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                    }

                    // Silence arrives as an error ("no match"), which is an invalid take, not a failure.
                    override fun onError(error: Int) = finish(emptyList())
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                recognizer.startListening(
                    recognizerIntent()
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, WavFile.SAMPLE_RATE),
                )
            }
        } finally {
            runCatching { source.close() }
        }
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

    private fun isEnglishUs(tag: String) = tag.replace('_', '-').equals(LANGUAGE, ignoreCase = true)

    private companion object {
        const val LANGUAGE = "en-US"
        const val SUPPORT_TIMEOUT_MS = 3_000L
        const val RECOGNITION_TIMEOUT_MS = 15_000L
        /** 0.6 s of 16 kHz 16-bit silence. */
        const val TRAILING_SILENCE_BYTES = WavFile.SAMPLE_RATE * 2 * 6 / 10
    }
}
