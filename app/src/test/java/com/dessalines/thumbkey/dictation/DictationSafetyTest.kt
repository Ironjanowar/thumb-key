package com.dessalines.thumbkey.dictation

import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import com.dessalines.thumbkey.dictation.models.ModelDownloader
import com.dessalines.thumbkey.dictation.models.ModelRepository
import com.dessalines.thumbkey.dictation.models.WhisperModelCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory

class AudioCaptureSafetyTest {
    @Test fun cancellationBeforeRecordNeverStartsSource() {
        val source = FakePcmSource()
        val cancelled = AtomicBoolean(true)

        val result = PcmCaptureLoop(source, { 0L }).record(cancelled) {}

        assertFalse(source.started)
        assertTrue(result.samples.isEmpty())
    }

    @Test fun monotonicDeadlineStopsEvenWhenReadsReturnZero() {
        var now = 0L
        val source = FakePcmSource(readResult = 0, afterRead = { now += 1_000_000_000L })

        val result = PcmCaptureLoop(source, { now }, maxSeconds = 3).record(AtomicBoolean()) {}

        assertEquals(3, source.reads)
        assertTrue(result.samples.isEmpty())
        assertTrue(source.released)
    }

    @Test fun stopThatInterruptsReadFinishesWithoutCaptureError() {
        val stop = AtomicBoolean(false)
        val source =
            object : PcmSource {
                var released = false

                override fun start() = Unit

                override fun read(buffer: ShortArray): Int {
                    stop.set(true)
                    error("AudioRecord stopped")
                }

                override fun stop() = Unit

                override fun release() {
                    released = true
                }
            }

        val result = PcmCaptureLoop(source, { 0L }, shouldStop = stop::get).record(AtomicBoolean()) {}

        assertTrue(result.samples.isEmpty())
        assertTrue(source.released)
    }

    @Test fun silenceIsNotSpeechButConservativeVoiceEnergyIs() {
        val silence = FakePcmSource(chunks = listOf(ShortArray(160) { 20 }, ShortArray(0)))
        val voice = FakePcmSource(chunks = listOf(ShortArray(160) { if (it % 2 == 0) 1200 else -1200 }, ShortArray(0)))

        assertFalse(PcmCaptureLoop(silence, { 0L }).record(AtomicBoolean()) {}.hasSpeech)
        assertTrue(PcmCaptureLoop(voice, { 0L }).record(AtomicBoolean()) {}.hasSpeech)
    }

    private class FakePcmSource(
        private val readResult: Int? = null,
        private val afterRead: () -> Unit = {},
        private val chunks: List<ShortArray>? = null,
    ) : PcmSource {
        var started = false
        var released = false
        var reads = 0
        private var chunk = 0

        override fun start() {
            started = true
        }

        override fun read(buffer: ShortArray): Int {
            reads++
            val data = chunks?.getOrNull(chunk++)
            if (data != null) data.copyInto(buffer)
            afterRead()
            return data?.size ?: readResult ?: -1
        }

        override fun stop() = Unit

        override fun release() {
            released = true
        }
    }
}

class ModelIntegritySafetyTest {
    @Test fun downloaderAbortsAtFirstExcessByte() =
        runBlocking {
            val root = createTempDirectory("oversize").toFile()
            val expected = 4L
            val stream = CountingStream(ByteArray(100) { 7 })
            val model = WhisperModelCatalog.models.first().copy(bytes = expected, sha256 = "0".repeat(64))

            val failure = runCatching { ModelDownloader(root) { stream }.download(model) { _, _ -> } }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals(expected + 1, stream.delivered)
            assertFalse(File(root, model.fileName).exists())
        }

    @Test fun readinessHashesActualFileAndCleansStalePart() {
        val root = createTempDirectory("ready").toFile()
        val bytes = "right".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val model = WhisperModelCatalog.models.first().copy(bytes = bytes.size.toLong(), sha256 = hash)
        File(root, model.fileName).writeBytes("wrong".toByteArray())
        File(root, "${model.fileName}.sha256").writeText(hash)
        File(root, "${model.fileName}.part").writeText("partial")

        val repository = ModelRepository(root)

        assertFalse(repository.isReady(model))
        assertFalse(File(root, "${model.fileName}.part").exists())
    }

    @Test fun activeModelCannotBeDeleted() {
        val root = createTempDirectory("leased").toFile()
        val bytes = byteArrayOf(1, 2, 3)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val model = WhisperModelCatalog.models.first().copy(bytes = 3, sha256 = hash)
        File(root, model.fileName).writeBytes(bytes)
        val repository = ModelRepository(root)

        val lease = repository.acquire(model)
        assertFalse(repository.delete(model))
        assertTrue(File(root, model.fileName).exists())
        lease.close()
        assertTrue(repository.delete(model))
    }

    private class CountingStream(
        bytes: ByteArray,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var delivered = 0L

        override fun read(): Int = delegate.read().also { if (it >= 0) delivered++ }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = delegate.read(b, off, len).also { if (it > 0) delivered += it }
    }
}

@RunWith(RobolectricTestRunner::class)
class DictationEditorSafetyTest {
    @Test fun incompleteSnapshotRejectsExistingSelection() {
        val connection = MutableConnection().apply { snapshot = extracted(2, 5, partial = true) }
        var cached = 2 to 5
        val editor = AndroidDictationEditor({ connection }, { "field" }, { cached }, {})

        assertFalse(editor.isCurrent(DictationTarget("field", 2, 5)))
    }

    @Test fun mutationDuringBeforeCommitFailsClosed() {
        val connection = MutableConnection().apply { snapshot = extracted(2, 5) }
        val editor =
            AndroidDictationEditor(
                { connection },
                { "field" },
                { 2 to 5 },
                { connection.snapshot = extracted(6, 6) },
            )

        assertFalse(editor.commit(DictationTarget("field", 2, 5), "hola"))
        assertEquals(0, connection.commits)
    }

    private class MutableConnection : BaseInputConnection(View(RuntimeEnvironment.getApplication()), false) {
        var snapshot: ExtractedText? = null
        var commits = 0

        override fun getExtractedText(
            request: ExtractedTextRequest?,
            flags: Int,
        ) = snapshot

        override fun commitText(
            text: CharSequence?,
            newCursorPosition: Int,
        ): Boolean {
            commits++
            return true
        }
    }

    private fun extracted(
        start: Int,
        end: Int,
        partial: Boolean = false,
    ) = ExtractedText().apply {
        text = "0123456789"
        startOffset = 0
        partialStartOffset = if (partial) 0 else -1
        partialEndOffset = if (partial) 10 else -1
        selectionStart = start
        selectionEnd = end
    }
}
