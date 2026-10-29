package dev.codux

import dev.codux.data.Utf8StreamDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Utf8StreamDecoderTest {
    @Test fun decodesCodePointSplitAcrossChunks() {
        val decoder = Utf8StreamDecoder()
        val bytes = "✓ codux".toByteArray(Charsets.UTF_8)
        assertEquals("", decoder.append(bytes.copyOfRange(0, 1)))
        assertEquals("✓ codux", decoder.append(bytes.copyOfRange(1, bytes.size)))
        assertEquals("", decoder.finish())
    }

    @Test fun finishReplacesAnIncompleteTrailingSequence() {
        val decoder = Utf8StreamDecoder()
        assertEquals("", decoder.append(byteArrayOf(0xE2.toByte(), 0x9C.toByte())))
        assertTrue(decoder.finish().contains("\uFFFD"))
        assertEquals("ready", decoder.append("ready".toByteArray(Charsets.UTF_8)))
    }

    @Test fun resetDropsBytesFromPreviousProcess() {
        val decoder = Utf8StreamDecoder()
        decoder.append(byteArrayOf(0xE2.toByte()))
        decoder.reset()
        assertEquals("new", decoder.append("new".toByteArray(Charsets.UTF_8)))
    }
}
