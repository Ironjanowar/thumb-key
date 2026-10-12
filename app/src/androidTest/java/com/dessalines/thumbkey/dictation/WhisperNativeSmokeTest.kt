package com.dessalines.thumbkey.dictation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dessalines.thumbkey.dictation.models.WhisperModel
import com.dessalines.thumbkey.dictation.models.WhisperModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class WhisperNativeSmokeTest {
    @Test
    fun loadsPinnedWhisperCppEngine() {
        assertTrue(WhisperNative.systemInfo().isNotBlank())
    }

    @Test
    fun cancelBeforeInferenceIsNotErasedByTranscribe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.getExternalFilesDir(null)!!.resolve("ggml-tiny.bin")
        check(model.isFile) { "Push ggml-tiny.bin to ${model.absolutePath} before this smoke test" }
        val engine = WhisperEngine()
        try {
            engine.load(model.absolutePath)
            engine.cancel()
            assertTrue(engine.transcribe(ShortArray(16_000), "en").isEmpty())
        } finally {
            engine.close()
        }
    }

    @Test
    fun transcribesKnownJfkFixtureWithRealModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.getExternalFilesDir(null)!!.resolve("ggml-tiny.bin")
        check(model.isFile) { "Push ggml-tiny.bin to ${model.absolutePath} before this smoke test" }
        assertKnownJfkTranscript(model.absolutePath)
    }

    @Test
    fun transcribesKnownJfkFixtureWithTurboQ5() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pinned = checkNotNull(WhisperModelCatalog.find("turbo-q5"))
        val model = context.getExternalFilesDir(null)!!.resolve(pinned.fileName)
        check(model.isFile) { "Push ggml-large-v3-turbo-q5_0.bin to ${model.absolutePath} before this smoke test" }
        assertPinnedArtifact(model, pinned)
        assertKnownJfkTranscript(model.absolutePath)
    }

    private fun assertPinnedArtifact(
        file: File,
        model: WhisperModel,
    ) {
        assertEquals(model.bytes, file.length())
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        assertEquals(model.sha256, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun assertKnownJfkTranscript(modelPath: String) {
        val wav =
            InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open("jfk.wav")
                .use { it.readBytes() }
        val pcm = ByteBuffer.wrap(wav, 44, wav.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(pcm.remaining()).also(pcm::get)
        val engine = WhisperEngine()
        try {
            engine.load(modelPath)
            val text = engine.transcribe(samples, "en")
            assertTrue("Unexpected transcript: $text", text.contains("fellow Americans", ignoreCase = true))
        } finally {
            engine.close()
        }
    }
}
