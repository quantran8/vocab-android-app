package com.syll.platform

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.WavFile
import java.io.ByteArrayOutputStream
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * iOS `VoiceRecorder`: records 16 kHz mono 16-bit PCM WAV for Azure Speech. The time limit keeps uploads well
 * under the 2 MB cap. Samples are read on a background thread; everything else runs on the main thread.
 */
class VoiceRecorder(private val permission: MicrophonePermission) : VoiceRecording {
    override var onLimitReached: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var take: Take? = null
    private var smoothedLevel = 0.0
    private var lastLevelTime = 0L

    override val canRecord: Boolean get() = !permission.isDenied

    override suspend fun requestAccess(): Boolean = permission.request()

    override fun inputLevel(): Double {
        val take = take ?: return 0.0
        val now = SystemClock.uptimeMillis()
        val elapsed = (now - lastLevelTime) / 1000.0
        // Several views read the level each frame; only the first read of a frame advances it.
        if (elapsed <= 1.0 / 240) return smoothedLevel
        lastLevelTime = now
        val power = max(take.power, SILENCE_FLOOR)
        val level = (power - SILENCE_FLOOR) / -SILENCE_FLOOR
        // Rise at once, fall over a fixed time (not per call), so the rings don't flicker between syllables.
        val keep = exp(-min(elapsed, 0.25) / LEVEL_RELEASE)
        smoothedLevel = if (level > smoothedLevel) level else smoothedLevel * keep + level * (1 - keep)
        return smoothedLevel
    }

    @SuppressLint("MissingPermission") // The store asks for the permission first; a revoked one throws and is caught.
    override suspend fun start() {
        cancel()
        val minBuffer = AudioRecord.getMinBufferSize(WavFile.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IllegalStateException("No 16 kHz input")
        // Voice recognition input: no automatic gain or noise suppression that would colour the scored sounds.
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            WavFile.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, READ_BYTES * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Recorder not initialized")
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            throw e
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            throw IllegalStateException("Microphone busy")
        }
        smoothedLevel = 0.0
        val take = Take(record)
        this.take = take
        take.thread = Thread({ take.read() }, "VoiceRecorder").apply { start() }
    }

    override fun stop(): RecordedAudio? {
        val take = take ?: return null
        this.take = null
        val pcm = take.finish()
        if (pcm.isEmpty()) return null
        return RecordedAudio(WavFile.pcm16Mono(pcm))
    }

    override fun cancel() {
        val take = take ?: return
        this.take = null
        take.finish()
    }

    /** One recording: the reader thread fills [pcm] until [finish] or the time limit. */
    private inner class Take(private val record: AudioRecord) {
        var thread: Thread? = null
        @Volatile var power = SILENCE_FLOOR
        @Volatile private var running = true
        private val pcm = ByteArrayOutputStream(MAX_BYTES)

        fun read() {
            val buffer = ByteArray(READ_BYTES)
            while (running) {
                val count = record.read(buffer, 0, buffer.size)
                if (count <= 0) {
                    if (count < 0) break
                    continue
                }
                val room = MAX_BYTES - pcm.size()
                synchronized(pcm) { pcm.write(buffer, 0, min(count, room)) }
                power = decibels(buffer, count)
                if (count >= room) {
                    running = false
                    main.post {
                        // A manual stop has already cleared `take`, so this only fires for the limit.
                        if (take === this) onLimitReached?.invoke()
                    }
                }
            }
        }

        /** Stops the microphone and returns what was recorded. */
        fun finish(): ByteArray {
            running = false
            runCatching { record.stop() }
            thread?.join(JOIN_TIMEOUT_MS)
            record.release()
            return synchronized(pcm) { pcm.toByteArray() }
        }
    }

    private companion object {
        const val MAX_SECONDS = 20
        const val MAX_BYTES = WavFile.SAMPLE_RATE * 2 * MAX_SECONDS
        /** 40 ms of audio per read: the level updates about as often as iOS meters. */
        const val READ_BYTES = WavFile.SAMPLE_RATE * 2 / 25
        const val JOIN_TIMEOUT_MS = 300L
        const val SILENCE_FLOOR = -50.0
        /** Time constant of the level's fall between syllables. */
        const val LEVEL_RELEASE = 0.25

        /** RMS of 16-bit little-endian samples in dBFS (iOS `averagePower`). */
        fun decibels(buffer: ByteArray, count: Int): Double {
            var sum = 0.0
            val samples = count / 2
            if (samples == 0) return SILENCE_FLOOR
            for (i in 0 until samples) {
                val sample = (buffer[2 * i].toInt() and 0xFF) or (buffer[2 * i + 1].toInt() shl 8)
                sum += sample.toDouble() * sample
            }
            val rms = sqrt(sum / samples) / 32768.0
            return if (rms <= 0) SILENCE_FLOOR else 20 * log10(rms)
        }
    }
}
