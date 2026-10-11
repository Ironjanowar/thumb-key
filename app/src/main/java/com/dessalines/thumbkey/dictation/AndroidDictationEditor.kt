package com.dessalines.thumbkey.dictation

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

object DictationFieldPolicy {
    fun allows(info: EditorInfo?): Boolean {
        if (info == null || info.inputType == EditorInfo.TYPE_NULL) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
}

class AndroidDictationEditor(
    private val connection: () -> InputConnection?,
    private val fieldToken: () -> String,
    private val selection: () -> Pair<Int, Int>,
    private val beforeCommit: () -> Unit,
) : DictationEditor {
    fun target(): DictationTarget {
        val (start, end) = selection()
        return DictationTarget(fieldToken(), start, end)
    }

    override fun isCurrent(target: DictationTarget): Boolean {
        if (target.fieldToken != fieldToken()) return false
        val snapshot = connection()?.getExtractedText(ExtractedTextRequest(), 0)
        val complete =
            snapshot != null &&
                snapshot.startOffset == 0 &&
                snapshot.partialStartOffset < 0 &&
                snapshot.partialEndOffset < 0
        if (complete) {
            return snapshot.selectionStart == target.selectionStart && snapshot.selectionEnd == target.selectionEnd
        }
        // A non-collapsed destination must be confirmed from an authoritative snapshot. The
        // callback cache alone cannot prove that the same selected range still exists.
        if (target.selectionStart != target.selectionEnd) return false
        return selection() == (target.selectionStart to target.selectionEnd)
    }

    override fun commit(
        target: DictationTarget,
        text: String,
    ): Boolean {
        if (!isCurrent(target)) return false
        // Finishing composition can itself move the selection. Validate again afterwards so the
        // range we checked is exactly the range commitText will replace.
        beforeCommit()
        if (!isCurrent(target)) return false
        return connection()?.commitText(text, 1) == true
    }
}
