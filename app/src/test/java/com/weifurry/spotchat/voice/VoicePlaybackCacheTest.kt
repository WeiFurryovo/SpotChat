package com.weifurry.spotchat.voice

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlaybackCacheTest {
    @Test
    fun createsRandomPlaybackFilesInsideDedicatedDirectory() = withTemporaryCache { cacheRoot ->
        val cache = VoicePlaybackCache(cacheRoot)
        val first = cache.create(byteArrayOf(1, 2, 3))
        val second = cache.create(byteArrayOf(4, 5, 6))

        val firstDirectory = checkNotNull(first.parentFile)
        val secondDirectory = checkNotNull(second.parentFile)
        assertEquals(VoicePlaybackCache.DIRECTORY_NAME, firstDirectory.name)
        assertEquals(firstDirectory.canonicalFile, secondDirectory.canonicalFile)
        assertNotEquals(first.name, second.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertArrayEquals(byteArrayOf(4, 5, 6), second.readBytes())
    }

    @Test
    fun newCacheInstanceClearsFilesLeftByAnEarlierProcess() = withTemporaryCache { cacheRoot ->
        val firstCache = VoicePlaybackCache(cacheRoot)
        val staleFile = firstCache.create(byteArrayOf(7, 8, 9))
        assertTrue(staleFile.exists())

        VoicePlaybackCache(cacheRoot)

        assertFalse(staleFile.exists())
    }

    @Test
    fun deleteRemovesOnlyFilesOwnedByPlaybackCache() = withTemporaryCache { cacheRoot ->
        val cache = VoicePlaybackCache(cacheRoot)
        val playbackFile = cache.create(byteArrayOf(1))
        val outsideFile = cacheRoot.resolve("outside.m4a").apply { writeBytes(byteArrayOf(2)) }

        cache.delete(playbackFile)
        cache.delete(outsideFile)

        assertFalse(playbackFile.exists())
        assertTrue(outsideFile.exists())
    }

    @Test
    fun clearIsIdempotent() = withTemporaryCache { cacheRoot ->
        val cache = VoicePlaybackCache(cacheRoot)
        val first = cache.create(byteArrayOf(1))
        val second = cache.create(byteArrayOf(2))

        cache.clear()
        cache.clear()

        assertFalse(first.exists())
        assertFalse(second.exists())
    }

    private fun withTemporaryCache(block: (java.io.File) -> Unit) {
        val cacheRoot = Files.createTempDirectory("spotchat-voice-cache-test").toFile()
        try {
            block(cacheRoot)
        } finally {
            cacheRoot.deleteRecursively()
        }
    }
}
