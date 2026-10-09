package com.dessalines.thumbkey.keyboards

import com.dessalines.thumbkey.utils.KeyAction
import com.dessalines.thumbkey.utils.KeyC
import com.dessalines.thumbkey.utils.KeyItemC
import com.dessalines.thumbkey.utils.KeyboardC
import com.dessalines.thumbkey.utils.KeyboardDefinition
import com.dessalines.thumbkey.utils.KeyboardDefinitionModes
import com.dessalines.thumbkey.utils.KeyboardLayout
import com.dessalines.thumbkey.utils.resolveKeyboardDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class EditingModeEntryAllLayoutsTest(
    private val layoutIndex: Int,
) {
    @Test
    fun preferenceTransformsOnlyFreeNumericAnchorsAndIsIdempotent() {
        assertEquals(LAYOUT_COUNT, KeyboardLayout.entries.size)
        val layout = KeyboardLayout.entries[layoutIndex]
        val original = layout.keyboardDefinition

        assertSame(original, installEditingModeEntry(original, enabled = false))

        val transformed = installEditingModeEntry(original, enabled = true)
        var installedCount = 0
        modePairs(original, transformed).forEach { (beforeMode, afterMode) ->
            beforeMode.arr.zip(afterMode.arr).forEach { (beforeRow, afterRow) ->
                beforeRow.zip(afterRow).forEach { (before, after) ->
                    val numericAnchor =
                        (before.center.action as? KeyAction.ToggleNumericMode)?.enable == true
                    if (numericAnchor && before.left == null) {
                        installedCount += 1
                        assertSame(TOGGLE_EDITING_TRUE_KEYC, after.left)
                        assertEquals(before.copy(left = TOGGLE_EDITING_TRUE_KEYC), after)
                    } else {
                        assertSame(before, after)
                    }
                }
            }
        }

        if (installedCount == 0) {
            assertSame(original, transformed)
        } else {
            assertNotSame(original, transformed)
        }
        assertSame(transformed, installEditingModeEntry(transformed, enabled = true))
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

    companion object {
        private const val LAYOUT_COUNT = 226

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun layouts(): List<Array<Int>> = (0 until LAYOUT_COUNT).map { arrayOf(it) }
    }
}

@RunWith(org.robolectric.RobolectricTestRunner::class)
class EditingModeEntryTest {
    @Test
    fun reportsLayoutCoverageCounts() {
        val transformed =
            KeyboardLayout.entries.count { layout ->
                installEditingModeEntry(layout.keyboardDefinition, enabled = true) !== layout.keyboardDefinition
            }
        val unchanged = KeyboardLayout.entries.size - transformed

        assertEquals(226, transformed + unchanged)
        println("editing-entry layouts transformed=$transformed unchanged=$unchanged")
    }

    @Test
    fun resolutionAppliesPreferenceAfterUserModifications() {
        val yaml =
            """
            ESThumbKey:
              main:
                key1_3:
                  left:
                    keyAction: SwitchLanguage
            """.trimIndent()

        val disabled = resolveKeyboardDefinition(KeyboardLayout.ESThumbKey, yaml, editingModeEnabled = false)
        val enabled = resolveKeyboardDefinition(KeyboardLayout.ESThumbKey, yaml, editingModeEnabled = true)

        assertSame(
            KeyAction.SwitchLanguage,
            disabled.modes.main.arr[1][3]
                .left
                ?.action,
        )
        assertSame(
            KeyAction.SwitchLanguage,
            enabled.modes.main.arr[1][3]
                .left
                ?.action,
        )
        assertSame(
            TOGGLE_EDITING_TRUE_KEYC,
            enabled.modes.shifted.arr[1][3]
                .left,
        )
    }

    @Test
    fun occupiedNumericAnchorGesturesAreNeverOverwritten() {
        listOf(KeyAction.SwitchLanguage, KeyAction.Undo, KeyAction.CommitText("custom")).forEach { occupiedAction ->
            val occupied = numericAnchor(leftAction = occupiedAction)
            val definition = definitionWithMain(occupied)

            val transformed = installEditingModeEntry(definition, enabled = true)

            assertSame(definition, transformed)
            assertSame(
                occupiedAction,
                transformed.modes.main.arr[0][0]
                    .left
                    ?.action,
            )
        }
    }

    @Test
    fun t9WithoutNumericAnchorIsUnchanged() {
        val definition = KeyboardLayout.T9.keyboardDefinition

        assertTrue(allKeys(definition).none { (it.center.action as? KeyAction.ToggleNumericMode)?.enable == true })
        assertSame(definition, installEditingModeEntry(definition, enabled = true))
    }

    @Test
    fun spanishLayoutsReceiveEntryOnlyWhenEnabled() {
        listOf(KeyboardLayout.ESThumbKey, KeyboardLayout.ESMessagEase).forEach { layout ->
            val disabled = installEditingModeEntry(layout.keyboardDefinition, enabled = false)
            val enabled = installEditingModeEntry(layout.keyboardDefinition, enabled = true)

            assertSame(layout.keyboardDefinition, disabled)
            assertEquals(2, allKeys(enabled).count { it.left === TOGGLE_EDITING_TRUE_KEYC })
        }
    }

    private fun numericAnchor(leftAction: KeyAction) =
        KeyItemC(
            center = KeyC(action = KeyAction.ToggleNumericMode(true)),
            left = KeyC(action = leftAction),
        )

    private fun definitionWithMain(item: KeyItemC): KeyboardDefinition {
        val mode = KeyboardC(listOf(listOf(item)))
        return KeyboardDefinition("test", KeyboardDefinitionModes(mode, mode, mode))
    }

    private fun allKeys(definition: KeyboardDefinition) =
        with(definition.modes) { listOfNotNull(main, shifted, numeric, ctrled, alted, emoji) }
            .flatMap { it.arr.flatten() }
}
