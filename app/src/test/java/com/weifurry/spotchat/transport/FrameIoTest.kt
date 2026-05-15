package com.weifurry.spotchat.transport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameIoTest {
    @Test
    fun framesRoundTripWithLengthPrefix() {
        val payload = "SpotChat".toByteArray()
        val output = ByteArrayOutputStream()

        FrameIo.writeFrame(output, payload)

        assertArrayEquals(
            payload,
            FrameIo.readFrame(ByteArrayInputStream(output.toByteArray()))
        )
    }

    @Test
    fun readFrameRejectsInvalidLength() {
        val invalidHeader =
            ByteBuffer
                .allocate(Int.SIZE_BYTES)
                .putInt(MAX_TEST_FRAME_BYTES + 1)
                .array()

        assertThrows(IllegalArgumentException::class.java) {
            FrameIo.readFrame(ByteArrayInputStream(invalidHeader))
        }
    }

    @Test
    fun readFrameReturnsNullForPartialPayload() {
        val partialFrame =
            ByteBuffer
                .allocate(Int.SIZE_BYTES + 2)
                .putInt(4)
                .put(byteArrayOf(1, 2))
                .array()

        assertNull(FrameIo.readFrame(ByteArrayInputStream(partialFrame)))
    }

    @Test
    fun writeFrameRejectsOversizedPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameIo.writeFrame(ByteArrayOutputStream(), ByteArray(MAX_TEST_FRAME_BYTES + 1))
        }
    }

    private companion object {
        private const val MAX_TEST_FRAME_BYTES = 256 * 1024
    }
}
