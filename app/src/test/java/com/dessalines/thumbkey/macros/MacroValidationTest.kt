package com.dessalines.thumbkey.macros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroValidationTest {
    @Test
    fun `valid unicode key and literal multiline expansion are preserved`() {
        val macro = MacroDefinition(key = "café2", expansions = listOf("  hola\n🙂  "), mode = MacroMode.MANUAL)

        assertTrue(MacroValidation.validate(macro, emptyList()).errors.isEmpty())
        assertEquals("  hola\n🙂  ", macro.expansions.single())
    }

    @Test
    fun `empty and punctuated keys and empty expansions are rejected`() {
        assertTrue(MacroValidation.validate(MacroDefinition(key = "", expansions = listOf("x")), emptyList()).errors.isNotEmpty())
        assertTrue(MacroValidation.validate(MacroDefinition(key = "a-b", expansions = listOf("x")), emptyList()).errors.isNotEmpty())
        assertTrue(MacroValidation.validate(MacroDefinition(key = "a", expansions = listOf("")), emptyList()).errors.isNotEmpty())
    }

    @Test
    fun `multiple automatic expansions are rejected`() {
        val macro = MacroDefinition(key = "x", expansions = listOf("one", "two"), mode = MacroMode.IMMEDIATE)
        assertTrue(MacroValidation.validate(macro, emptyList()).errors.contains(MacroValidationError.MULTIPLE_AUTOMATIC_EXPANSIONS))
    }

    @Test
    fun `unicode caseless conflicts use the same folding as matching`() {
        listOf("Straße" to "STRASSE", "ΟΣ" to "ος", "İ" to "i\u0307").forEachIndexed { index, (first, second) ->
            val existing = MacroDefinition(id = index.toLong() + 1, key = first, expansions = listOf("x"))
            val proposed = MacroDefinition(id = index.toLong() + 10, key = second, expansions = listOf("y"))
            assertTrue(
                "$first and $second must conflict",
                MacroValidation.validate(proposed, listOf(existing)).errors.contains(MacroValidationError.KEY_CONFLICT),
            )
        }
    }

    @Test
    fun `overlapping case domains conflict while two sensitive keys may coexist`() {
        val insensitive = MacroDefinition(id = 1, key = "JJ", expansions = listOf("x"), caseSensitive = false)
        val sensitive = MacroDefinition(id = 2, key = "jj", expansions = listOf("y"), caseSensitive = true)
        assertTrue(MacroValidation.validate(sensitive, listOf(insensitive)).errors.contains(MacroValidationError.KEY_CONFLICT))

        val upper = insensitive.copy(caseSensitive = true)
        assertTrue(MacroValidation.validate(sensitive, listOf(upper)).errors.isEmpty())
    }

    @Test
    fun `immediate prefix warning is symmetric across mixed sensitivity`() {
        val sensitiveImmediate =
            MacroDefinition(id = 1, key = "A", expansions = listOf("x"), caseSensitive = true, mode = MacroMode.IMMEDIATE)
        val insensitiveLong = MacroDefinition(id = 2, key = "ab", expansions = listOf("y"), caseSensitive = false)

        assertTrue(
            MacroValidation
                .validate(insensitiveLong, listOf(sensitiveImmediate))
                .warnings
                .contains(MacroValidationWarning.IMMEDIATE_PREFIX),
        )
        assertTrue(
            MacroValidation
                .validate(sensitiveImmediate, listOf(insensitiveLong))
                .warnings
                .contains(MacroValidationWarning.IMMEDIATE_PREFIX),
        )
    }

    @Test
    fun `immediate prefix is a warning rather than an error`() {
        val short = MacroDefinition(id = 1, key = "a", expansions = listOf("x"), mode = MacroMode.IMMEDIATE)
        val long = MacroDefinition(id = 2, key = "ab", expansions = listOf("y"))
        val result = MacroValidation.validate(long, listOf(short))
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.contains(MacroValidationWarning.IMMEDIATE_PREFIX))

        val configuringShort = MacroValidation.validate(short, listOf(long))
        assertTrue(configuringShort.warnings.contains(MacroValidationWarning.IMMEDIATE_PREFIX))
    }
}
