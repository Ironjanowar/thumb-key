package com.dessalines.thumbkey.macros

data class MacroSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    val hasCollapsedSelection: Boolean get() = selectionStart == selectionEnd && selectionStart in 0..text.length
}

data class MacroMatch(
    val macro: MacroDefinition,
    val keyRange: IntRange,
    val originalKey: String,
    val expansion: String,
    val separator: String = "",
)

data class ManualMacroMatch(
    val macro: MacroDefinition,
    val keyRange: IntRange,
    val originalKey: String,
) {
    val expansions: List<String> get() = macro.expansions
}

object MacroMatcher {
    fun matchAutomatic(
        macros: List<MacroDefinition>,
        snapshot: MacroSnapshot,
        directlyTypedText: String?,
    ): MacroMatch? {
        if (!snapshot.hasCollapsedSelection || directlyTypedText.isNullOrEmpty()) return null
        return macros
            .asSequence()
            .filter { it.mode != MacroMode.MANUAL && it.expansions.size == 1 }
            .mapNotNull { macro ->
                val separator =
                    if (macro.mode ==
                        MacroMode.ON_SEPARATOR
                    ) {
                        // The trigger is the actual separator run at the end of the confirmed
                        // chunk, which may also carry the last key characters (for example a
                        // chunk produced by a text processor or a custom key), not only a chunk
                        // made entirely of separators.
                        trailingSeparators(directlyTypedText).takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    } else {
                        ""
                    }
                val keyEnd = snapshot.selectionEnd - separator.length
                matchAt(macro, snapshot.text, keyEnd)?.let { range ->
                    MacroMatch(macro, range, snapshot.text.substring(range.first, range.last + 1), macro.expansions.single(), separator)
                }
            }.firstOrNull()
    }

    fun manualCandidates(
        macros: List<MacroDefinition>,
        snapshot: MacroSnapshot,
    ): ManualMacroMatch? {
        if (!snapshot.hasCollapsedSelection) return null
        return macros
            .asSequence()
            .filter { it.mode == MacroMode.MANUAL }
            .mapNotNull { macro ->
                matchAt(macro, snapshot.text, snapshot.selectionEnd)?.let { range ->
                    ManualMacroMatch(macro, range, snapshot.text.substring(range.first, range.last + 1))
                }
            }.firstOrNull()
    }

    private fun matchAt(
        macro: MacroDefinition,
        text: String,
        end: Int,
    ): IntRange? {
        if (end !in 0..text.length) return null
        val start =
            if (macro.caseSensitive) {
                end - macro.key.length
            } else {
                wordStart(text, end)
            }
        if (start < 0) return null
        val actual = text.substring(start, end)
        val equal = if (macro.caseSensitive) actual == macro.key else MacroValidation.fold(actual) == MacroValidation.fold(macro.key)
        if (!equal || !isWordBoundary(text, start)) return null
        return start until end
    }

    private fun wordStart(
        text: String,
        end: Int,
    ): Int {
        var offset = end
        while (offset > 0) {
            val cluster = clusterStart(text, offset)
            if (isBoundaryCodePoint(text.codePointAt(cluster))) return offset
            offset = cluster
        }
        return 0
    }

    /**
     * Start index of the base character (plus any combining marks) whose cluster ends at [end]. A
     * combining mark belongs to the base character it follows, so it never begins a word.
     */
    private fun clusterStart(
        text: String,
        end: Int,
    ): Int {
        var offset = end
        while (offset > 0 && isCombiningMark(text.codePointBefore(offset))) {
            offset -= Character.charCount(text.codePointBefore(offset))
        }
        if (offset == 0) return 0
        return offset - Character.charCount(text.codePointBefore(offset))
    }

    private fun isWordBoundary(
        text: String,
        offset: Int,
    ): Boolean {
        if (offset == 0) return true
        var base = offset
        while (base > 0 && isCombiningMark(text.codePointBefore(base))) {
            base -= Character.charCount(text.codePointBefore(base))
        }
        // Only combining marks with no base character: not a word boundary.
        if (base == 0) return false
        return isBoundaryCodePoint(text.codePointBefore(base))
    }

    private fun isCombiningMark(codePoint: Int): Boolean =
        Character.getType(codePoint) in
            setOf(
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
            )

    fun isSeparator(value: String): Boolean =
        value.isNotEmpty() &&
            value.codePoints().allMatch { cp ->
                isBoundaryCodePoint(cp)
            }

    /**
     * The maximal run of separator code points at the end of a confirmed commit. The rest of the
     * chunk (if any) is key text and stays part of the matched word.
     */
    private fun trailingSeparators(value: String): String {
        var start = value.length
        while (start > 0) {
            val cp = value.codePointBefore(start)
            if (!isBoundaryCodePoint(cp)) break
            start -= Character.charCount(cp)
        }
        return value.substring(start)
    }

    private fun isBoundaryCodePoint(cp: Int): Boolean =
        Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.getType(cp) in punctuationTypes

    private val punctuationTypes =
        setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
        )
}
