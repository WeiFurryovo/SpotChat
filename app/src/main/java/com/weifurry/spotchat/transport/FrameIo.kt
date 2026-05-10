package com.weifurry.spotchat.transport

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

internal object FrameIo {
    private const val MAX_FRAME_BYTES = 256 * 1024

    fun readFrame(input: InputStream): ByteArray? {
        val header = readFullyOrNull(input, Int.SIZE_BYTES) ?: return null
        val length = ByteBuffer.wrap(header).int
        require(length in 0..MAX_FRAME_BYTES) {
            "Frame length $length is outside 0..$MAX_FRAME_BYTES"
        }
        return readFullyOrNull(input, length)
    }

    fun writeFrame(
        output: OutputStream,
        payload: ByteArray
    ) {
        require(payload.size <= MAX_FRAME_BYTES) {
            "Frame length ${payload.size} is larger than $MAX_FRAME_BYTES"
        }
        output.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(payload.size).array())
        output.write(payload)
        output.flush()
    }

    private fun readFullyOrNull(
        input: InputStream,
        length: Int
    ): ByteArray? {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read == -1) {
                return null
            }
            offset += read
        }
        return bytes
    }
}
