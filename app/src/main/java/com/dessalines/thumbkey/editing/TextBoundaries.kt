package com.dessalines.thumbkey.editing

/**
 * Unicode-aware cursor boundaries expressed as UTF-16 offsets.
 *
 * The grapheme walk covers what a phone editor actually meets: combining marks, spacing marks,
 * variation selectors, skin-tone modifiers, regional-indicator pairs and flags, emoji tag
 * sequences, ZWJ sequences, Hangul syllables and Indic consonant-linker-consonant clusters.
 * It is not a complete UAX #29 implementation, so another script may still be split.
 */
object TextBoundaries {
    fun graphemeBoundaries(text: String): List<Int> {
        val boundaries = mutableListOf(0)
        var offset = 0
        while (offset < text.length) {
            offset = nextClusterEnd(text, offset)
            boundaries += offset
        }
        return boundaries
    }

    fun previousGrapheme(
        text: String,
        offset: Int,
    ): Int = graphemeBoundaries(text).lastOrNull { it < offset.coerceIn(0, text.length) } ?: 0

    fun nextGrapheme(
        text: String,
        offset: Int,
    ): Int = graphemeBoundaries(text).firstOrNull { it > offset.coerceIn(0, text.length) } ?: text.length

    /** Start of the line holding [offset]. A CRLF pair is a single terminator, never split. */
    fun lineStart(
        text: String,
        offset: Int,
    ): Int {
        val index = offset.coerceIn(0, text.length)
        if (index == 0) return 0
        return text.lastIndexOf('\n', index - 1) + 1
    }

    /** End of the line holding [offset], before any CRLF pair that terminates it. */
    fun lineEnd(
        text: String,
        offset: Int,
    ): Int {
        val index = offset.coerceIn(0, text.length)
        val nextBreak = text.indexOf('\n', index)
        if (nextBreak < 0) return text.length
        return if (nextBreak > 0 && text[nextBreak - 1] == '\r') nextBreak - 1 else nextBreak
    }

    fun previousToken(
        text: String,
        offset: Int,
    ): Int {
        val boundaries = graphemeBoundaries(text)
        var index = boundaryIndexAtOrBefore(boundaries, offset.coerceIn(0, text.length))
        while (index > 0 && clusterKind(text, boundaries[index - 1], boundaries[index]) == ClusterKind.WHITESPACE) index--
        if (index == 0) return 0
        if (clusterKind(text, boundaries[index - 1], boundaries[index]) == ClusterKind.WORD) {
            while (index > 0 && clusterKind(text, boundaries[index - 1], boundaries[index]) == ClusterKind.WORD) index--
        } else {
            index--
        }
        return boundaries[index]
    }

    fun nextToken(
        text: String,
        offset: Int,
    ): Int {
        val boundaries = graphemeBoundaries(text)
        var index = boundaryIndexAtOrAfter(boundaries, offset.coerceIn(0, text.length))
        while (index < boundaries.lastIndex &&
            clusterKind(text, boundaries[index], boundaries[index + 1]) == ClusterKind.WHITESPACE
        ) {
            index++
        }
        if (index == boundaries.lastIndex) return text.length
        if (clusterKind(text, boundaries[index], boundaries[index + 1]) == ClusterKind.WORD) {
            while (index < boundaries.lastIndex && clusterKind(text, boundaries[index], boundaries[index + 1]) == ClusterKind.WORD) index++
        } else {
            index++
        }
        return boundaries[index]
    }

    fun deletePreviousTokenCount(
        text: String,
        offset: Int,
    ): Int = offset.coerceIn(0, text.length) - previousToken(text, offset)

    fun deleteNextTokenCount(
        text: String,
        offset: Int,
    ): Int = nextToken(text, offset) - offset.coerceIn(0, text.length)

    private fun nextClusterEnd(
        text: String,
        start: Int,
    ): Int {
        var offset = start
        val first = text.codePointAt(offset)
        offset += Character.charCount(first)

        // A leading prepend character belongs to the cluster that follows it
        if (isPrepend(first)) return if (offset < text.length) nextClusterEnd(text, offset) else offset
        if (first == '\r'.code && offset < text.length && text.codePointAt(offset) == '\n'.code) return offset + 1
        if (isRegionalIndicator(first) && offset < text.length && isRegionalIndicator(text.codePointAt(offset))) {
            return offset + Character.charCount(text.codePointAt(offset))
        }

        offset = consumeHangulTail(text, first, offset)
        return consumeClusterTail(text, offset)
    }

    private fun consumeHangulTail(
        text: String,
        first: Int,
        initialOffset: Int,
    ): Int {
        var offset = initialOffset
        var previous = hangulClass(first)
        while (offset < text.length) {
            val next = hangulClass(text.codePointAt(offset))
            if (!hangulSequenceContinues(previous, next)) break
            offset += Character.charCount(text.codePointAt(offset))
            previous = next
        }
        return offset
    }

    private fun consumeClusterTail(
        text: String,
        initialOffset: Int,
    ): Int {
        var offset = initialOffset
        var linkerSeen = false
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            when {
                isExtender(codePoint) -> {
                    if (isLinker(codePoint)) linkerSeen = true
                    offset += Character.charCount(codePoint)
                }

                codePoint == ZERO_WIDTH_JOINER -> {
                    linkerSeen = false
                    offset += Character.charCount(codePoint)
                    if (offset >= text.length) break
                    offset += Character.charCount(text.codePointAt(offset))
                }

                isTagCharacter(codePoint) -> {
                    offset += Character.charCount(codePoint)
                }

                // A consonant after a linker stays in the same cluster (Indic conjuncts)
                linkerSeen && Character.isLetter(codePoint) -> {
                    linkerSeen = false
                    offset += Character.charCount(codePoint)
                }

                else -> {
                    return offset
                }
            }
        }
        return offset
    }

    private fun hangulClass(codePoint: Int): HangulClass =
        when {
            codePoint in 0x1100..0x115F || codePoint in 0xA960..0xA97C -> {
                HangulClass.LEADING
            }

            codePoint in 0x1160..0x11A7 || codePoint in 0xD7B0..0xD7C6 -> {
                HangulClass.VOWEL
            }

            codePoint in 0x11A8..0x11FF || codePoint in 0xD7CB..0xD7FB -> {
                HangulClass.TRAILING
            }

            codePoint in 0xAC00..0xD7A3 -> {
                if ((codePoint - 0xAC00) % 28 == 0) HangulClass.SYLLABLE else HangulClass.SYLLABLE_WITH_TRAILING
            }

            else -> {
                HangulClass.NONE
            }
        }

    private fun hangulSequenceContinues(
        previous: HangulClass,
        next: HangulClass,
    ): Boolean =
        when (previous) {
            HangulClass.LEADING -> {
                next in
                    setOf(
                        HangulClass.LEADING,
                        HangulClass.VOWEL,
                        HangulClass.SYLLABLE,
                        HangulClass.SYLLABLE_WITH_TRAILING,
                    )
            }

            HangulClass.VOWEL, HangulClass.SYLLABLE -> {
                next == HangulClass.VOWEL || next == HangulClass.TRAILING
            }

            HangulClass.SYLLABLE_WITH_TRAILING, HangulClass.TRAILING -> {
                next == HangulClass.TRAILING
            }

            HangulClass.NONE -> {
                false
            }
        }

    private fun isExtender(codePoint: Int): Boolean =
        Character.getType(codePoint) in MARK_TYPES ||
            codePoint == ZERO_WIDTH_NON_JOINER ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint in 0xE0100..0xE01EF ||
            codePoint in 0x1F3FB..0x1F3FF

    private fun isLinker(codePoint: Int): Boolean = codePoint in LINKERS

    private fun isTagCharacter(codePoint: Int): Boolean = codePoint in 0xE0020..0xE007F

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

    private fun isPrepend(codePoint: Int): Boolean = codePoint in PREPEND

    private fun boundaryIndexAtOrBefore(
        boundaries: List<Int>,
        offset: Int,
    ): Int = boundaries.indexOfLast { it <= offset }.coerceAtLeast(0)

    private fun boundaryIndexAtOrAfter(
        boundaries: List<Int>,
        offset: Int,
    ): Int = boundaries.indexOfFirst { it >= offset }.let { if (it < 0) boundaries.lastIndex else it }

    private fun clusterKind(
        text: String,
        start: Int,
        end: Int,
    ): ClusterKind {
        val codePoint = text.codePointAt(start)
        if (Character.isWhitespace(codePoint)) return ClusterKind.WHITESPACE
        if (Character.isLetterOrDigit(codePoint)) return ClusterKind.WORD
        if (end > start && Character.getType(codePoint) in MARK_TYPES) return ClusterKind.WORD
        return ClusterKind.PUNCTUATION
    }

    private enum class ClusterKind {
        WHITESPACE,
        WORD,
        PUNCTUATION,
    }

    private enum class HangulClass {
        NONE,
        LEADING,
        VOWEL,
        TRAILING,
        SYLLABLE,
        SYLLABLE_WITH_TRAILING,
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val ZERO_WIDTH_NON_JOINER = 0x200C

    private val MARK_TYPES =
        setOf(
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
        )

    // Viramas and other conjunct linkers of the scripts that use them
    private val LINKERS =
        setOf(
            0x094D,
            0x09CD,
            0x0A4D,
            0x0ACD,
            0x0B4D,
            0x0BCD,
            0x0C4D,
            0x0CCD,
            0x0D3B,
            0x0D3C,
            0x0D4D,
            0x0DCA,
            0x0E3A,
            0x0EBA,
            0x0F84,
            0x1039,
            0x103A,
            0x1714,
            0x1734,
            0x17D2,
            0x1A60,
            0x1B44,
            0x1BAA,
            0x1BAB,
            0x1BF2,
            0x1BF3,
            0x2D7F,
            0xA806,
            0xA82C,
            0xA8C4,
            0xA953,
            0xA9C0,
            0xAAF6,
            0xABED,
            0x10A3F,
            0x11046,
            0x11070,
            0x1107F,
            0x110B9,
            0x11133,
            0x11134,
            0x111C0,
            0x11235,
            0x112EA,
            0x1134D,
            0x11442,
            0x11446,
            0x114C2,
            0x115BF,
            0x1163F,
            0x116B6,
            0x1172B,
            0x11839,
            0x1193D,
            0x1193E,
            0x119E0,
            0x11A34,
            0x11A47,
            0x11A99,
            0x11C3F,
            0x11D44,
            0x11D45,
            0x11D97,
        )

    // Arabic number signs, Syriac and other prepend characters (UAX #29 Prepend property)
    private val PREPEND =
        setOf(
            0x0600,
            0x0601,
            0x0602,
            0x0603,
            0x0604,
            0x0605,
            0x06DD,
            0x070F,
            0x0890,
            0x0891,
            0x08E2,
            0x0D4E,
            0x110BD,
            0x110CD,
            0x111C2,
            0x111C3,
            0x1193F,
            0x11941,
            0x11A3A,
            0x11A84,
            0x11A85,
            0x11A86,
            0x11A87,
            0x11A88,
            0x11A89,
            0x11D46,
        )
}
