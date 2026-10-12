package com.dessalines.thumbkey.dictation

import com.dessalines.thumbkey.keyboards.TOGGLE_DICTATION_KEYC
import com.dessalines.thumbkey.keyboards.installDictationModeEntry
import com.dessalines.thumbkey.utils.KeyAction
import com.dessalines.thumbkey.utils.KeyActionSerializable
import com.dessalines.thumbkey.utils.KeyC
import com.dessalines.thumbkey.utils.KeyItemC
import com.dessalines.thumbkey.utils.KeyboardC
import com.dessalines.thumbkey.utils.KeyboardDefinition
import com.dessalines.thumbkey.utils.KeyboardDefinitionModes
import com.dessalines.thumbkey.utils.KeyboardLayout
import com.dessalines.thumbkey.utils.deserializeKeyModifications
import com.dessalines.thumbkey.utils.getCommonKeyCFromKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
                  left:
                    keyAction: ToggleDictation
            """.trimIndent()

        val serialized =
            deserializeKeyModifications(yaml)
                .getValue("ESThumbKey")
                .main
                ?.key0_0
                ?.left
                ?.keyAction

        assertEquals(KeyActionSerializable.ToggleDictation, serialized)
        assertEquals(KeyAction.ToggleDictation, getCommonKeyCFromKeyAction(serialized)?.action)
    }

    @Test fun spanishMessageaseGetsTheEntryOnItsTopRightKeyAndLeavesTheNumericKeyAlone() {
        val before = KeyboardLayout.ESMessagEase.keyboardDefinition
        assertEquals(
            KeyAction.SwitchLanguage,
            before.modes.main.arr[0][3]
                .left
                ?.action,
        )
        assertEquals(
            KeyAction.SwitchIMEVoice,
            before.modes.main.arr[0][3]
                .bottomLeft
                ?.action,
        )

        val after = installDictationModeEntry(before, true)

        val topRight = after.modes.main.arr[0][3]
        assertEquals(KeyAction.ToggleDictation, topRight.left?.action)
        assertEquals(KeyAction.SwitchLanguage, topRight.bottomLeft?.action)
        assertTrue(
            "the voice-IME shortcut must be gone from that key",
            topRight.cells().none { it?.action == KeyAction.SwitchIMEVoice },
        )
        assertNull(
            "the numeric key must stay untouched",
            after.modes.main.arr[1][3]
                .right,
        )
    }

    @Test fun everyLayoutEitherSwapsItsSpecialKeyOrFallsBackToTheNumericKey() {
        assertEquals(226, KeyboardLayout.entries.size)
        var swapped = 0
        var fellBack = 0
        KeyboardLayout.entries.forEach { layout ->
            val before = layout.keyboardDefinition
            assertSame(before, installDictationModeEntry(before, false))

            val cluster = modes(before).any { mode -> clusterCells(mode).isNotEmpty() }
            val after = installDictationModeEntry(before, true)

            if (cluster) swapped++ else fellBack++

            modePairs(before, after).forEach { (oldMode, newMode) ->
                oldMode.arr.zip(newMode.arr).forEach { (oldRow, newRow) ->
                    oldRow.zip(newRow).forEach { (old, new) ->
                        when {
                            isCluster(old) -> {
                                assertEquals(KeyAction.ToggleDictation, new.left?.action)
                                assertEquals(KeyAction.SwitchLanguage, new.bottomLeft?.action)
                            }

                            !cluster -> {
                                val freeAnchor = (old.center.action as? KeyAction.ToggleNumericMode)?.enable == true && old.right == null
                                if (freeAnchor) {
                                    assertSame(TOGGLE_DICTATION_KEYC, new.right)
                                    assertEquals(old.copy(right = TOGGLE_DICTATION_KEYC), new)
                                } else {
                                    assertSame(old, new)
                                }
                            }

                            else -> {
                                assertSame("${layout.name}: cluster layouts keep every other cell", old, new)
                            }
                        }
                    }
                }
            }

            val offered =
                cluster ||
                    modes(before).any { mode ->
                        mode.arr.flatten().any { item ->
                            (item.center.action as? KeyAction.ToggleNumericMode)?.enable == true && item.right == null
                        }
                    }
            assertEquals(
                "${layout.name}: the entry appears exactly when the layout offers a free slot",
                offered,
                dictationCells(after).isNotEmpty(),
            )
            assertSame("${layout.name} must be idempotent", after, installDictationModeEntry(after, true))
        }
        assertTrue("most layouts carry the special key, got $swapped", swapped > 150)
        assertTrue("some layouts need the numeric fallback, got $fellBack", fellBack > 0)
    }

    @Test fun handBuiltClusterKeySwapsBothGestures() {
        val special =
            KeyItemC(
                center = KeyC("a"),
                left = KeyC(KeyAction.SwitchLanguage),
                bottomLeft = KeyC(KeyAction.SwitchIMEVoice),
            )

        val installed = installDictationModeEntry(definition(special), true)

        assertEquals(
            KeyAction.ToggleDictation,
            installed.modes.main.arr[0][0]
                .left
                ?.action,
        )
        assertEquals(
            KeyAction.SwitchLanguage,
            installed.modes.main.arr[0][0]
                .bottomLeft
                ?.action,
        )
    }

    private fun isCluster(item: KeyItemC): Boolean =
        item.left?.action == KeyAction.SwitchLanguage && item.bottomLeft?.action == KeyAction.SwitchIMEVoice

    private fun clusterCells(mode: KeyboardC): List<KeyItemC> = mode.arr.flatten().filter(::isCluster)

    private fun dictationCells(definition: KeyboardDefinition): List<KeyC> =
        modes(definition).flatMap { mode ->
            mode.arr
                .flatten()
                .flatMap { item -> item.cells().filter { it?.action == KeyAction.ToggleDictation } }
                .filterNotNull()
        }

    private fun KeyItemC.cells(): List<KeyC?> = listOf(center, left, top, right, bottom, topLeft, topRight, bottomLeft, bottomRight)

    private fun modes(definition: KeyboardDefinition): List<KeyboardC> =
        with(definition.modes) { listOfNotNull(main, shifted, numeric, ctrled, alted, emoji) }

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

    private fun definition(vararg keys: KeyItemC): KeyboardDefinition {
        val keyboard = KeyboardC(listOf(keys.toList()))
        return KeyboardDefinition("test", KeyboardDefinitionModes(keyboard, keyboard, keyboard))
    }
}
