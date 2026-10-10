package com.dessalines.thumbkey.macros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroMatcherTest {
    private val immediate = MacroDefinition(1, "JJ", listOf("jajajajaja"), false, MacroMode.IMMEDIATE)
    private val separated = immediate.copy(id = 2, mode = MacroMode.ON_SEPARATOR)
    private val manual = MacroDefinition(3, "saludo", listOf("Hola", "Buenas", "¿Qué tal?"))

    @Test fun `immediate matches complete key at field and word boundaries`() {
        assertEquals(0 until 2, MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("JJ", 2, 2), "J")?.keyRange)
        assertEquals(2 until 4, MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("a JJ derecha", 4, 4), "J")?.keyRange)
        assertEquals(3 until 5, MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("🙂 JJ", 5, 5), "J")?.keyRange)
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("relojJJ", 7, 7), "J"))
    }

    @Test fun `separator trigger preserves exact whitespace or punctuation`() {
        val space = MacroMatcher.matchAutomatic(listOf(separated), MacroSnapshot("JJ x", 3, 3), " ")!!
        assertEquals(" ", space.separator)
        assertEquals(0 until 2, space.keyRange)
        val comma = MacroMatcher.matchAutomatic(listOf(separated), MacroSnapshot("JJ,", 3, 3), ",")!!
        assertEquals(",", comma.separator)
    }

    @Test fun `separator trigger fires on a multi-char commit ending in punctuation`() {
        // A processed commit may carry the key plus the trailing separator in one chunk.
        val match = MacroMatcher.matchAutomatic(listOf(separated), MacroSnapshot("JJ.", 3, 3), "JJ.")
        assertEquals(".", match?.separator)
        assertEquals(0 until 2, match?.keyRange)
        assertEquals("JJ", match?.originalKey)
    }

    @Test fun `separator trigger uses only the trailing separator run of a chunk`() {
        // The chunk "J." completes the key and carries the separator in the same commit.
        val match = MacroMatcher.matchAutomatic(listOf(separated), MacroSnapshot("JJ.", 3, 3), "J.")
        assertEquals(".", match?.separator)
        assertEquals(0 until 2, match?.keyRange)
        // A non-separator character between the key and the trailing run breaks the trigger.
        assertNull(MacroMatcher.matchAutomatic(listOf(separated), MacroSnapshot("JJa.", 4, 4), "a."))
        // A chunk with no trailing separator does not trigger.
        assertNull(MacroMatcher.matchAutomatic(listOf(separated), MacroSnapshot("JJx", 3, 3), "x"))
    }

    @Test fun `unicode space separators trigger and establish word boundaries`() {
        val nbsp = "\u00a0"
        val trigger = MacroMatcher.matchAutomatic(listOf(separated), MacroSnapshot("JJ$nbsp", 3, 3), nbsp)
        assertEquals(nbsp, trigger?.separator)

        val afterBoundary = MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("x${nbsp}JJ", 4, 4), "J")
        assertEquals(2 until 4, afterBoundary?.keyRange)
    }

    @Test fun `manual match requires cursor immediately after complete key`() {
        assertEquals(
            listOf("Hola", "Buenas", "¿Qué tal?"),
            MacroMatcher.manualCandidates(listOf(manual), MacroSnapshot("x. saludo", 9, 9))?.expansions,
        )
        assertNull(MacroMatcher.manualCandidates(listOf(manual), MacroSnapshot("saludo ", 7, 7)))
        assertNull(MacroMatcher.manualCandidates(listOf(manual), MacroSnapshot("salud", 5, 5)))
    }

    @Test fun `matching is root case insensitive unless requested`() {
        assertEquals("Jj", MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("Jj", 2, 2), "j")?.originalKey)
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate.copy(caseSensitive = true)), MacroSnapshot("Jj", 2, 2), "j"))
    }

    @Test fun `unicode caseless matching handles folds whose UTF16 lengths differ`() {
        val cases =
            listOf(
                "ΟΣ" to "ος",
                "ΟΣ" to "οσ",
                "STRASSE" to "Straße",
                "İ" to "i\u0307",
            )
        cases.forEachIndexed { index, (key, typed) ->
            val definition = immediate.copy(id = 10L + index, key = key)
            val match = MacroMatcher.matchAutomatic(listOf(definition), MacroSnapshot(typed, typed.length, typed.length), typed.takeLast(1))
            assertEquals("caseless match for $key / $typed", typed, match?.originalKey)
            assertEquals(0 until typed.length, match?.keyRange)
        }
        assertNull(
            MacroMatcher.matchAutomatic(
                listOf(immediate.copy(key = "I")),
                MacroSnapshot("ı", 1, 1),
                "ı",
            ),
        )
        assertNull(
            MacroMatcher.matchAutomatic(
                listOf(immediate.copy(key = "cafe")),
                MacroSnapshot("café", 4, 4),
                "é",
            ),
        )
    }

    @Test fun `send actions and selected snapshots never match`() {
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("JJ", 2, 2), null))
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("JJ", 0, 2), "J"))
    }
}
