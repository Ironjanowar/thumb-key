package com.dessalines.thumbkey.dictation

import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import com.dessalines.thumbkey.dictation.models.ModelDownloader
import com.dessalines.thumbkey.dictation.models.ModelFileCoordination
import com.dessalines.thumbkey.dictation.models.ModelRepository
import com.dessalines.thumbkey.dictation.models.WhisperModelCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

class ModelLeaseAndDownloadCancellationTest {
    @Test fun cancelledDownloadDoesNotPublishOrKeepItsRegistration() =
        runBlocking {
            val root = createTempDirectory("cancelled").toFile()
            val payload = ByteArray(1 shl 20) { 3 }
            val model = WhisperModelCatalog.models.first().copy(bytes = payload.size.toLong(), sha256 = sha256(payload))
            val stream = PacedStream(payload)

            val job = launch(Dispatchers.IO) { ModelDownloader(root) { stream }.download(model) { _, _ -> } }
            assertTrue(stream.firstChunkRead.await(10, TimeUnit.SECONDS))
            job.cancel()
            job.join()

            assertFalse("a cancelled download must not publish the model", File(root, model.fileName).exists())
            assertFalse("a cancelled download must not leave a .part behind", File(root, "${model.fileName}.part").exists())

            ModelDownloader(root) { ByteArrayInputStream(payload) }.download(model) { _, _ -> }
            assertTrue("the cancelled download must release its registration", File(root, model.fileName).exists())
        }

    @Test fun concurrentDownloadOfTheSameModelIsRefused() =
        runBlocking {
            val root = createTempDirectory("concurrent").toFile()
            val payload = ByteArray(1 shl 20) { 5 }
            val model = WhisperModelCatalog.models.first().copy(bytes = payload.size.toLong(), sha256 = sha256(payload))
            val stream = PacedStream(payload)

            val job = launch(Dispatchers.IO) { ModelDownloader(root) { stream }.download(model) { _, _ -> } }
            assertTrue(stream.firstChunkRead.await(10, TimeUnit.SECONDS))

            val failure =
                runCatching {
                    ModelDownloader(root) { ByteArrayInputStream(payload) }.download(model) { _, _ -> }
                }.exceptionOrNull()

            assertTrue("a second download of the same model must be refused, got $failure", failure is IllegalStateException)
            job.cancel()
            job.join()
        }

    @Test fun stalePreviousSurvivesWhileItsDownloadIsInFlight() {
        val root = createTempDirectory("previous").toFile()
        val model = WhisperModelCatalog.models.first()
        val part = File(root, "${model.fileName}.part")
        val previous = File(root, "${model.fileName}.previous").apply { writeText("publish in progress") }
        part.writeText("in flight")

        ModelFileCoordination.downloadStarted(part)
        try {
            ModelRepository(root)
            assertTrue("a .previous mid-publish must survive", previous.exists())
            assertTrue("an in-flight .part must survive", part.exists())
        } finally {
            ModelFileCoordination.downloadFinished(part)
        }
    }

    @Test fun cancellingWhileTheModelIsLeasedReleasesTheLease() =
        runBlocking {
            val root = createTempDirectory("lease").toFile()
            val payload = ByteArray(1 shl 16) { 9 }
            val model = WhisperModelCatalog.models.first().copy(bytes = payload.size.toLong(), sha256 = sha256(payload))
            File(root, model.fileName).writeBytes(payload)
            val repository = ModelRepository(root)

            val leased = CompletableDeferred<Unit>()
            val job =
                launch(Dispatchers.IO) {
                    repository.withModel(model) {
                        leased.complete(Unit)
                        delay(Long.MAX_VALUE)
                    }
                }
            leased.await()
            assertFalse("the model is leased while dictation runs", repository.delete(model))

            job.cancelAndJoin()

            assertTrue("cancelling dictation must release the model lease", repository.delete(model))
        }

    @Test fun cancellationDuringLeaseAcquisitionNeverStrandsTheLease() =
        runBlocking {
            val payload = ByteArray(1 shl 18) { 4 }
            val model = WhisperModelCatalog.models.first().copy(bytes = payload.size.toLong(), sha256 = sha256(payload))
            repeat(40) { attempt ->
                val root = createTempDirectory("acquire-$attempt").toFile()
                File(root, model.fileName).writeBytes(payload)
                val repository = ModelRepository(root)
                val job = launch(Dispatchers.IO) { repository.withModel(model) {} }
                delay(attempt.toLong() % 3)
                job.cancelAndJoin()
                assertTrue("attempt $attempt stranded the lease", repository.delete(model))
            }
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class PacedStream(
        bytes: ByteArray,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        val firstChunkRead = CountDownLatch(1)

        override fun read(): Int = delegate.read()

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            val read = delegate.read(b, off, minOf(len, 4096))
            if (read > 0) firstChunkRead.countDown()
            Thread.sleep(2)
            return read
        }
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
