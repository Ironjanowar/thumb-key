package com.dessalines.thumbkey.macros

import com.dessalines.thumbkey.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroValidationMessagesTest {
    private fun messagesFor(
        macro: MacroDefinition,
        existing: List<MacroDefinition> = emptyList(),
    ): Set<Int> =
        MacroValidation
            .validate(macro, existing)
            .errors
            .map(MacroValidationMessages::messageResource)
            .toSet()

    @Test
    fun `a conflicting key maps to the conflict message and not the invalid-key message`() {
        // Case-insensitive by default: "jj" collides with an existing "JJ".
        val existing = MacroDefinition(id = 1, key = "JJ", expansions = listOf("x"))
        val proposed = MacroDefinition(id = 2, key = "jj", expansions = listOf("y"))

        val messages = messagesFor(proposed, listOf(existing))

        assertTrue(messages.contains(R.string.macro_error_key_conflict))
        assertFalse(messages.contains(R.string.macro_error_invalid_key))
        assertEquals(R.string.macro_error_key_conflict, MacroValidationMessages.messageResource(MacroValidationError.KEY_CONFLICT))
        assertFalse(MacroValidationMessages.messageResource(MacroValidationError.KEY_CONFLICT) == R.string.macro_error_invalid_key)
    }

    @Test
    fun `empty and whitespace keys map to the invalid-key message`() {
        listOf("", " ", "a b", "a\nb", "a\tb", "\u00a0").forEach { key ->
            val messages = messagesFor(MacroDefinition(key = key, expansions = listOf("x")))
            assertEquals("[$key] must map to the invalid-key message only", setOf(R.string.macro_error_invalid_key), messages)
        }
    }

    @Test
    fun `empty expansions map to the expansion message`() {
        val noExpansions = messagesFor(MacroDefinition(key = "x", expansions = emptyList()))
        val blankExpansion = messagesFor(MacroDefinition(key = "x", expansions = listOf("")))

        assertTrue(noExpansions.contains(R.string.macro_error_empty_expansion))
        assertTrue(blankExpansion.contains(R.string.macro_error_empty_expansion))
        assertEquals(R.string.macro_error_empty_expansion, MacroValidationMessages.messageResource(MacroValidationError.EMPTY_EXPANSIONS))
        assertEquals(R.string.macro_error_empty_expansion, MacroValidationMessages.messageResource(MacroValidationError.EMPTY_EXPANSION))
    }

    @Test
    fun `several expansions with an automatic mode map to the manual-mode message`() {
        val messages =
            messagesFor(
                MacroDefinition(key = "x", expansions = listOf("one", "two"), mode = MacroMode.IMMEDIATE),
            )

        assertTrue(messages.contains(R.string.macro_error_multiple_automatic))
        assertEquals(
            R.string.macro_error_multiple_automatic,
            MacroValidationMessages.messageResource(MacroValidationError.MULTIPLE_AUTOMATIC_EXPANSIONS),
        )
    }

    @Test
    fun `messages are grouped beside the control that caused them`() {
        val existing = MacroDefinition(id = 1, key = "JJ", expansions = listOf("x"))
        val proposed = MacroDefinition(id = 2, key = "jj", expansions = emptyList())
        val errors = MacroValidation.validate(proposed, listOf(existing)).errors

        assertEquals(
            listOf(R.string.macro_error_key_conflict),
            MacroValidationMessages.messagesFor(MacroValidationMessages.MacroEditorControl.KEY, errors),
        )
        assertEquals(
            listOf(R.string.macro_error_empty_expansion),
            MacroValidationMessages.messagesFor(MacroValidationMessages.MacroEditorControl.EXPANSIONS, errors),
        )
        assertTrue(MacroValidationMessages.messagesFor(MacroValidationMessages.MacroEditorControl.MODE, errors).isEmpty())
    }
}
