package dev.codux.data

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Decodes UTF-8 chunks without losing code points split across stream events. */
internal class Utf8StreamDecoder {
    private var pending = ByteArray(0)

    fun append(chunk: ByteArray): String {
        if (chunk.isEmpty()) return ""
        val bytes = pending + chunk
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(bytes.size.coerceAtLeast(1))
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        decoder.decode(input, output, false)
        pending = bytes.copyOfRange(input.position(), bytes.size)
        output.flip()
        return output.toString()
    }

    fun finish(): String {
        val finalText = String(pending, Charsets.UTF_8)
        pending = ByteArray(0)
        return finalText
    }

    fun reset() {
        pending = ByteArray(0)
    }
}
