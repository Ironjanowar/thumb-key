package com.dessalines.thumbkey.editing

sealed interface EditingEvent {
    data class Activate(
        val anchor: Int,
        val head: Int,
    ) : EditingEvent

    data class MoveHead(
        val head: Int,
    ) : EditingEvent

    data object SwapEnds : EditingEvent

    data object Disarm : EditingEvent
}

object EditingReducer {
    fun reduce(
        state: MarkState,
        event: EditingEvent,
    ): MarkState =
        when (event) {
            is EditingEvent.Activate -> MarkState(event.anchor, event.head)
            is EditingEvent.MoveHead -> if (state.active) state.copy(head = event.head) else state
            EditingEvent.SwapEnds -> if (state.active) MarkState(state.head, state.anchor) else state
            EditingEvent.Disarm -> MarkState.Inactive
        }
}
