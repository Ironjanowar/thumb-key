package com.dessalines.thumbkey.macros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroControllerTest {
    private val macro = MacroDefinition(1, "JJ", listOf("jajajajaja"), false, MacroMode.IMMEDIATE)

    @Test fun `confirmed automatic expansion creates exact undo and preserves separator`() {
        val port = FakePort("JJ ", 3)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(macro.copy(mode = MacroMode.ON_SEPARATOR)))

        assertTrue(controller.onDirectTextCommitted(" "))
        assertEquals("jajajajaja ", port.text)
        assertEquals(port.text.length, port.cursor)
        assertTrue(controller.state.value.canUndo)
        assertTrue(controller.undo())
        assertEquals("JJ ", port.text)
        assertFalse(controller.state.value.canUndo)
    }

    @Test fun `undo restores original case and never recursively expands`() {
        val port = FakePort("Jj", 2)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(macro))
        assertTrue(controller.onDirectTextCommitted("j"))
        assertTrue(controller.undo())
        assertEquals("Jj", port.text)
    }

    @Test fun `direct typing that replaces a selection cannot expand or offer candidates`() {
        val manual = MacroDefinition(2, "saludo", listOf("Hola", "Buenas"))
        listOf(macro, manual).forEach { definition ->
            val port = FakePort("selected", 0).apply { selectionEnd = text.length }
            val controller = MacroController(port)
            controller.updateDefinitions(listOf(definition))

            val mayProcessMacros = controller.beforeDirectTextCommit()
            port.text = definition.key
            port.cursor = definition.key.length
            port.selectionEnd = port.cursor
            controller.onDirectTextCommitted(definition.key.takeLast(1), mayProcessMacros)

            assertEquals(definition.key, port.text)
            assertTrue(
                controller.state.value.candidates
                    .isEmpty(),
            )
            assertFalse(controller.state.value.canUndo)
        }
    }

    @Test fun `manual candidate revalidates fresh snapshot before replacing`() {
        val manual = MacroDefinition(2, "saludo", listOf("Hola", "Buenas"))
        val port = FakePort("saludo", 6)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(manual))
        controller.onDirectTextCommitted("o")
        assertEquals(listOf("Hola", "Buenas"), controller.state.value.candidates)
        port.text = "externo"
        port.cursor = 7
        assertFalse(controller.acceptCandidate(0))
        assertEquals("externo", port.text)
    }

    @Test fun `write delete movement field and configuration changes invalidate undo`() {
        listOf<(MacroController) -> Unit>(
            { it.beforeExplicitEdit() },
            { it.finishSession() },
            { it.updateDefinitions(emptyList()) },
        ).forEach { invalidate ->
            val port = FakePort("JJ", 2)
            val controller = MacroController(port)
            controller.updateDefinitions(listOf(macro))
            controller.onDirectTextCommitted("J")
            invalidate(controller)
            assertFalse(controller.state.value.canUndo)
        }
    }

    @Test fun `duplicate callbacks preserve undo but a fresh external mutation invalidates it`() {
        val port = FakePort("JJ", 2)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(macro))
        controller.onDirectTextCommitted("J")
        controller.onSelectionChanged()
        assertTrue(controller.state.value.canUndo)
        port.text += "x"
        port.cursor++
        controller.onSelectionChanged()
        assertFalse(controller.state.value.canUndo)
    }

    @Test fun `selection callbacks never create candidates for pasted or repositioned text`() {
        val manual = MacroDefinition(2, "saludo", listOf("Hola", "Buenas"))
        val port = FakePort("saludo", 6)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(manual))

        controller.onSelectionChanged()

        assertTrue(
            controller.state.value.candidates
                .isEmpty(),
        )
    }

    @Test fun `matching duplicate callback preserves manual candidates`() {
        val manual = MacroDefinition(2, "saludo", listOf("Hola", "Buenas"))
        val port = FakePort("saludo", 6)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(manual))
        controller.onDirectTextCommitted("o")

        controller.onSelectionChanged()

        assertEquals(listOf("Hola", "Buenas"), controller.state.value.candidates)
    }

    @Test fun `a content change by another path hides a stale manual candidate`() {
        val manual = MacroDefinition(2, "saludo", listOf("Hola", "Buenas"))
        val port = FakePort("saludo", 6)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(manual))
        controller.onDirectTextCommitted("o")
        assertEquals(listOf("Hola", "Buenas"), controller.state.value.candidates)

        // The app rewrote the field without a typing commit or a selection callback.
        port.text = "saludo!"
        port.cursor = 7
        port.selectionEnd = 7
        controller.onContentChanged()

        assertTrue(
            controller.state.value.candidates
                .isEmpty(),
        )
    }

    @Test fun `an unrelated content callback keeps a candidate that is still valid`() {
        val manual = MacroDefinition(2, "saludo", listOf("Hola", "Buenas"))
        val port = FakePort("saludo", 6)
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(manual))
        controller.onDirectTextCommitted("o")

        controller.onContentChanged()
        assertEquals(listOf("Hola", "Buenas"), controller.state.value.candidates)

        port.cursor = 5
        port.selectionEnd = 5
        controller.onContentChanged()
        assertTrue(
            controller.state.value.candidates
                .isEmpty(),
        )
    }

    @Test fun `failed or unconfirmed replacement never publishes undo`() {
        val port = FakePort("JJ", 2).apply { failReplacement = true }
        val controller = MacroController(port)
        controller.updateDefinitions(listOf(macro))
        assertFalse(controller.onDirectTextCommitted("J"))
        assertNull(controller.state.value.undoLabel)
    }

    private class FakePort(
        var text: String,
        var cursor: Int,
    ) : MacroEditorPort {
        var selectionEnd: Int = cursor
        var failReplacement = false

        override fun snapshot(): MacroSnapshot = MacroSnapshot(text, cursor, selectionEnd)

        override fun replace(
            expected: MacroSnapshot,
            range: IntRange,
            replacement: String,
        ): MacroEditResult {
            if (failReplacement || snapshot() != expected ||
                text.substring(range.first, range.last + 1) != expected.text.substring(range.first, range.last + 1)
            ) {
                return MacroEditResult.Failure
            }
            text = text.replaceRange(range.first, range.last + 1, replacement)
            cursor = range.first + replacement.length
            selectionEnd = cursor
            return MacroEditResult.Success(snapshot())
        }
    }
}
