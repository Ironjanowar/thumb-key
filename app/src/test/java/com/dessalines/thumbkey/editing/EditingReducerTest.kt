package com.dessalines.thumbkey.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditingReducerTest {
    @Test
    fun reducerPreservesAnchorAcrossCrossingAndZeroLengthSelections() {
        var state = EditingReducer.reduce(MarkState.Inactive, EditingEvent.Activate(anchor = 4, head = 6))
        state = EditingReducer.reduce(state, EditingEvent.MoveHead(4))
        assertTrue(state.active)
        assertEquals(MarkState(4, 4), state)

        state = EditingReducer.reduce(state, EditingEvent.MoveHead(2))
        assertEquals(MarkState(4, 2), state)
    }

    @Test
    fun reducerSwapsEndpointsAndDisarmsWithoutMovingTheHead() {
        val state = MarkState(anchor = 2, head = 7)
        assertEquals(MarkState(7, 2), EditingReducer.reduce(state, EditingEvent.SwapEnds))
        assertEquals(MarkState.Inactive, EditingReducer.reduce(state, EditingEvent.Disarm))
    }
}
