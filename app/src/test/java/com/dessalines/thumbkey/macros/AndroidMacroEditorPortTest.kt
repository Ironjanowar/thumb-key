package com.dessalines.thumbkey.macros

import android.text.InputType
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class AndroidMacroEditorPortTest {
    @Test fun `controller snapshot before InputConnection commit blocks replacement of selection`() {
        val fake = FakeConnection("selected", 0).apply { selectionEnd = text.length }
        val controller = MacroController(AndroidMacroEditorPort({ fake.proxy }, { InputType.TYPE_CLASS_TEXT }, { false }))
        controller.updateDefinitions(listOf(MacroDefinition(1, "JJ", listOf("expanded"), false, MacroMode.IMMEDIATE)))

        val mayProcessMacros = controller.beforeDirectTextCommit()
        assertTrue(fake.proxy.commitText("JJ", 1))
        assertFalse(controller.onDirectTextCommitted("J", mayProcessMacros))

        assertEquals("JJ", fake.text)
        assertFalse(controller.state.value.canUndo)
    }

    @Test fun `complete snapshots replace only a revalidated range`() {
        val fake = FakeConnection("left JJ right", 7)
        val port = AndroidMacroEditorPort({ fake.proxy }, { InputType.TYPE_CLASS_TEXT }, { false })
        val snapshot = port.snapshot()!!
        val result = port.replace(snapshot, 5 until 7, "expanded")
        assertTrue(result is MacroEditResult.Success)
        assertEquals("left expanded right", fake.text)
    }

    @Test fun `partial invalid selected and composing snapshots fail closed`() {
        val fake = FakeConnection("JJ", 2)
        val port = AndroidMacroEditorPort({ fake.proxy }, { InputType.TYPE_CLASS_TEXT }, { false })
        assertNull(AndroidMacroEditorPort({ fake.proxy }, { InputType.TYPE_CLASS_TEXT }, { false }, { true }).snapshot())
        fake.startOffset = 1
        assertNull(port.snapshot())
        fake.startOffset = 0
        fake.partialStartOffset = 0
        fake.partialEndOffset = 1
        assertNull(port.snapshot())
        fake.partialStartOffset = -1
        fake.partialEndOffset = -1
        fake.selectionStart = 0
        assertNull(port.snapshot())
        assertNull(AndroidMacroEditorPort({ null }, { InputType.TYPE_CLASS_TEXT }, { false }).snapshot())
    }

    @Test fun `failed selection never commits and changed snapshots are rejected`() {
        val fake = FakeConnection("JJ", 2)
        val port = AndroidMacroEditorPort({ fake.proxy }, { InputType.TYPE_CLASS_TEXT }, { false })
        val expected = port.snapshot()!!
        fake.failSelection = true
        assertEquals(MacroEditResult.Failure, port.replace(expected, 0 until 2, "x"))
        assertEquals(0, fake.commitCalls)
        fake.failSelection = false
        fake.text = "changed"
        fake.selectionStart = 7
        fake.selectionEnd = 7
        assertEquals(MacroEditResult.Failure, port.replace(expected, 0 until 2, "x"))
    }

    @Test fun `password and own fields never request editor text`() {
        val fake = FakeConnection("secret", 6)
        assertNull(
            AndroidMacroEditorPort({
                fake.proxy
            }, { InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }, { false }).snapshot(),
        )
        assertNull(AndroidMacroEditorPort({ fake.proxy }, { InputType.TYPE_CLASS_TEXT }, { true }).snapshot())
        assertEquals(0, fake.snapshotCalls)
    }

    private class FakeConnection(
        var text: String,
        cursor: Int,
    ) {
        var selectionStart = cursor
        var selectionEnd = cursor
        var startOffset = 0
        var partialStartOffset = -1
        var partialEndOffset = -1
        var failSelection = false
        var commitCalls = 0
        var snapshotCalls = 0
        val proxy: InputConnection =
            Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, args ->
                when (method.name) {
                    "getExtractedText" -> {
                        snapshotCalls++
                        ExtractedText().apply {
                            text = this@FakeConnection.text
                            startOffset = this@FakeConnection.startOffset
                            partialStartOffset = this@FakeConnection.partialStartOffset
                            partialEndOffset = this@FakeConnection.partialEndOffset
                            selectionStart = this@FakeConnection.selectionStart
                            selectionEnd = this@FakeConnection.selectionEnd
                        }
                    }

                    "beginBatchEdit", "endBatchEdit" -> {
                        true
                    }

                    "setSelection" -> {
                        if (failSelection) {
                            false
                        } else {
                            true.also {
                                selectionStart = args!![0] as Int
                                selectionEnd = args[1] as Int
                            }
                        }
                    }

                    "commitText" -> {
                        commitCalls++
                        val value = args!![0].toString()
                        text = text.replaceRange(selectionStart, selectionEnd, value)
                        selectionStart += value.length
                        selectionEnd = selectionStart
                        true
                    }

                    else -> {
                        when (method.returnType) {
                            Boolean::class.javaPrimitiveType -> false
                            Int::class.javaPrimitiveType -> 0
                            else -> null
                        }
                    }
                }
            } as InputConnection
    }
}
