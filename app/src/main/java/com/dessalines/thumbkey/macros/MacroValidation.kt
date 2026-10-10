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
        if (macro.key.isEmpty() || !macro.key.codePoints().allMatch { Character.isLetterOrDigit(it) || isMark(it) }) {
            errors += MacroValidationError.INVALID_KEY
        }
        if (macro.expansions.isEmpty()) errors += MacroValidationError.EMPTY_EXPANSIONS
        if (macro.expansions.any(String::isEmpty)) errors += MacroValidationError.EMPTY_EXPANSION
        if (macro.expansions.size > 1 && macro.mode != MacroMode.MANUAL) {
            errors += MacroValidationError.MULTIPLE_AUTOMATIC_EXPANSIONS
        }
        existing.filter { it.id != macro.id }.forEach { other ->
            if (domainsOverlap(macro, other)) errors += MacroValidationError.KEY_CONFLICT
            if (other.mode == MacroMode.IMMEDIATE && startsWith(macro.key, other.key, other.caseSensitive)) {
                warnings += MacroValidationWarning.IMMEDIATE_PREFIX
            }
        }
        return MacroValidationResult(errors, warnings)
    }

    private fun isMark(codePoint: Int): Boolean =
        Character.getType(codePoint) in
            setOf(
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
            )

    private fun domainsOverlap(
        a: MacroDefinition,
        b: MacroDefinition,
    ): Boolean = if (a.caseSensitive && b.caseSensitive) a.key == b.key else fold(a.key) == fold(b.key)

    private fun startsWith(
        value: String,
        prefix: String,
        sensitive: Boolean,
    ): Boolean = if (sensitive) value.startsWith(prefix) else fold(value).startsWith(fold(prefix))

    internal fun fold(value: String): String = value.lowercase(Locale.ROOT)
}
