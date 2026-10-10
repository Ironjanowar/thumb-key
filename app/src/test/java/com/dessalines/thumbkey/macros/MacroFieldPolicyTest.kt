package com.dessalines.thumbkey.macros

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroFieldPolicyTest {
    @Test fun `password variants type null and own fields are blocked`() {
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            EditorInfo.TYPE_NULL,
        ).forEach { assertFalse(MacroFieldPolicy.isAllowed(it, false)) }
        assertFalse(MacroFieldPolicy.isAllowed(InputType.TYPE_CLASS_TEXT, true))
    }

    @Test fun `ordinary text and multiline flags are allowed`() {
        assertTrue(MacroFieldPolicy.isAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, false))
    }
}
