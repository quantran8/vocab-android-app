package com.syll.domain.practice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 16-bit little-endian PCM in a RIFF/WAVE container: what iOS `AVAudioRecorder` writes for the recording settings
 * (16 kHz, mono, 16 bit, integer, little endian) and what the backend passes to Azure Speech.
 */
object WavFile {
    const val SAMPLE_RATE = 16_000
    const val HEADER_SIZE = 44

    fun pcm16Mono(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val blockAlign = channels * bitsPerSample / 8
        return ByteBuffer.allocate(HEADER_SIZE + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * blockAlign)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
            put(pcm)
        }.array()
    }

    /** The samples of a WAV file's `data` chunk; null if [wav] isn't a RIFF/WAVE file. */
    fun pcmData(wav: ByteArray): ByteArray? {
        if (wav.size < 12 || String(wav, 0, 4, Charsets.US_ASCII) != "RIFF" || String(wav, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        while (offset + 8 <= wav.size) {
            val id = String(wav, offset, 4, Charsets.US_ASCII)
            val size = buffer.getInt(offset + 4)
            val start = offset + 8
            if (size < 0) return null
            if (id == "data") return wav.copyOfRange(start, minOf(start + size, wav.size))
            // Chunks are padded to an even size.
            offset = start + size + (size and 1)
        }
        return null
    }
}
