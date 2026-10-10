package com.dessalines.thumbkey.macros

import androidx.annotation.StringRes
import com.dessalines.thumbkey.R

/**
 * Maps each validation error to the message shown beside the control that caused it. Deliberately
 * free of Android framework calls so the mapping can be asserted in plain JVM unit tests.
 */
object MacroValidationMessages {
    @StringRes
    fun messageResource(error: MacroValidationError): Int =
        when (error) {
            MacroValidationError.INVALID_KEY -> R.string.macro_error_invalid_key
            MacroValidationError.KEY_CONFLICT -> R.string.macro_error_key_conflict
            MacroValidationError.EMPTY_EXPANSIONS -> R.string.macro_error_empty_expansion
            MacroValidationError.EMPTY_EXPANSION -> R.string.macro_error_empty_expansion
            MacroValidationError.MULTIPLE_AUTOMATIC_EXPANSIONS -> R.string.macro_error_multiple_automatic
        }

    /**
     * Messages for the errors that belong to the given [control], in a stable order. Kept here so
     * the screen only decides which control to render beside, not which text an error produces.
     */
    @StringRes
    fun messagesFor(
        control: MacroEditorControl,
        errors: Set<MacroValidationError>,
    ): List<Int> =
        control.errorTypes
            .filter { it in errors }
            .map(::messageResource)
            .distinct()

    enum class MacroEditorControl(
        val errorTypes: List<MacroValidationError>,
    ) {
        KEY(
            listOf(MacroValidationError.INVALID_KEY, MacroValidationError.KEY_CONFLICT),
        ),
        EXPANSIONS(
            listOf(MacroValidationError.EMPTY_EXPANSIONS, MacroValidationError.EMPTY_EXPANSION),
        ),
        MODE(
            listOf(MacroValidationError.MULTIPLE_AUTOMATIC_EXPANSIONS),
        ),
    }
}
