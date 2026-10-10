package com.dessalines.thumbkey.macros

data class MacroSnapshot(val text: String, val selectionStart: Int, val selectionEnd: Int) {
    val hasCollapsedSelection: Boolean get() = selectionStart == selectionEnd && selectionStart in 0..text.length
}

data class MacroMatch(
    val macro: MacroDefinition,
    val keyRange: IntRange,
    val originalKey: String,
    val expansion: String,
    val separator: String = "",
)

data class ManualMacroMatch(val macro: MacroDefinition, val keyRange: IntRange, val originalKey: String) {
    val expansions: List<String> get() = macro.expansions
}

object MacroMatcher {
    fun matchAutomatic(
        macros: List<MacroDefinition>,
        snapshot: MacroSnapshot,
        directlyTypedText: String?,
    ): MacroMatch? {
        if (!snapshot.hasCollapsedSelection || directlyTypedText.isNullOrEmpty()) return null
        return macros.asSequence().filter { it.mode != MacroMode.MANUAL && it.expansions.size == 1 }.mapNotNull { macro ->
            val separator = if (macro.mode == MacroMode.ON_SEPARATOR) directlyTypedText.takeIf(::isSeparator) ?: return@mapNotNull null else ""
            val keyEnd = snapshot.selectionEnd - separator.length
            matchAt(macro, snapshot.text, keyEnd)?.let { range ->
                MacroMatch(macro, range, snapshot.text.substring(range.first, range.last + 1), macro.expansions.single(), separator)
            }
        }.firstOrNull()
    }

    fun manualCandidates(macros: List<MacroDefinition>, snapshot: MacroSnapshot): ManualMacroMatch? {
        if (!snapshot.hasCollapsedSelection) return null
        return macros.asSequence().filter { it.mode == MacroMode.MANUAL }.mapNotNull { macro ->
            matchAt(macro, snapshot.text, snapshot.selectionEnd)?.let { range ->
                ManualMacroMatch(macro, range, snapshot.text.substring(range.first, range.last + 1))
            }
        }.firstOrNull()
    }

    private fun matchAt(macro: MacroDefinition, text: String, end: Int): IntRange? {
        val start = end - macro.key.length
        if (start < 0 || end > text.length) return null
        val actual = text.substring(start, end)
        val equal = if (macro.caseSensitive) actual == macro.key else MacroValidation.fold(actual) == MacroValidation.fold(macro.key)
        if (!equal || !isWordBoundary(text, start)) return null
        return start until end
    }

    private fun isWordBoundary(text: String, offset: Int): Boolean {
        if (offset == 0) return true
        val cp = text.codePointBefore(offset)
        return Character.isWhitespace(cp) || Character.getType(cp) in punctuationTypes
    }

    fun isSeparator(value: String): Boolean = value.isNotEmpty() && value.codePoints().allMatch { cp ->
        Character.isWhitespace(cp) || Character.getType(cp) in punctuationTypes
    }

    private val punctuationTypes = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
    )
}
