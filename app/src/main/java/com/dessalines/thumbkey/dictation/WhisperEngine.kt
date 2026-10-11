package com.dessalines.thumbkey.dictation

object WhisperNative {
    init {
        System.loadLibrary("thumbkey_whisper")
    }

    external fun systemInfo(): String

    external fun create(modelPath: String): Long

    external fun transcribe(
        handle: Long,
        pcm16: ShortArray,
        language: String,
    ): String?

    external fun cancel(handle: Long)

    external fun destroy(handle: Long)
}

class WhisperEngine : AutoCloseable {
    private val lock = Any()
    private var handle = 0L

    fun load(modelPath: String) {
        val created = WhisperNative.create(modelPath)
        check(created != 0L) { "Unable to load Whisper model" }
        synchronized(lock) {
            check(handle == 0L) { "Whisper model is already loaded" }
            handle = created
        }
    }

    fun transcribe(
        pcm16: ShortArray,
        language: String,
    ): String {
        val current = synchronized(lock) { handle }
        check(current != 0L) { "Whisper model is not loaded" }
        return WhisperNative.transcribe(current, pcm16, language)?.trim().orEmpty()
    }

    fun cancel() {
        val current = synchronized(lock) { handle }
        if (current != 0L) WhisperNative.cancel(current)
    }

    override fun close() {
        val current =
            synchronized(lock) {
                val value = handle
                handle = 0L
                value
            }
        if (current != 0L) WhisperNative.destroy(current)
    }
}
