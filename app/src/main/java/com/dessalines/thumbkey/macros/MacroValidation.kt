package com.dessalines.thumbkey.macros

import java.util.Locale

enum class MacroValidationError { INVALID_KEY, EMPTY_EXPANSIONS, EMPTY_EXPANSION, MULTIPLE_AUTOMATIC_EXPANSIONS, KEY_CONFLICT }

enum class MacroValidationWarning { IMMEDIATE_PREFIX }

data class MacroValidationResult(
    val errors: Set<MacroValidationError> = emptySet(),
    val warnings: Set<MacroValidationWarning> = emptySet(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object MacroValidation {
    fun validate(
        macro: MacroDefinition,
        existing: List<MacroDefinition>,
    ): MacroValidationResult {
        val errors = mutableSetOf<MacroValidationError>()
        val warnings = mutableSetOf<MacroValidationWarning>()
        if (macro.key.isEmpty() || macro.key.codePoints().anyMatch { isSeparatorCodePoint(it) }) {
            errors += MacroValidationError.INVALID_KEY
        }
        if (macro.expansions.isEmpty()) errors += MacroValidationError.EMPTY_EXPANSIONS
        if (macro.expansions.any(String::isEmpty)) errors += MacroValidationError.EMPTY_EXPANSION
        if (macro.expansions.size > 1 && macro.mode != MacroMode.MANUAL) {
            errors += MacroValidationError.MULTIPLE_AUTOMATIC_EXPANSIONS
        }
        existing.filter { it.id != macro.id }.forEach { other ->
            if (domainsOverlap(macro, other)) errors += MacroValidationError.KEY_CONFLICT
            if (
                (other.mode == MacroMode.IMMEDIATE && startsWith(macro, other)) ||
                (macro.mode == MacroMode.IMMEDIATE && startsWith(other, macro))
            ) {
                warnings += MacroValidationWarning.IMMEDIATE_PREFIX
            }
        }
        return MacroValidationResult(errors, warnings)
    }

    /**
     * A key may not contain whitespace or a line break. Those code points are what end a word and
     * what trigger separator-mode macros, so a key built from or containing them could never be
     * reliably recognised: it is ambiguous with the macro separator. Punctuation inside a key is
     * fine and deliberately allowed.
     */
    private fun isSeparatorCodePoint(codePoint: Int): Boolean = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

    private fun domainsOverlap(
        a: MacroDefinition,
        b: MacroDefinition,
    ): Boolean = if (a.caseSensitive && b.caseSensitive) a.key == b.key else fold(a.key) == fold(b.key)

    private fun startsWith(
        value: MacroDefinition,
        prefix: MacroDefinition,
    ): Boolean =
        if (value.caseSensitive && prefix.caseSensitive) {
            value.key.startsWith(prefix.key)
        } else {
            fold(value.key).startsWith(fold(prefix.key))
        }

    internal fun fold(value: String): String =
        buildString {
            value.codePoints().forEachOrdered { codePoint ->
                when (codePoint) {
                    0x0131 -> {
                        // Dotless i is distinct in default Unicode folding.
                        appendCodePoint(codePoint)
                    }

                    0x00df, 0x1e9e -> {
                        append("ss")
                    }

                    else -> {
                        append(
                            String(Character.toChars(codePoint))
                                .uppercase(Locale.ROOT)
                                .lowercase(Locale.ROOT),
                        )
                    }
                }
            }
        }
}
