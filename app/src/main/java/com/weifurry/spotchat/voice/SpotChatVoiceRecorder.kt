package com.weifurry.spotchat.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

data class RecordedVoiceMessage(
    val file: File,
    val durationMs: Long,
    val audioBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordedVoiceMessage) return false

        return file == other.file &&
            durationMs == other.durationMs &&
            audioBytes.contentEquals(other.audioBytes)
    }

    override fun hashCode(): Int {
        var result = file.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + audioBytes.contentHashCode()
        return result
    }
}

class SpotChatVoiceRecorder(
    context: Context
) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtEpochMillis: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start() {
        check(recorder == null) {
            "Voice recording is already active"
        }
        val voiceDir = File(appContext.cacheDir, VOICE_CACHE_DIR).apply { mkdirs() }
        val file = File.createTempFile("spotchat-voice-", ".m4a", voiceDir)
        val mediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(AUDIO_SAMPLE_RATE_HZ)
            setAudioChannels(AUDIO_CHANNELS)
            setAudioEncodingBitRate(AUDIO_BIT_RATE)
            setMaxDuration(MAX_RECORDING_DURATION_MS.toInt())
            setMaxFileSize(MAX_RECORDING_BYTES.toLong())
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        outputFile = file
        startedAtEpochMillis = System.currentTimeMillis()
        recorder = mediaRecorder
    }

    fun stop(): RecordedVoiceMessage? {
        val activeRecorder = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
        val durationMs = System.currentTimeMillis() - startedAtEpochMillis
        runCatching {
            activeRecorder.stop()
        }
        activeRecorder.release()
        if (file == null || !file.exists() || file.length() <= 0L) {
            file?.delete()
            return null
        }
        if (durationMs < MIN_DURATION_MS) {
            file.delete()
            return null
        }
        val audioBytes = file.readBytes()
        if (audioBytes.size > MAX_RECORDING_BYTES) {
            file.delete()
            throw IllegalStateException("语音太长，请录短一点")
        }
        return RecordedVoiceMessage(
            file = file,
            durationMs = durationMs.coerceAtMost(MAX_RECORDING_DURATION_MS),
            audioBytes = audioBytes
        )
    }

    fun cancel() {
        val activeRecorder = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        runCatching { activeRecorder?.stop() }
        activeRecorder?.release()
        file?.delete()
    }

    companion object {
        const val MAX_RECORDING_DURATION_MS = 8_000L
        const val MAX_RECORDING_BYTES = 96 * 1024
        private const val VOICE_CACHE_DIR = "voice"
        private const val MIN_DURATION_MS = 500L
        private const val AUDIO_SAMPLE_RATE_HZ = 16_000
        private const val AUDIO_CHANNELS = 1
        private const val AUDIO_BIT_RATE = 24_000
    }
}
