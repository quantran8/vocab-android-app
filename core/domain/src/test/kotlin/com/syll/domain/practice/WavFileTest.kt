package com.syll.domain.practice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileTest {
    @Test fun headerDescribes16kHzMonoPcm() {
        val pcm = ByteArray(320) { it.toByte() }
        val wav = WavFile.pcm16Mono(pcm)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(WavFile.HEADER_SIZE + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(36 + pcm.size, header.getInt(4))
        assertEquals("WAVEfmt ", String(wav, 8, 8))
        assertEquals(16, header.getInt(16))
        assertEquals(1.toShort(), header.getShort(20))
        assertEquals(1.toShort(), header.getShort(22))
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28))
        assertEquals(2.toShort(), header.getShort(32))
        assertEquals(16.toShort(), header.getShort(34))
        assertEquals("data", String(wav, 36, 4))
        assertEquals(pcm.size, header.getInt(40))
    }

    @Test fun pcmDataRoundTrips() {
        val pcm = ByteArray(100) { (it * 3).toByte() }
        assertArrayEquals(pcm, WavFile.pcmData(WavFile.pcm16Mono(pcm)))
    }

    @Test fun pcmDataSkipsOtherChunks() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = WavFile.pcm16Mono(pcm)
        // A 3-byte LIST chunk (padded to 4) between fmt and data, as some recorders write.
        val list = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .put("LIST".toByteArray()).putInt(3).put(byteArrayOf(9, 9, 9, 0)).array()
        val withList = wav.copyOfRange(0, 36) + list + wav.copyOfRange(36, wav.size)
        assertArrayEquals(pcm, WavFile.pcmData(withList))
    }

    @Test fun notAWavFile() {
        assertNull(WavFile.pcmData(byteArrayOf(1, 2, 3)))
        assertNull(WavFile.pcmData("RIFF....AVI LIST".toByteArray()))
    }
}
