package com.dessalines.thumbkey.utils

import com.dessalines.thumbkey.editing.EditingCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModificationEditingActionsTest {
    @Test
    fun everyEditingCommandHasAYamlActionMapping() {
        val expected =
            mapOf(
                KeyActionSerializable.EditingToggleMark to EditingCommand.ToggleMark,
                KeyActionSerializable.EditingSelectAll to EditingCommand.SelectAll,
                KeyActionSerializable.EditingSwapEnds to EditingCommand.SwapEnds,
                KeyActionSerializable.EditingCharacterBackward to EditingCommand.MoveCharacterBackward,
                KeyActionSerializable.EditingCharacterForward to EditingCommand.MoveCharacterForward,
                KeyActionSerializable.EditingWordBackward to EditingCommand.MoveWordBackward,
                KeyActionSerializable.EditingWordForward to EditingCommand.MoveWordForward,
                KeyActionSerializable.EditingVisualUp to EditingCommand.MoveVisualUp,
                KeyActionSerializable.EditingVisualDown to EditingCommand.MoveVisualDown,
                KeyActionSerializable.EditingLineStart to EditingCommand.MoveLineStart,
                KeyActionSerializable.EditingLineEnd to EditingCommand.MoveLineEnd,
                KeyActionSerializable.EditingTextStart to EditingCommand.MoveTextStart,
                KeyActionSerializable.EditingTextEnd to EditingCommand.MoveTextEnd,
                KeyActionSerializable.EditingCopy to EditingCommand.Copy,
                KeyActionSerializable.EditingCut to EditingCommand.Cut,
                KeyActionSerializable.EditingPaste to EditingCommand.Paste,
                KeyActionSerializable.EditingUndo to EditingCommand.Undo,
                KeyActionSerializable.EditingRedo to EditingCommand.Redo,
                KeyActionSerializable.EditingDeletePreviousGrapheme to EditingCommand.DeletePreviousGrapheme,
                KeyActionSerializable.EditingDeleteNextGrapheme to EditingCommand.DeleteNextGrapheme,
                KeyActionSerializable.EditingDeletePreviousWord to EditingCommand.DeletePreviousWord,
                KeyActionSerializable.EditingDeleteNextWord to EditingCommand.DeleteNextWord,
            )

        expected.forEach { (serializable, command) ->
            val action = getCommonKeyCFromKeyAction(serializable)?.action
            assertTrue("Missing mapping for $serializable", action is KeyAction.Editing)
            assertEquals(command, (action as KeyAction.Editing).command)
        }
    }

    @Test
    fun editingModeEntryAndExitAreMappedWithoutAddingAYamlMode() {
        assertEquals(
            true,
            (getCommonKeyCFromKeyAction(KeyActionSerializable.ToggleEditingMode)?.action as KeyAction.ToggleEditingMode).enable,
        )
        assertEquals(
            false,
            (getCommonKeyCFromKeyAction(KeyActionSerializable.ExitEditingMode)?.action as KeyAction.ToggleEditingMode).enable,
        )
        assertFalse(KeyboardDefinitionModesSerializable::class.members.any { it.name == "editing" })
    }

    @Test
    fun editingActionsDeserializeFromYaml() {
        val yaml =
            """
            ESThumbKey:
              main:
                key0_0:
                  center:
                    keyAction: EditingToggleMark
            """.trimIndent()

        val action =
            deserializeKeyModifications(yaml)
                .getValue("ESThumbKey")
                .main
                ?.key0_0
                ?.center
                ?.keyAction
        assertEquals(KeyActionSerializable.EditingToggleMark, action)
    }
}
