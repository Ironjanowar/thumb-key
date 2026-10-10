package com.dessalines.thumbkey.macros

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MacroUiState(
    val candidates: List<String> = emptyList(),
    val canUndo: Boolean = false,
    val undoLabel: String? = null,
)

class MacroController(
    private val port: MacroEditorPort,
) {
    private var definitions: List<MacroDefinition> = emptyList()
    private var manual: PendingManual? = null
    private var undo: UndoRecord? = null
    private val mutableState = MutableStateFlow(MacroUiState())
    val state: StateFlow<MacroUiState> = mutableState.asStateFlow()

    fun startSession() = clear()

    fun finishSession() = clear()

    fun updateDefinitions(value: List<MacroDefinition>) {
        definitions = value
        clear()
    }

    fun beforeDirectTextCommit(): Boolean {
        val hadCollapsedSelection = port.snapshot()?.hasCollapsedSelection == true
        clear()
        return hadCollapsedSelection
    }

    fun onDirectTextCommitted(
        text: String,
        mayProcessMacros: Boolean = true,
    ): Boolean {
        undo = null
        if (!mayProcessMacros) return publishCandidates(null)
        val snapshot = port.snapshot() ?: return publishCandidates(null)
        val automatic = MacroMatcher.matchAutomatic(definitions, snapshot, text)
        if (automatic != null) {
            val editRange = automatic.keyRange.first until (automatic.keyRange.last + 1 + automatic.separator.length)
            val inserted = automatic.expansion + automatic.separator
            val result = port.replace(snapshot, editRange, inserted)
            if (result is MacroEditResult.Success) {
                val expansionRange = automatic.keyRange.first until (automatic.keyRange.first + inserted.length)
                undo = UndoRecord(result.snapshot, expansionRange, automatic.originalKey + automatic.separator)
                manual = null
                publish()
                return true
            }
            clear()
            return false
        }
        return publishCandidates(MacroMatcher.manualCandidates(definitions, snapshot), snapshot)
    }

    fun acceptCandidate(index: Int): Boolean {
        val pending = manual ?: return false
        val expansion = pending.match.expansions.getOrNull(index) ?: return false
        val result = port.replace(pending.snapshot, pending.match.keyRange, expansion)
        clear()
        return result is MacroEditResult.Success
    }

    fun undo(): Boolean {
        val record = undo ?: return false
        val result = port.replace(record.expected, record.expansionRange, record.originalKey)
        clear()
        return result is MacroEditResult.Success
    }

    fun beforeExplicitEdit() = clear()

    /** A selection callback arrived; keep only what the confirmed snapshot still backs. */
    fun onSelectionChanged() = revalidate()

    /** The app's field content changed without a typing commit; drop anything stale. */
    fun onContentChanged() = revalidate()

    /**
     * Revalidates the pending manual candidate and the undo record against the confirmed snapshot,
     * hiding a candidate as soon as it no longer matches. The fail-closed check in
     * [MacroEditorPort.replace] still guards the actual mutation.
     */
    private fun revalidate() {
        if (manual == null && undo == null) return
        val current = port.snapshot()
        if (undo != null && current == undo?.expected) return
        if (manual != null && current == manual?.snapshot) return
        clear()
    }

    private fun publishCandidates(
        match: ManualMacroMatch?,
        snapshot: MacroSnapshot? = null,
    ): Boolean {
        manual = if (match != null && snapshot != null) PendingManual(snapshot, match) else null
        publish()
        return false
    }

    private fun publish() {
        mutableState.value = MacroUiState(manual?.match?.expansions.orEmpty(), undo != null, if (undo != null) "undo" else null)
    }

    private fun clear() {
        manual = null
        undo = null
        publish()
    }

    private data class PendingManual(
        val snapshot: MacroSnapshot,
        val match: ManualMacroMatch,
    )

    private data class UndoRecord(
        val expected: MacroSnapshot,
        val expansionRange: IntRange,
        val originalKey: String,
    )
}
