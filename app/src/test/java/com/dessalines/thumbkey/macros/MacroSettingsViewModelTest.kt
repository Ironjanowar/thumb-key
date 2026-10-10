package com.dessalines.thumbkey.macros

import com.dessalines.thumbkey.ui.components.settings.macros.MacroEditorDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroSettingsViewModelTest {
    @Test fun `adding a second expansion immediately forces manual`() {
        val draft =
            MacroEditorDraft(key = "x", expansions = listOf("one"), mode = MacroMode.IMMEDIATE)
                .addExpansion()
        assertEquals(MacroMode.MANUAL, draft.mode)
        assertEquals(2, draft.expansions.size)
        assertTrue(draft.automaticDisabled)
    }

    @Test fun `removing back to one expansion does not reactivate automatic`() {
        val draft =
            MacroEditorDraft(key = "x", expansions = listOf("one", "two"), mode = MacroMode.MANUAL)
                .removeExpansion(1)
        assertEquals(MacroMode.MANUAL, draft.mode)
        assertFalse(draft.automaticDisabled)
    }

    @Test fun `draft preserves literal expansion and maps to domain only on request`() {
        val draft = MacroEditorDraft(key = "café", expansions = listOf("  hi\n🙂  "), caseSensitive = true)
        assertEquals("  hi\n🙂  ", draft.toDefinition().expansions.single())
    }
}
