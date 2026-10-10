package com.dessalines.thumbkey.macros

import android.text.InputType
import android.view.inputmethod.EditorInfo

object MacroFieldPolicy {
    fun isAllowed(
        inputType: Int,
        typingIntoOwnField: Boolean,
    ): Boolean {
        if (typingIntoOwnField || inputType == EditorInfo.TYPE_NULL) return false
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (inputClass == InputType.TYPE_CLASS_TEXT && variation in textPasswordVariations) return false
        if (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return false
        return inputClass == InputType.TYPE_CLASS_TEXT
    }

    private val textPasswordVariations =
        setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
}
