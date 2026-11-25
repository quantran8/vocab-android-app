package com.syll.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * iOS `SpeechPlayer` (`SpeechProviding`): the device speaks a word when it has no recorded clip. American English,
 * like the clips. [isAvailable] is false until the engine is ready, or when it has no English voice.
 */
class SpeechPlayer(context: Context) {
    var isAvailable by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    /** What is being spoken, so a view can tell its own word from another's. */
    var spokenText by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val result = tts.setLanguage(Locale.US)
            isAvailable = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = main.post { isSpeaking = true }.let { }
                override fun onDone(utteranceId: String?) = main.post { finish() }.let { }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = main.post { finish() }.let { }
            })
        }
    }

    /** Returns false when the device can't speak right now. */
    fun speak(text: String): Boolean {
        val tts = engine?.takeIf { isAvailable } ?: return false
        spokenText = text
        isSpeaking = true
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE) == TextToSpeech.SUCCESS
    }

    fun stop() {
        engine?.stop()
        finish()
    }

    private fun finish() {
        isSpeaking = false
        spokenText = null
    }

    private companion object {
        const val UTTERANCE = "syll-word"
    }
}
