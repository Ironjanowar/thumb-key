package com.dessalines.thumbkey.dictation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class DictationTarget(
    val fieldToken: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

sealed interface DictationState {
    data object Idle : DictationState

    data class Preparing(
        val session: Long,
    ) : DictationState

    data class Recording(
        val session: Long,
        val elapsedSeconds: Int = 0,
    ) : DictationState

    data class Transcribing(
        val session: Long,
    ) : DictationState

    data class Error(
        val message: String,
    ) : DictationState
}

interface DictationEditor {
    fun isCurrent(target: DictationTarget): Boolean

    fun commit(
        target: DictationTarget,
        text: String,
    ): Boolean
}

class DictationController(
    private val editor: DictationEditor,
) {
    private val ids = AtomicLong()
    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    val state = _state.asStateFlow()
    private var target: DictationTarget? = null
    private var activeSession = 0L

    @Synchronized
    fun start(target: DictationTarget): Long {
        cancel()
        this.target = target
        activeSession = ids.incrementAndGet()
        _state.value = DictationState.Preparing(activeSession)
        return activeSession
    }

    @Synchronized
    fun recording(session: Long) {
        if (session == activeSession) _state.value = DictationState.Recording(session)
    }

    @Synchronized
    fun tick(
        session: Long,
        seconds: Int,
    ) {
        if (session == activeSession) _state.value = DictationState.Recording(session, seconds)
    }

    @Synchronized
    fun transcribing(session: Long) {
        if (session == activeSession) _state.value = DictationState.Transcribing(session)
    }

    @Synchronized
    fun complete(
        session: Long,
        text: String,
    ) {
        if (session != activeSession) return
        val destination = target
        invalidate()
        if (text.isNotBlank() && destination != null && editor.isCurrent(destination)) editor.commit(destination, text)
    }

    @Synchronized
    fun fail(
        session: Long,
        message: String,
    ) {
        if (session == activeSession) {
            activeSession = 0
            target = null
            _state.value = DictationState.Error(message)
        }
    }

    @Synchronized
    fun cancel() = invalidate()

    @Synchronized
    fun isActive(session: Long): Boolean = session != 0L && session == activeSession

    @Synchronized
    fun cancelIfDestinationChanged(): Boolean {
        val destination = target ?: return false
        if (editor.isCurrent(destination)) return false
        invalidate()
        return true
    }

    private fun invalidate() {
        activeSession = 0
        target = null
        _state.value = DictationState.Idle
    }
}
