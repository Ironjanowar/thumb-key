package com.dessalines.thumbkey.editing

sealed interface EditingCommand {
    data object ToggleMark : EditingCommand

    data object SelectAll : EditingCommand

    data object SwapEnds : EditingCommand

    data object MoveCharacterBackward : EditingCommand

    data object MoveCharacterForward : EditingCommand

    data object MoveWordBackward : EditingCommand

    data object MoveWordForward : EditingCommand

    data object MoveVisualUp : EditingCommand

    data object MoveVisualDown : EditingCommand

    data object MoveLineStart : EditingCommand

    data object MoveLineEnd : EditingCommand

    data object MoveTextStart : EditingCommand

    data object MoveTextEnd : EditingCommand

    data object Copy : EditingCommand

    data object Cut : EditingCommand

    data object Paste : EditingCommand

    data object Undo : EditingCommand

    data object Redo : EditingCommand

    data object DeletePreviousGrapheme : EditingCommand

    data object DeleteNextGrapheme : EditingCommand

    data object DeletePreviousWord : EditingCommand

    data object DeleteNextWord : EditingCommand
}

data class EditorSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    val isValid: Boolean
        get() = selectionStart in 0..text.length && selectionEnd in 0..text.length
    val hasSelection: Boolean
        get() = selectionStart != selectionEnd
    val lower: Int
        get() = minOf(selectionStart, selectionEnd)
    val upper: Int
        get() = maxOf(selectionStart, selectionEnd)
}

data class MarkState(
    val anchor: Int = 0,
    val head: Int = 0,
    val active: Boolean = true,
) {
    companion object {
        val Inactive = MarkState(active = false)
    }
}

interface EditingPort {
    fun snapshot(): EditorSnapshot?

    fun setSelection(
        start: Int,
        end: Int,
    ): Boolean

    fun fallback(command: EditingCommand): Boolean

    fun copy(): Boolean

    fun cut(): Boolean

    fun paste(): Boolean

    fun deleteSelection(): Boolean

    fun deleteSurrounding(
        before: Int,
        after: Int,
    ): Boolean
}

class EditingController(
    private val port: EditingPort,
    private val onMarkActiveChanged: (Boolean) -> Unit = {},
) {
    var markState: MarkState = MarkState.Inactive
        private set

    // A new user command expires the mutation expectation because some editors omit that callback.
    private var awaitingMutationCallback = false

    fun startSession() {
        awaitingMutationCallback = false
        updateMark(MarkState.Inactive)
    }

    fun finishSession() {
        awaitingMutationCallback = false
        updateMark(MarkState.Inactive)
    }

    fun disarm() = reduceMark(EditingEvent.Disarm)

    fun onSelectionUpdate(
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        if (awaitingMutationCallback) {
            awaitingMutationCallback = false
            return
        }
        if (!markState.active) return

        val controlledRange = normalized(markState.anchor, markState.head)
        val currentRange = reliableSnapshot()?.let { normalized(it.selectionStart, it.selectionEnd) }
        if (currentRange != controlledRange) disarm()
    }

    fun perform(command: EditingCommand) {
        awaitingMutationCallback = false
        when (command) {
            EditingCommand.ToggleMark -> toggleMark()

            EditingCommand.SelectAll -> selectAll()

            EditingCommand.SwapEnds -> swapEnds()

            EditingCommand.Copy -> copy()

            EditingCommand.Cut -> cut()

            EditingCommand.Paste -> if (port.paste()) finishMutation()

            EditingCommand.DeletePreviousGrapheme,
            EditingCommand.DeleteNextGrapheme,
            EditingCommand.DeletePreviousWord,
            EditingCommand.DeleteNextWord,
            -> delete(command)

            EditingCommand.Undo,
            EditingCommand.Redo,
            -> port.fallback(command)

            else -> move(command)
        }
    }

    private fun reliableSnapshot(): EditorSnapshot? = port.snapshot()?.takeIf(EditorSnapshot::isValid)

    private fun toggleMark() {
        if (markState.active) {
            // Collapsing the range moves the editor's cursor, so it needs confirmed offsets
            val snapshot = reliableSnapshot()
            if (snapshot != null && markState.head in 0..snapshot.text.length) {
                port.setSelection(markState.head, markState.head)
            }
            disarm()
            return
        }
        val snapshot = reliableSnapshot() ?: return
        reduceMark(EditingEvent.Activate(snapshot.selectionStart, snapshot.selectionEnd))
    }

    private fun selectAll() {
        val snapshot = reliableSnapshot() ?: return
        if (port.setSelection(0, snapshot.text.length)) {
            reduceMark(EditingEvent.Activate(0, snapshot.text.length))
        }
    }

    private fun swapEnds() {
        if (!markState.active) return
        val snapshot = reliableSnapshot() ?: return
        val swapped = EditingReducer.reduce(markState, EditingEvent.SwapEnds)
        val withinText = swapped.anchor in 0..snapshot.text.length && swapped.head in 0..snapshot.text.length
        if (!withinText) return
        if (port.setSelection(swapped.anchor, swapped.head)) updateMark(swapped)
    }

    private fun move(command: EditingCommand) {
        val snapshot = reliableSnapshot()
        if (snapshot == null) {
            if (!markState.active) port.fallback(command)
            return
        }

        val backward = command.isBackward()
        if (!markState.active && snapshot.hasSelection) {
            val edge = if (backward) snapshot.lower else snapshot.upper
            port.setSelection(edge, edge)
            return
        }
        if (markState.active && (command == EditingCommand.MoveVisualUp || command == EditingCommand.MoveVisualDown)) return
        if (!markState.active && (command == EditingCommand.MoveVisualUp || command == EditingCommand.MoveVisualDown)) {
            port.fallback(command)
            return
        }

        val origin = if (markState.active) markState.head else snapshot.selectionEnd
        val target =
            movementTarget(command, snapshot.text, origin) ?: run {
                port.fallback(command)
                return
            }
        if (markState.active) {
            if (port.setSelection(markState.anchor, target)) reduceMark(EditingEvent.MoveHead(target))
        } else {
            port.setSelection(target, target)
        }
    }

    private fun movementTarget(
        command: EditingCommand,
        text: String,
        origin: Int,
    ): Int? =
        when (command) {
            EditingCommand.MoveCharacterBackward -> {
                TextBoundaries.previousGrapheme(text, origin)
            }

            EditingCommand.MoveCharacterForward -> {
                TextBoundaries.nextGrapheme(text, origin)
            }

            EditingCommand.MoveWordBackward -> {
                TextBoundaries.previousToken(text, origin)
            }

            EditingCommand.MoveWordForward -> {
                TextBoundaries.nextToken(text, origin)
            }

            EditingCommand.MoveLineStart -> {
                TextBoundaries.lineStart(text, origin)
            }

            EditingCommand.MoveLineEnd -> {
                TextBoundaries.lineEnd(text, origin)
            }

            EditingCommand.MoveTextStart -> {
                0
            }

            EditingCommand.MoveTextEnd -> {
                text.length
            }

            else -> {
                null
            }
        }

    private fun copy() {
        val snapshot = reliableSnapshot() ?: return
        if (snapshot.hasSelection) port.copy()
    }

    private fun cut() {
        val snapshot = reliableSnapshot() ?: return
        if (snapshot.hasSelection && port.cut()) finishMutation()
    }

    private fun delete(command: EditingCommand) {
        val snapshot = reliableSnapshot()
        if (snapshot == null) {
            if (!markState.active) port.fallback(command)
            return
        }
        val success =
            if (snapshot.hasSelection) {
                port.deleteSelection()
            } else {
                val cursor = snapshot.selectionEnd
                when (command) {
                    EditingCommand.DeletePreviousGrapheme -> {
                        port.deleteSurrounding(cursor - TextBoundaries.previousGrapheme(snapshot.text, cursor), 0)
                    }

                    EditingCommand.DeleteNextGrapheme -> {
                        port.deleteSurrounding(0, TextBoundaries.nextGrapheme(snapshot.text, cursor) - cursor)
                    }

                    EditingCommand.DeletePreviousWord -> {
                        port.deleteSurrounding(TextBoundaries.deletePreviousTokenCount(snapshot.text, cursor), 0)
                    }

                    EditingCommand.DeleteNextWord -> {
                        port.deleteSurrounding(0, TextBoundaries.deleteNextTokenCount(snapshot.text, cursor))
                    }

                    else -> {
                        false
                    }
                }
            }
        if (success) finishMutation()
    }

    /** A successful text mutation disarms the mark and makes its callback a resync, not a move. */
    private fun finishMutation() {
        awaitingMutationCallback = true
        disarm()
    }

    private fun updateMark(newState: MarkState) {
        val changed = markState.active != newState.active
        markState = newState
        if (changed) onMarkActiveChanged(newState.active)
    }

    private fun reduceMark(event: EditingEvent) = updateMark(EditingReducer.reduce(markState, event))

    private fun EditingCommand.isBackward(): Boolean =
        this == EditingCommand.MoveCharacterBackward ||
            this == EditingCommand.MoveWordBackward ||
            this == EditingCommand.MoveVisualUp ||
            this == EditingCommand.MoveLineStart ||
            this == EditingCommand.MoveTextStart

    private fun normalized(
        start: Int,
        end: Int,
    ): Pair<Int, Int> = minOf(start, end) to maxOf(start, end)
}
