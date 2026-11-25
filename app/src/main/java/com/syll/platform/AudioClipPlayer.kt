package com.syll.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.resume

/**
 * iOS `AudioClipPlayer`: plays prompt audio. Clips are downloaded first, so a failed load is known rather than
 * assumed. Main thread only, like the stores that call it.
 */
class AudioClipPlayer(
    context: Context,
    private val http: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = MainScope(),
) : AudioClipPlaying {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    /** Lowers other apps' audio while a word plays (iOS `.spokenAudio`). */
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    override var isPlaying by mutableStateOf(false)
        private set

    private var player: MediaPlayer? = null
    private val cache = mutableMapOf<String, ByteArray>()
    private val downloads = mutableMapOf<String, Deferred<ByteArray>>()
    /** Bumped by [stop], so a play still waiting on its clip can tell it was called off. */
    private var playRequest = 0

    override val canPlay: Boolean
        get() = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type != AudioDeviceInfo.TYPE_TELEPHONY }

    override suspend fun play(url: String): Boolean {
        stop()
        val request = playRequest
        return try {
            val data = clip(url)
            if (request != playRequest) return false
            val started = start(data, request)
            if (request != playRequest) return false
            isPlaying = started
            started
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (request == playRequest) isPlaying = false
            false
        }
    }

    override fun stop() {
        playRequest += 1
        player?.release()
        player = null
        if (isPlaying) audioManager.abandonAudioFocusRequest(focus)
        isPlaying = false
    }

    override suspend fun preload(urls: List<String>) = coroutineScope {
        urls.toSet().filter { it !in cache }.map { url -> async { runCatching { clip(url) } } }.awaitAll()
        Unit
    }

    /** Prepares and starts a player; true once it is really playing. */
    private suspend fun start(data: ByteArray, request: Int): Boolean = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer()
        this.player = player
        player.setAudioAttributes(attributes)
        player.setOnPreparedListener {
            if (request != playRequest) {
                if (continuation.isActive) continuation.resume(false)
                return@setOnPreparedListener
            }
            audioManager.requestAudioFocus(focus)
            it.start()
            if (continuation.isActive) continuation.resume(it.isPlaying)
        }
        player.setOnCompletionListener {
            if (this.player !== it) return@setOnCompletionListener
            isPlaying = false
            audioManager.abandonAudioFocusRequest(focus)
        }
        player.setOnErrorListener { _, _, _ ->
            if (this.player === player) isPlaying = false
            if (continuation.isActive) continuation.resume(false)
            true
        }
        try {
            player.setDataSource(ByteArrayDataSource(data))
            player.prepareAsync()
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(false)
        }
        continuation.invokeOnCancellation { scope.launch { if (this@AudioClipPlayer.player === player) stop() } }
    }

    /** A play and a preload of the same clip share one download. */
    private suspend fun clip(url: String): ByteArray {
        cache[url]?.let { return it }
        val pending = downloads[url] ?: scope.async { download(url) }.also { downloads[url] = it }
        try {
            val data = pending.await()
            cache[url] = data
            return data
        } finally {
            if (downloads[url] === pending && pending.isCompleted) downloads.remove(url)
        }
    }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty clip")
        }
    }

    private class ByteArrayDataSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize() = data.size.toLong()
        override fun close() {}
    }
}
