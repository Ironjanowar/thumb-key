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

    @Test fun `manual match requires cursor immediately after complete key`() {
        assertEquals(listOf("Hola", "Buenas", "¿Qué tal?"), MacroMatcher.manualCandidates(listOf(manual), MacroSnapshot("x. saludo", 9, 9))?.expansions)
        assertNull(MacroMatcher.manualCandidates(listOf(manual), MacroSnapshot("saludo ", 7, 7)))
        assertNull(MacroMatcher.manualCandidates(listOf(manual), MacroSnapshot("salud", 5, 5)))
    }

    @Test fun `matching is root case insensitive unless requested`() {
        assertEquals("Jj", MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("Jj", 2, 2), "j")?.originalKey)
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate.copy(caseSensitive = true)), MacroSnapshot("Jj", 2, 2), "j"))
    }

    @Test fun `send actions and selected snapshots never match`() {
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("JJ", 2, 2), null))
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("JJ", 0, 2), "J"))
    }
}
