package com.dessalines.thumbkey.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditingControllerTest {
    @Test
    fun markAdoptsExternalRangeMovesHeadAcrossAnchorAndSecondToggleCollapsesAtHead() {
        val port = FakePort("abcdef", 2, 5)
        val controller = EditingController(port)

        controller.perform(EditingCommand.ToggleMark)
        assertEquals(MarkState(anchor = 2, head = 5), controller.markState)

        controller.perform(EditingCommand.MoveCharacterBackward)
        controller.perform(EditingCommand.MoveCharacterBackward)
        controller.perform(EditingCommand.MoveCharacterBackward)
        controller.perform(EditingCommand.MoveCharacterBackward)
        assertEquals(MarkState(anchor = 2, head = 1), controller.markState)
        assertEquals(2 to 1, port.selections.last())

        controller.perform(EditingCommand.ToggleMark)
        assertFalse(controller.markState.active)
        assertEquals(1 to 1, port.selections.last())
    }

    @Test
    fun zeroLengthMarkRemainsActiveAndSwapChangesOnlyTheActiveEndpoint() {
        val port = FakePort("abcdef", 3, 3)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.MoveCharacterForward)
        controller.perform(EditingCommand.SwapEnds)

        assertEquals(MarkState(anchor = 4, head = 3), controller.markState)
        assertEquals(4 to 3, port.selections.last())

        controller.perform(EditingCommand.MoveCharacterForward)
        assertEquals(MarkState(anchor = 4, head = 4), controller.markState)
        assertTrue(controller.markState.active)
    }

    @Test
    fun inactiveMovementCollapsesExistingRangeWithoutTakingAnExtraStep() {
        val backward = FakePort("abcdef", 2, 5)
        EditingController(backward).perform(EditingCommand.MoveCharacterBackward)
        assertEquals(listOf(2 to 2), backward.selections)

        val forward = FakePort("abcdef", 2, 5)
        EditingController(forward).perform(EditingCommand.MoveWordForward)
        assertEquals(listOf(5 to 5), forward.selections)
    }

    @Test
    fun markedVisualMovementIsSafeNoOpWhileUnmarkedUsesDpad() {
        val port = FakePort("one\ntwo", 4, 4)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.MoveVisualUp)
        assertTrue(port.fallbacks.isEmpty())
        assertEquals(MarkState(4, 4), controller.markState)

        controller.disarm()
        controller.perform(EditingCommand.MoveVisualDown)
        assertEquals(listOf(EditingCommand.MoveVisualDown), port.fallbacks)
    }

    @Test
    fun unreliableEditorsRejectMarkButKeepPlainFallbackNavigationAndDeletion() {
        val port = FakePort(snapshotReliable = false)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.MoveCharacterBackward)
        controller.perform(EditingCommand.DeletePreviousWord)

        assertFalse(controller.markState.active)
        assertEquals(
            listOf(EditingCommand.MoveCharacterBackward, EditingCommand.DeletePreviousWord),
            port.fallbacks,
        )
    }

    @Test
    fun copyNeedsConfirmedRangeAndKeepsMarkWhileSuccessfulCutPasteAndDeleteDisarm() {
        val emptyPort = FakePort("abc", 1, 1)
        EditingController(emptyPort).perform(EditingCommand.Copy)
        assertEquals(0, emptyPort.copyCalls)

        val copyPort = FakePort("abc", 0, 2)
        val copyController = EditingController(copyPort)
        copyController.perform(EditingCommand.ToggleMark)
        copyController.perform(EditingCommand.Copy)
        assertEquals(1, copyPort.copyCalls)
        assertTrue(copyController.markState.active)

        listOf(EditingCommand.Cut, EditingCommand.Paste, EditingCommand.DeletePreviousGrapheme).forEach { command ->
            val port = FakePort("abc", 0, 2)
            val controller = EditingController(port)
            controller.perform(EditingCommand.ToggleMark)
            controller.perform(command)
            assertFalse("$command should disarm mark", controller.markState.active)
        }
    }

    @Test
    fun selectionDeletionDeletesOnlyTheSelectedRange() {
        val port = FakePort("a👍🏽b", 1, 5)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.DeleteNextGrapheme)

        assertEquals(1, port.deleteSelectionCalls)
        assertTrue(port.surroundingDeletes.isEmpty())
        assertFalse(controller.markState.active)
    }

    @Test
    fun graphemeAndWordDeletionUseSharedUnicodeBoundaries() {
        val graphemePort = FakePort("a👩‍👩‍👧‍👦b", 12, 12)
        EditingController(graphemePort).perform(EditingCommand.DeletePreviousGrapheme)
        assertEquals(listOf(11 to 0), graphemePort.surroundingDeletes)

        val wordPort = FakePort("café — fin", 7, 7)
        EditingController(wordPort).perform(EditingCommand.DeletePreviousWord)
        assertEquals(listOf(2 to 0), wordPort.surroundingDeletes)
    }

    @Test
    fun textAndLineLimitsUseConfirmedOffsets() {
        val port = FakePort("zero\none two\nthree", 8, 8)
        val controller = EditingController(port)
        controller.perform(EditingCommand.MoveLineStart)
        controller.perform(EditingCommand.MoveLineEnd)
        controller.perform(EditingCommand.MoveTextStart)
        controller.perform(EditingCommand.MoveTextEnd)

        assertEquals(listOf(5 to 5, 12 to 12, 0 to 0, 18 to 18), port.selections)

        val atBeginning = FakePort("\ntext", 0, 0)
        EditingController(atBeginning).perform(EditingCommand.MoveLineStart)
        assertEquals(listOf(0 to 0), atBeginning.selections)
    }

    @Test
    fun pendingSelectionAcknowledgementsAreMatchedIndividuallyAndExternalMovesDisarm() {
        val port = FakePort("abcdef", 2, 2)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.MoveCharacterForward)
        controller.perform(EditingCommand.MoveCharacterForward)

        controller.onSelectionUpdate(2, 3)
        assertTrue(controller.markState.active)
        controller.onSelectionUpdate(2, 4)
        assertTrue(controller.markState.active)
        controller.onSelectionUpdate(0, 0)
        assertFalse(controller.markState.active)
    }

    @Test
    fun sessionEndDisarmsWithoutChangingEditorSelection() {
        val port = FakePort("abc", 0, 2)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.finishSession()

        assertFalse(controller.markState.active)
        assertEquals(listOf<Pair<Int, Int>>(), port.selections)
    }

    @Test
    fun coalescedAcknowledgementDropsSupersededExpectations() {
        val port = FakePort("abcdef", 2, 2)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.MoveCharacterForward)
        controller.perform(EditingCommand.MoveCharacterForward)

        // The editor reports only the newest range, as it may coalesce callbacks
        controller.onSelectionUpdate(2, 4)
        assertTrue(controller.markState.active)

        // The superseded expectation must not swallow an external move to that old range
        controller.onSelectionUpdate(2, 3)
        assertFalse(controller.markState.active)
    }

    @Test
    fun missingMutationCallbackDoesNotHideExternalMoveAfterMarkIsRearmed() {
        val port = FakePort("abc", 0, 2)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.Cut)
        assertFalse(controller.markState.active)

        // The editor omits the cut callback, then the user arms a fresh mark.
        controller.perform(EditingCommand.ToggleMark)
        assertTrue(controller.markState.active)

        controller.onSelectionUpdate(3, 3)
        assertFalse(controller.markState.active)
    }

    @Test
    fun mutationCallbackBeforeNextCommandIsHarmless() {
        val port = FakePort("abc", 0, 2)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        controller.perform(EditingCommand.Cut)
        assertFalse(controller.markState.active)

        controller.onSelectionUpdate(0, 0)
        assertFalse(controller.markState.active)

        controller.perform(EditingCommand.ToggleMark)
        assertTrue(controller.markState.active)
        controller.onSelectionUpdate(3, 3)
        assertFalse(controller.markState.active)
    }

    @Test
    fun markOperationsFailClosedWhenTheEditorStopsBeingReliable() {
        val offPort = FakePort("abc", 1, 1)
        val offController = EditingController(offPort)
        offController.perform(EditingCommand.ToggleMark)
        offPort.corrupt()
        offController.perform(EditingCommand.ToggleMark)

        assertFalse(offController.markState.active)
        assertEquals(listOf<Pair<Int, Int>>(), offPort.selections)

        val swapPort = FakePort("abcdef", 2, 2)
        val swapController = EditingController(swapPort)
        swapController.perform(EditingCommand.ToggleMark)
        swapController.perform(EditingCommand.MoveCharacterForward)
        val before = swapController.markState
        val selectionsBefore = swapPort.selections.size
        swapPort.corrupt()
        swapController.perform(EditingCommand.SwapEnds)

        assertEquals(before, swapController.markState)
        assertEquals(selectionsBefore, swapPort.selections.size)
    }

    @Test
    fun swapEndsRejectsEndpointsOutsideTheConfirmedText() {
        val port = FakePort("ab", 0, 2)
        val controller = EditingController(port)
        controller.perform(EditingCommand.ToggleMark)
        port.shrink("a")
        controller.perform(EditingCommand.SwapEnds)

        assertEquals(MarkState(0, 2), controller.markState)
        assertEquals(listOf<Pair<Int, Int>>(), port.selections)
    }

    private class FakePort(
        text: String = "",
        selectionStart: Int = 0,
        selectionEnd: Int = selectionStart,
        private val snapshotReliable: Boolean = true,
    ) : EditingPort {
        private var current = EditorSnapshot(text, selectionStart, selectionEnd)
        val selections = mutableListOf<Pair<Int, Int>>()
        val fallbacks = mutableListOf<EditingCommand>()
        val surroundingDeletes = mutableListOf<Pair<Int, Int>>()
        var copyCalls = 0
        var cutCalls = 0
        var pasteCalls = 0
        var deleteSelectionCalls = 0

        override fun snapshot(): EditorSnapshot? = current.takeIf { snapshotReliable }

        fun corrupt() {
            current = current.copy(selectionStart = 0, selectionEnd = current.text.length + 5)
        }

        fun shrink(text: String) {
            current = EditorSnapshot(text, current.selectionStart, current.selectionEnd)
        }

        override fun setSelection(
            start: Int,
            end: Int,
        ): Boolean {
            selections += start to end
            current = current.copy(selectionStart = start, selectionEnd = end)
            return true
        }

        override fun fallback(command: EditingCommand): Boolean {
            fallbacks += command
            return true
        }

        override fun copy(): Boolean {
            copyCalls++
            return true
        }

        override fun cut(): Boolean {
            cutCalls++
            return true
        }

        override fun paste(): Boolean {
            pasteCalls++
            return true
        }

        override fun deleteSelection(): Boolean {
            deleteSelectionCalls++
            return true
        }

        override fun deleteSurrounding(
            before: Int,
            after: Int,
        ): Boolean {
            surroundingDeletes += before to after
            return true
        }
    }
}
