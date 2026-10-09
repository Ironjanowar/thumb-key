package com.dessalines.thumbkey.editing

import org.junit.Assert.assertEquals
import org.junit.Test

class TextBoundariesTest {
    @Test
    fun graphemeMovementKeepsUnicodeClustersAtomic() {
        val clusters = listOf("e\u0301", "👍🏽", "🇪🇸", "👩‍👩‍👧‍👦")
        val text = clusters.joinToString("")
        val expected =
            buildList {
                add(0)
                clusters.runningFold(0) { offset, cluster -> offset + cluster.length }.drop(1).forEach(::add)
            }

        assertEquals(expected, TextBoundaries.graphemeBoundaries(text))
        expected.zipWithNext().forEach { (start, end) ->
            assertEquals(end, TextBoundaries.nextGrapheme(text, start))
            assertEquals(start, TextBoundaries.previousGrapheme(text, end))
        }
    }

    @Test
    fun wordMovementSkipsWhitespaceAndTreatsPunctuationAsSeparateTokens() {
        val text = "  café's-test  "

        assertEquals(6, TextBoundaries.nextToken(text, 0))
        assertEquals(7, TextBoundaries.nextToken(text, 6))
        assertEquals(8, TextBoundaries.nextToken(text, 7))
        assertEquals(9, TextBoundaries.nextToken(text, 8))
        assertEquals(13, TextBoundaries.nextToken(text, 9))
        assertEquals(9, TextBoundaries.previousToken(text, 15))
        assertEquals(8, TextBoundaries.previousToken(text, 9))
        assertEquals(7, TextBoundaries.previousToken(text, 8))
        assertEquals(6, TextBoundaries.previousToken(text, 7))
        assertEquals(2, TextBoundaries.previousToken(text, 6))
    }

    @Test
    fun tokenDeletionDistancesUseTheSameBoundariesAsMovement() {
        val text = "αβ  — γδ"
        val cursor = text.length

        assertEquals(cursor - TextBoundaries.previousToken(text, cursor), TextBoundaries.deletePreviousTokenCount(text, cursor))
        assertEquals(TextBoundaries.nextToken(text, 0), TextBoundaries.deleteNextTokenCount(text, 0))
    }

    @Test
    fun graphemeMovementKeepsHangulIndicAndTagSequencesAtomic() {
        val clusters =
            listOf(
                "\u1100\u1161\u11A8", // Hangul Jamo L V T
                "क्षि", // Indic consonant + virama + consonant + vowel sign
                "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC7F", // emoji tag flag
                "\u0D9A\u0DCA\u0D9A", // Sinhala ka with al-lakuna and ka
            )
        val text = clusters.joinToString("")

        assertEquals(
            buildList {
                add(0)
                clusters.runningFold(0) { offset, cluster -> offset + cluster.length }.drop(1).forEach(::add)
            },
            TextBoundaries.graphemeBoundaries(text),
        )
    }

    @Test
    fun lineLimitsNeverSplitACarriageReturnPair() {
        val text = "uno\r\ndos\r\ntres"

        assertEquals(3, TextBoundaries.lineEnd(text, 1))
        assertEquals(8, TextBoundaries.lineEnd(text, 5))
        assertEquals(5, TextBoundaries.lineStart(text, 6))
        assertEquals(10, TextBoundaries.lineStart(text, 14))
        assertEquals(text.length, TextBoundaries.lineEnd(text, 11))
        assertEquals(0, TextBoundaries.lineStart(text, 1))
    }
}
