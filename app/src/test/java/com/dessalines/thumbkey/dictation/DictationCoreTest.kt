package com.dessalines.thumbkey.dictation

import com.dessalines.thumbkey.dictation.models.ModelDownloader
import com.dessalines.thumbkey.dictation.models.WhisperModelCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

class DictationCoreTest {
    @Test
    fun catalogPinsExactlyTheMultilingualModels() {
        val models = WhisperModelCatalog.models
        assertEquals(listOf("tiny", "base", "small"), models.map { it.id })
        assertEquals("base", WhisperModelCatalog.recommended.id)
        models.forEach {
            assertTrue(it.url.startsWith("https://huggingface.co/"))
            assertFalse(it.fileName.contains(".en"))
            assertTrue(it.bytes > 0)
            assertTrue(it.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(it.revision.matches(Regex("[0-9a-f]{40}")))
        }
    }

    @Test
    fun downloaderPublishesOnlyVerifiedBytes() =
        runBlocking {
            val root = createTempDirectory("models").toFile()
            val bytes = "verified model".toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val model = WhisperModelCatalog.models.first().copy(bytes = bytes.size.toLong(), sha256 = hash)
            val downloader = ModelDownloader(root) { ByteArrayInputStream(bytes) }

            val result = downloader.download(model) { _, _ -> }

            assertEquals(File(root, model.fileName), result)
            assertTrue(result.isFile)
            assertFalse(File(root, "${model.fileName}.part").exists())
        }

    @Test
    fun downloaderRejectsCorruptBytesWithoutPublishing() =
        runBlocking {
            val root = createTempDirectory("models").toFile()
            val model = WhisperModelCatalog.models.first().copy(bytes = 3, sha256 = "0".repeat(64))
            val downloader = ModelDownloader(root) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }

            val error = runCatching { downloader.download(model) { _, _ -> } }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertFalse(File(root, model.fileName).exists())
            assertFalse(File(root, "${model.fileName}.part").exists())
        }

    @Test
    fun controllerCommitsOnlyCurrentSuccessfulSession() =
        runBlocking {
            val editor = FakeEditor()
            val controller = DictationController(editor)
            val session = controller.start(DictationTarget("field", 1, 3))
            controller.recording(session)
            controller.transcribing(session)

            controller.complete(session, "hola")
            controller.complete(session, "late")

            assertEquals(listOf("hola"), editor.commits)
            assertEquals(DictationState.Idle, controller.state.value)
        }

    @Test
    fun cancellationNeverMutatesEditor() =
        runBlocking {
            val editor = FakeEditor()
            val controller = DictationController(editor)
            val session = controller.start(DictationTarget("field", 0, 0))

            controller.cancel()
            assertFalse(controller.isActive(session))
            controller.complete(session, "must not appear")

            assertTrue(editor.commits.isEmpty())
            assertEquals(DictationState.Idle, controller.state.value)
        }

    @Test
    fun duplicateSelectionCallbackKeepsSessionButChangedDestinationCancels() =
        runBlocking {
            val editor = FakeEditor()
            val controller = DictationController(editor)
            val session = controller.start(DictationTarget("field", 0, 0))
            controller.recording(session)

            controller.cancelIfDestinationChanged()
            assertTrue(controller.state.value is DictationState.Recording)

            editor.current = false
            controller.cancelIfDestinationChanged()
            assertEquals(DictationState.Idle, controller.state.value)
        }

    private class FakeEditor : DictationEditor {
        val commits = mutableListOf<String>()
        var current = true

        override fun isCurrent(target: DictationTarget) = current

        override fun commit(
            target: DictationTarget,
            text: String,
        ): Boolean {
            commits += text
            return true
        }
    }
}
