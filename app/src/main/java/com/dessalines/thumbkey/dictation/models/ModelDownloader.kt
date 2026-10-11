package com.dessalines.thumbkey.dictation.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class ModelDownloader(
    private val directory: File,
    private val openStream: suspend (WhisperModel) -> InputStream,
) {
    suspend fun download(
        model: WhisperModel,
        progress: (Long, Long) -> Unit,
    ): File {
        check(directory.mkdirs() || directory.isDirectory) { "Could not create model directory" }
        val part = File(directory, "${model.fileName}.part")
        val target = File(directory, model.fileName)
        val previous = File(directory, "${model.fileName}.previous")
        check(ModelFileCoordination.downloadStarted(part)) { "A download for ${model.title} is already in progress" }
        part.delete()
        previous.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            openStream(model).use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val remainingWithOverflowByte = model.bytes - copied + 1
                        val request = minOf(buffer.size.toLong(), maxOf(1L, remainingWithOverflowByte)).toInt()
                        val read = input.read(buffer, 0, request)
                        if (read < 0) break
                        if (read == 0) continue
                        check(copied + read <= model.bytes) { "Downloaded model exceeds expected size" }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        progress(copied, model.bytes)
                    }
                    output.flush()
                }
            }
            val hash = digest.digest().toHex()
            check(copied == model.bytes && hash == model.sha256) { "Downloaded model failed integrity verification" }
            currentCoroutineContext().ensureActive()
            if (target.exists()) check(target.renameTo(previous)) { "Could not preserve existing model" }
            if (!part.renameTo(target)) {
                previous.renameTo(target)
                error("Could not publish downloaded model")
            }
            previous.delete()
            return target
        } catch (error: Throwable) {
            part.delete()
            if (!target.exists() && previous.exists()) previous.renameTo(target)
            throw error
        } finally {
            ModelFileCoordination.downloadFinished(part)
        }
    }
}

class ModelRepository(
    private val directory: File,
) {
    init {
        directory
            .listFiles { file -> file.name.endsWith(".part") || file.name.endsWith(".previous") }
            ?.forEach { file ->
                // A .previous artifact only exists inside a publish window; while that model's
                // download is in flight the file is not stale and must survive.
                val owner = if (file.name.endsWith(".previous")) File(directory, file.name.removeSuffix(".previous") + ".part") else file
                if (!ModelFileCoordination.isDownloading(owner)) file.delete()
            }
    }

    fun file(model: WhisperModel) = File(directory, model.fileName)

    fun isReady(model: WhisperModel): Boolean =
        ModelFileCoordination.locked {
            val artifact = file(model)
            artifact.isFile &&
                artifact.length() == model.bytes &&
                runCatching { artifact.sha256() == model.sha256 }.getOrDefault(false)
        }

    fun acquire(model: WhisperModel): Closeable {
        val artifact = file(model)
        return ModelFileCoordination.locked {
            check(isReady(model)) { "Model ${model.title} is not downloaded or is corrupt" }
            ModelFileCoordination.acquire(artifact)
        }
    }

    /**
     * Runs [block] with the model leased. The lease is taken uncancellably so that a cancellation
     * landing while the artifact is being verified cannot strand the use-count, and it is always
     * released on the way out — including when [block] is cancelled.
     */
    suspend fun <T> withModel(
        model: WhisperModel,
        block: suspend (File) -> T,
    ): T {
        val lease = withContext(NonCancellable + Dispatchers.IO) { acquire(model) }
        try {
            return block(file(model))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { lease.close() }
        }
    }

    fun delete(model: WhisperModel): Boolean =
        ModelFileCoordination.locked {
            val artifact = file(model)
            if (ModelFileCoordination.isInUse(artifact)) return@locked false
            File(directory, "${model.fileName}.part").delete()
            File(directory, "${model.fileName}.previous").delete()
            !artifact.exists() || artifact.delete()
        }
}

internal object ModelFileCoordination {
    private val uses = mutableMapOf<String, Int>()
    private val downloads = mutableSetOf<String>()

    fun <T> locked(block: () -> T): T = synchronized(this, block)

    fun acquire(file: File): Closeable {
        val path = file.canonicalPath
        uses[path] = (uses[path] ?: 0) + 1
        var closed = false
        return Closeable {
            locked {
                if (!closed) {
                    closed = true
                    val remaining = (uses[path] ?: 1) - 1
                    if (remaining == 0) uses.remove(path) else uses[path] = remaining
                }
            }
        }
    }

    fun isInUse(file: File): Boolean = (uses[file.canonicalPath] ?: 0) > 0

    /** Registers a download, returning false when this file already has one in flight. */
    fun downloadStarted(file: File): Boolean = locked { downloads.add(file.canonicalPath) }

    fun downloadFinished(file: File) = locked { downloads -= file.canonicalPath }

    fun isDownloading(file: File): Boolean = locked { file.canonicalPath in downloads }
}

private fun File.sha256(): String =
    inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().toHex()
    }

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
