package com.dessalines.thumbkey.dictation

import com.dessalines.thumbkey.keyboards.TOGGLE_DICTATION_KEYC
import com.dessalines.thumbkey.keyboards.installDictationModeEntry
import com.dessalines.thumbkey.utils.KeyAction
import com.dessalines.thumbkey.utils.KeyActionSerializable
import com.dessalines.thumbkey.utils.KeyboardC
import com.dessalines.thumbkey.utils.KeyboardDefinition
import com.dessalines.thumbkey.utils.KeyboardLayout
import com.dessalines.thumbkey.utils.deserializeKeyModifications
import com.dessalines.thumbkey.utils.getCommonKeyCFromKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DictationGestureAndYamlTest {
    @Test fun toggleDictationYamlActionRoundTrips() {
        val yaml =
            """
            ESThumbKey:
              main:
                key0_0:
                  right:
                    keyAction: ToggleDictation
            """.trimIndent()

        val serialized =
            deserializeKeyModifications(yaml)
                .getValue("ESThumbKey")
                .main
                ?.key0_0
                ?.right
                ?.keyAction

        assertEquals(KeyActionSerializable.ToggleDictation, serialized)
        assertEquals(KeyAction.ToggleDictation, getCommonKeyCFromKeyAction(serialized)?.action)
    }

    @Test fun everyLayoutPreservesOccupiedGesturesAndOnlyFillsFreeNumericRightSlots() {
        assertEquals(226, KeyboardLayout.entries.size)
        KeyboardLayout.entries.forEach { layout ->
            val before = layout.keyboardDefinition
            assertSame(before, installDictationModeEntry(before, false))
            val after = installDictationModeEntry(before, true)
            modePairs(before, after).forEach { (oldMode, newMode) ->
                oldMode.arr.zip(newMode.arr).forEach { (oldRow, newRow) ->
                    oldRow.zip(newRow).forEach { (old, new) ->
                        val freeAnchor = (old.center.action as? KeyAction.ToggleNumericMode)?.enable == true && old.right == null
                        if (freeAnchor) {
                            assertSame(TOGGLE_DICTATION_KEYC, new.right)
                            assertEquals(old.copy(right = TOGGLE_DICTATION_KEYC), new)
                        } else {
                            assertSame(old, new)
                        }
                    }
                }
            }
            assertSame(after, installDictationModeEntry(after, true))
        }
    }

    private fun modePairs(
        before: KeyboardDefinition,
        after: KeyboardDefinition,
    ): List<Pair<KeyboardC, KeyboardC>> =
        with(before.modes) {
            listOfNotNull(
                main to after.modes.main,
                shifted to after.modes.shifted,
                numeric to after.modes.numeric,
                ctrled?.let { it to checkNotNull(after.modes.ctrled) },
                alted?.let { it to checkNotNull(after.modes.alted) },
                emoji?.let { it to checkNotNull(after.modes.emoji) },
            )
        }
}
