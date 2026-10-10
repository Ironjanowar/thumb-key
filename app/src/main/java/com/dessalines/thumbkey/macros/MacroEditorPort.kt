package com.dessalines.thumbkey.macros

sealed interface MacroEditResult {
    data class Success(
        val snapshot: MacroSnapshot,
    ) : MacroEditResult

    data object Failure : MacroEditResult
}

interface MacroEditorPort {
    fun snapshot(): MacroSnapshot?

    fun replace(
        expected: MacroSnapshot,
        range: IntRange,
        replacement: String,
    ): MacroEditResult
}
