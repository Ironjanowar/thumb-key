package com.dessalines.thumbkey.keyboards

import com.dessalines.thumbkey.editing.EditingCommand
import com.dessalines.thumbkey.utils.FontSizeVariant
import com.dessalines.thumbkey.utils.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditingKeyboardTest {
    @Test
    fun panelKeepsFourByFourGeometryAndPlacesEveryCommand() {
        val keyboard = editingKeyboard(markActive = false, markLabel = "Mark", selectingLabel = "Selecting")

        assertEquals(4, keyboard.arr.size)
        assertTrue(keyboard.arr.all { row -> row.sumOf { it.widthMultiplier } == 4 })
        assertCommand(EditingCommand.MoveLineStart, keyboard.arr[0][0].center.action)
        assertCommand(EditingCommand.MoveTextStart, keyboard.arr[0][0].top?.action)
        assertCommand(EditingCommand.MoveVisualUp, keyboard.arr[0][1].center.action)
        assertCommand(EditingCommand.MoveLineEnd, keyboard.arr[0][2].center.action)
        assertCommand(EditingCommand.MoveTextEnd, keyboard.arr[0][2].bottom?.action)
        assertCommand(EditingCommand.MoveCharacterBackward, keyboard.arr[1][0].center.action)
        assertCommand(EditingCommand.MoveWordBackward, keyboard.arr[1][0].left?.action)
        assertCommand(EditingCommand.ToggleMark, keyboard.arr[1][1].center.action)
        assertCommand(EditingCommand.SelectAll, keyboard.arr[1][1].top?.action)
        assertCommand(EditingCommand.SwapEnds, keyboard.arr[1][1].bottom?.action)
        assertCommand(EditingCommand.MoveCharacterForward, keyboard.arr[1][2].center.action)
        assertCommand(EditingCommand.MoveWordForward, keyboard.arr[1][2].right?.action)
        assertEquals(false, (keyboard.arr[1][3].center.action as KeyAction.ToggleEditingMode).enable)
        assertCommand(EditingCommand.Copy, keyboard.arr[2][0].top?.action)
        assertCommand(EditingCommand.Cut, keyboard.arr[2][0].topRight?.action)
        assertCommand(EditingCommand.Paste, keyboard.arr[2][0].bottom?.action)
        assertCommand(EditingCommand.Undo, keyboard.arr[2][0].left?.action)
        assertCommand(EditingCommand.Redo, keyboard.arr[2][0].right?.action)
        assertCommand(EditingCommand.MoveVisualDown, keyboard.arr[2][1].center.action)
        assertCommand(EditingCommand.DeletePreviousGrapheme, keyboard.arr[2][2].center.action)
        assertCommand(EditingCommand.DeletePreviousWord, keyboard.arr[2][2].left?.action)
        assertCommand(EditingCommand.DeleteNextWord, keyboard.arr[2][2].right?.action)
        assertCommand(EditingCommand.DeleteNextGrapheme, keyboard.arr[2][2].top?.action)
    }

    @Test
    fun activeMarkChangesLabelColorAndShapeEmphasis() {
        val inactive = editingKeyboard(false, "Mark", "Selecting").arr[1][1].center
        val active = editingKeyboard(true, "Mark", "Selecting").arr[1][1].center

        assertEquals("Mark", inactive.displayText())
        assertEquals("Selecting", active.displayText())
        assertFalse(inactive.emphasized)
        assertTrue(active.emphasized)
        assertFalse(inactive.color == active.color)
    }

    @Test
    fun directionalLegendsStaySmallerThanCenterActions() {
        val keyboard = editingKeyboard(false, "Mark", "Selecting")

        assertEquals(FontSizeVariant.LARGE, keyboard.arr[0][0].center.size)
        assertEquals(FontSizeVariant.SMALL, keyboard.arr[0][0].top?.size)
        assertEquals(FontSizeVariant.SMALL, keyboard.arr[1][0].left?.size)
        assertEquals(FontSizeVariant.SMALL, keyboard.arr[2][0].topRight?.size)
        assertEquals(FontSizeVariant.SMALL, keyboard.arr[2][2].left?.size)
    }

    @Test
    fun spanishEntryUsesASpecializedNumericKeyWithoutChangingTheSharedKey() {
        assertTrue(NUMERIC_EDITING_KEY_ITEM.center.action is KeyAction.ToggleNumericMode)
        assertEquals(true, (NUMERIC_EDITING_KEY_ITEM.left?.action as KeyAction.ToggleEditingMode).enable)
        assertTrue(NUMERIC_KEY_ITEM.left == null)
    }

    private fun assertCommand(
        expected: EditingCommand,
        action: KeyAction?,
    ) = assertEquals(expected, (action as KeyAction.Editing).command)

    private fun com.dessalines.thumbkey.utils.KeyC.displayText(): String? =
        (display as? com.dessalines.thumbkey.utils.KeyDisplay.TextDisplay)?.text
}
