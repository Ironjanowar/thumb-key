package com.dessalines.thumbkey.dictation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
            engine.load(model.absolutePath)
            val text = engine.transcribe(samples, "en")
            assertTrue("Unexpected transcript: $text", text.contains("fellow Americans", ignoreCase = true))
        } finally {
            engine.close()
        }
    }
}
