package com.weifurry.spotchat.voice

import java.io.File

internal class VoicePlaybackCache(
    cacheRoot: File
) {
    private val directory = File(cacheRoot, DIRECTORY_NAME)

    init {
        clear()
    }

    fun create(audioBytes: ByteArray): File {
        check(directory.mkdirs() || directory.isDirectory) {
            "Unable to create voice playback cache"
        }
        val playbackFile = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, directory)
        return try {
            playbackFile.writeBytes(audioBytes)
            playbackFile
        } catch (error: Throwable) {
            playbackFile.delete()
            throw error
        }
    }

    fun delete(playbackFile: File?) {
        val file = playbackFile ?: return
        val cacheDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return
        val parent = runCatching { file.canonicalFile.parentFile }.getOrNull() ?: return
        if (parent == cacheDirectory) {
            runCatching { file.deleteRecursively() }
        }
    }

    fun clear() {
        directory.listFiles()?.forEach { file ->
            runCatching { file.deleteRecursively() }
        }
    }

    companion object {
        internal const val DIRECTORY_NAME = "voice-playback"
        private const val FILE_PREFIX = "spotchat-playback-"
        private const val FILE_SUFFIX = ".m4a"
    }
}
