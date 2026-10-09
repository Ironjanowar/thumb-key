package com.dessalines.thumbkey.editing

import android.R
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import com.dessalines.thumbkey.IMEService

/** Android boundary for [EditingController]. It rejects snapshots that cannot provide absolute offsets. */
class AndroidEditingPort(
    private val ime: IMEService,
) : EditingPort {
    private val connection
        get() = ime.currentInputConnection

    override fun snapshot(): EditorSnapshot? {
        if ((ime.currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL) == InputType.TYPE_NULL) return null
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.partialStartOffset != -1 || extracted.partialEndOffset != -1 || extracted.startOffset != 0) return null
        val text = extracted.text?.toString() ?: return null
        return EditorSnapshot(text, extracted.selectionStart, extracted.selectionEnd).takeIf(EditorSnapshot::isValid)
    }

    override fun setSelection(
        start: Int,
        end: Int,
    ): Boolean = connection.setSelection(start, end)

    override fun fallback(command: EditingCommand): Boolean =
        when (command) {
            EditingCommand.MoveCharacterBackward -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
            EditingCommand.MoveCharacterForward -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            EditingCommand.MoveWordBackward -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_CTRL_ON)
            EditingCommand.MoveWordForward -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON)
            EditingCommand.MoveVisualUp -> sendKey(KeyEvent.KEYCODE_DPAD_UP)
            EditingCommand.MoveVisualDown -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
            EditingCommand.MoveLineStart -> sendKey(KeyEvent.KEYCODE_MOVE_HOME)
            EditingCommand.MoveLineEnd -> sendKey(KeyEvent.KEYCODE_MOVE_END)
            EditingCommand.MoveTextStart -> sendKey(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_CTRL_ON)
            EditingCommand.MoveTextEnd -> sendKey(KeyEvent.KEYCODE_MOVE_END, KeyEvent.META_CTRL_ON)
            EditingCommand.DeletePreviousGrapheme -> sendKey(KeyEvent.KEYCODE_DEL)
            EditingCommand.DeleteNextGrapheme -> sendKey(KeyEvent.KEYCODE_FORWARD_DEL)
            EditingCommand.DeletePreviousWord -> sendKey(KeyEvent.KEYCODE_DEL, KeyEvent.META_CTRL_ON)
            EditingCommand.DeleteNextWord -> sendKey(KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.META_CTRL_ON)
            EditingCommand.Undo -> sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            EditingCommand.Redo -> sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            else -> false
        }

    override fun copy(): Boolean {
        if (!ime.clipboardUsePrivate()) return connection.performContextMenuAction(R.id.copy)
        val selected = connection.getSelectedText(0)?.takeIf { it.isNotEmpty() } ?: return false
        return ime.clipboardAddPrivateClip(selected.toString()) != null
    }

    override fun cut(): Boolean {
        if (!ime.clipboardUsePrivate()) return connection.performContextMenuAction(R.id.cut)
        val selected = connection.getSelectedText(0)?.takeIf { it.isNotEmpty() } ?: return false
        return ime.clipboardAddPrivateClip(selected.toString())?.let { connection.commitText("", 1) } ?: false
    }

    override fun paste(): Boolean {
        if (!ime.clipboardUsePrivate() || ime.clipboardWasLastCopyDoneViaSystem()) {
            return connection.performContextMenuAction(R.id.paste)
        }
        val text = ime.clipboardGetLastClip()?.takeIf { it.isNotEmpty() } ?: return false
        return connection.commitText(text, 1)
    }

    override fun deleteSelection(): Boolean = connection.commitText("", 1)

    override fun deleteSurrounding(
        before: Int,
        after: Int,
    ): Boolean = if (before == 0 && after == 0) false else connection.deleteSurroundingText(before, after)

    private fun sendKey(
        keyCode: Int,
        metaState: Int = 0,
    ): Boolean {
        val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState)
        return connection.sendKeyEvent(down) && connection.sendKeyEvent(up)
    }
}
