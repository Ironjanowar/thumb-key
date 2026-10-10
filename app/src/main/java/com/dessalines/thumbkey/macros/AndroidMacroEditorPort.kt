package com.dessalines.thumbkey.macros

import android.text.Spanned
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

class AndroidMacroEditorPort(
    private val connection: () -> InputConnection?,
    private val inputType: () -> Int,
    private val typingIntoOwnField: () -> Boolean,
    private val compositionActive: () -> Boolean = { false },
) : MacroEditorPort {
    override fun snapshot(): MacroSnapshot? {
        if (compositionActive() || !MacroFieldPolicy.isAllowed(inputType(), typingIntoOwnField())) return null
        val editor = connection() ?: return null
        val extracted = editor.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.startOffset != 0 || extracted.partialStartOffset != -1 || extracted.partialEndOffset != -1) return null
        val value = extracted.text ?: return null
        if (value is Spanned &&
            value.getSpans(0, value.length, Any::class.java).any { value.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0 }
        ) {
            return null
        }
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        if (start !in 0..value.length || end !in 0..value.length || start != end) return null
        return MacroSnapshot(value.toString(), start, end)
    }

    override fun replace(
        expected: MacroSnapshot,
        range: IntRange,
        replacement: String,
    ): MacroEditResult {
        val editor = connection() ?: return MacroEditResult.Failure
        if (snapshot() != expected || !validRange(expected.text, range)) return MacroEditResult.Failure
        if (!editor.beginBatchEdit()) return MacroEditResult.Failure
        var success = false
        try {
            if (!editor.setSelection(range.first, range.last + 1)) return MacroEditResult.Failure
            if (!editor.commitText(replacement, 1)) return MacroEditResult.Failure
            success = true
        } finally {
            if (!editor.endBatchEdit()) success = false
        }
        if (!success) return MacroEditResult.Failure
        val expectedText = expected.text.replaceRange(range.first, range.last + 1, replacement)
        val confirmed = snapshot() ?: return MacroEditResult.Failure
        val expectedAfter = MacroSnapshot(expectedText, range.first + replacement.length, range.first + replacement.length)
        return if (confirmed == expectedAfter) MacroEditResult.Success(confirmed) else MacroEditResult.Failure
    }

    private fun validRange(
        text: String,
        range: IntRange,
    ): Boolean {
        if (range.isEmpty() || range.first < 0 || range.last >= text.length) return false
        val end = range.last + 1
        return !splitsSurrogate(text, range.first) && !splitsSurrogate(text, end)
    }

    private fun splitsSurrogate(
        text: String,
        offset: Int,
    ): Boolean = offset in 1 until text.length && Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])
}
