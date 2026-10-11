package com.dessalines.thumbkey.dictation

import com.dessalines.thumbkey.db.DEFAULT_DICTATION_ENABLED
import com.dessalines.thumbkey.db.DEFAULT_DICTATION_LANGUAGE
import com.dessalines.thumbkey.db.DEFAULT_DICTATION_MODEL
import com.dessalines.thumbkey.keyboards.installDictationModeEntry
import com.dessalines.thumbkey.utils.KeyAction
import com.dessalines.thumbkey.utils.KeyC
import com.dessalines.thumbkey.utils.KeyItemC
import com.dessalines.thumbkey.utils.KeyboardC
import com.dessalines.thumbkey.utils.KeyboardDefinition
import com.dessalines.thumbkey.utils.KeyboardDefinitionModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DictationSettingsAndEntryTest {
    @Test fun defaultsAreOptInSpanishAndBase() {
        assertEquals(0, DEFAULT_DICTATION_ENABLED)
        assertEquals("es", DEFAULT_DICTATION_LANGUAGE)
        assertEquals("base", DEFAULT_DICTATION_MODEL)
    }

    @Test fun rightSwipeIsAddedWithoutReplacingOccupiedGesture() {
        val anchor = KeyItemC(center = KeyC(KeyAction.ToggleNumericMode(true)))
        val occupied = anchor.copy(right = KeyC(KeyAction.Copy))
        val definition = definition(anchor, occupied)

        val installed = installDictationModeEntry(definition, true)

        assertEquals(
            KeyAction.ToggleDictation,
            installed.modes.main.arr[0][0]
                .right
                ?.action,
        )
        assertEquals(
            KeyAction.Copy,
            installed.modes.main.arr[0][1]
                .right
                ?.action,
        )
        assertNull(
            definition.modes.main.arr[0][0]
                .right,
        )
        assertSame(definition, installDictationModeEntry(definition, false))
    }

    private fun definition(vararg keys: KeyItemC): KeyboardDefinition {
        val keyboard = KeyboardC(listOf(keys.toList()))
        return KeyboardDefinition("test", KeyboardDefinitionModes(keyboard, keyboard, keyboard))
    }
}
