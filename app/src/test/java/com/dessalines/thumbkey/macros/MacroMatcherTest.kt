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

    @Test fun `symbol-leading mixed key fires at a word start and not after a letter`() {
        val atMe = MacroDefinition(30, "@ME", listOf("you@example.com"), false, MacroMode.IMMEDIATE)
        assertEquals(1 until 4, MacroMatcher.matchAutomatic(listOf(atMe), MacroSnapshot(" @ME", 4, 4), "E")?.keyRange)
        assertEquals(0 until 3, MacroMatcher.matchAutomatic(listOf(atMe), MacroSnapshot("@ME", 3, 3), "E")?.keyRange)
        assertNull(MacroMatcher.matchAutomatic(listOf(atMe), MacroSnapshot("hola@ME", 7, 7), "E"))
        assertNull(MacroMatcher.matchAutomatic(listOf(atMe), MacroSnapshot("1@ME", 4, 4), "E"))
    }

    @Test fun `caseless symbol key matches a differently cased typing`() {
        val atMe = MacroDefinition(31, "@ME", listOf("x"), false, MacroMode.IMMEDIATE)
        val match = MacroMatcher.matchAutomatic(listOf(atMe), MacroSnapshot(" @me", 4, 4), "e")
        assertEquals("@me", match?.originalKey)
        assertEquals(1 until 4, match?.keyRange)
    }

    @Test fun `symbol key with exact case matches as typed`() {
        val atMe = MacroDefinition(32, "@ME", listOf("x"), true, MacroMode.IMMEDIATE)
        assertEquals(1 until 4, MacroMatcher.matchAutomatic(listOf(atMe), MacroSnapshot(" @ME", 4, 4), "E")?.keyRange)
        assertNull(MacroMatcher.matchAutomatic(listOf(atMe), MacroSnapshot(" @me", 4, 4), "e"))
    }

    @Test fun `punctuation-only key fires at a word start and not after a letter or digit`() {
        val dot = MacroDefinition(33, ".", listOf("full stop"), false, MacroMode.IMMEDIATE)
        assertEquals(0 until 1, MacroMatcher.matchAutomatic(listOf(dot), MacroSnapshot(".", 1, 1), ".")?.keyRange)
        assertEquals(1 until 2, MacroMatcher.matchAutomatic(listOf(dot), MacroSnapshot(" .", 2, 2), ".")?.keyRange)
        assertNull(MacroMatcher.matchAutomatic(listOf(dot), MacroSnapshot("a.", 2, 2), "."))
        assertNull(MacroMatcher.matchAutomatic(listOf(dot), MacroSnapshot("1.", 2, 2), "."))
    }

    @Test fun `emoji key fires at a word start and not after a letter`() {
        val smile = MacroDefinition(34, "🙂", listOf(":-)"), false, MacroMode.IMMEDIATE)
        val match = MacroMatcher.matchAutomatic(listOf(smile), MacroSnapshot(" 🙂", 3, 3), "🙂")
        assertEquals("🙂", match?.originalKey)
        assertEquals(1 until 3, match?.keyRange)
        assertNull(MacroMatcher.matchAutomatic(listOf(smile), MacroSnapshot("a🙂", 3, 3), "🙂"))
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

    @Test fun `a combining mark attached to the boundary still starts a word`() {
        val acute = "\u0301"
        val caselessBoundary =
            MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot(".${acute}JJ", 4, 4), "J")
        assertEquals(2 until 4, caselessBoundary?.keyRange)
        val sensitiveBoundary =
            MacroMatcher.matchAutomatic(
                listOf(immediate.copy(caseSensitive = true)),
                MacroSnapshot(".${acute}JJ", 4, 4),
                "J",
            )
        assertEquals(2 until 4, sensitiveBoundary?.keyRange)
    }

    @Test fun `several marks on a boundary cluster are treated as one boundary`() {
        val marks = "\u0301\u0302"
        val match = MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot(".${marks}JJ", 5, 5), "J")
        assertEquals(3 until 5, match?.keyRange)
    }

    @Test fun `a mark inside a word does not create a word boundary`() {
        val acute = "\u0301"
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("x${acute}JJ", 4, 4), "J"))
    }

    @Test fun `send actions and selected snapshots never match`() {
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("JJ", 2, 2), null))
        assertNull(MacroMatcher.matchAutomatic(listOf(immediate), MacroSnapshot("JJ", 0, 2), "J"))
    }
}
