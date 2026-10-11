package com.dessalines.thumbkey.dictation

import android.content.Context
import com.dessalines.thumbkey.dictation.models.ModelRepository
import com.dessalines.thumbkey.dictation.models.WhisperModelCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictationRuntime(
    context: Context,
    private val scope: CoroutineScope,
    private val editor: AndroidDictationEditor,
) {
    private val context = context.applicationContext
    private val models = ModelRepository(context.noBackupFilesDir.resolve("whisper-models"))
    val controller = DictationController(editor)
    private val lock = Any()
    private var active: Active? = null

    private data class Active(
        val session: Long,
        val recorder: AndroidAudioRecorder,
        val engine: WhisperEngine,
        var job: Job? = null,
    )

    fun start(
        modelId: String,
        language: String,
    ) {
        val model =
            WhisperModelCatalog.find(modelId) ?: run {
                val session = controller.start(editor.target())
                controller.fail(session, "Unknown model")
                return
            }
        val current =
            synchronized(lock) {
                if (active != null) return
                val value = Active(controller.start(editor.target()), AndroidAudioRecorder(context), WhisperEngine())
                active = value
                value
            }
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val lease = withContext(Dispatchers.IO) { models.acquire(model) }
                    try {
                        val recorded =
                            withContext(Dispatchers.IO) {
                                current.engine.load(models.file(model).absolutePath)
                                if (!owns(current)) return@withContext RecordedAudio(ShortArray(0), false)
                                controller.recording(current.session)
                                current.recorder.record { controller.tick(current.session, it) }
                            }
                        if (!owns(current)) return@launch
                        if (!recorded.hasSpeech) {
                            controller.complete(current.session, "")
                            return@launch
                        }
                        controller.transcribing(current.session)
                        val text = withContext(Dispatchers.Default) { current.engine.transcribe(recorded.samples, language) }
                        if (owns(current)) controller.complete(current.session, text)
                    } finally {
                        lease.close()
                    }
                } catch (_: CancellationException) {
                    // Cancellation is the normal lifecycle path and must never surface as an error.
                } catch (error: Throwable) {
                    if (owns(current)) controller.fail(current.session, error.message ?: "Dictation failed")
                } finally {
                    synchronized(lock) { if (active === current) active = null }
                    withContext(NonCancellable + Dispatchers.IO) {
                        current.recorder.cancel()
                        current.engine.cancel()
                        current.engine.close()
                    }
                }
            }
        synchronized(lock) {
            if (active === current) current.job = job else job.cancel()
        }
        job.start()
    }

    fun stop() {
        synchronized(lock) { active }?.recorder?.stop()
    }

    fun cancel() {
        val cancelled =
            synchronized(lock) {
                val value = active
                active = null
                value
            }
        controller.cancel()
        cancelled?.recorder?.cancel()
        cancelled?.engine?.cancel()
        cancelled?.job?.cancel()
    }

    fun close() = cancel()

    private fun owns(candidate: Active): Boolean = synchronized(lock) { active === candidate && controller.isActive(candidate.session) }
}
