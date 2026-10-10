package com.dessalines.thumbkey.ui.components.settings.macros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dessalines.thumbkey.db.MacroRepository
import com.dessalines.thumbkey.macros.MacroDefinition
import com.dessalines.thumbkey.macros.MacroMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MacroEditorDraft(
    val id: Long = 0,
    val key: String = "",
    val expansions: List<String> = listOf(""),
    val caseSensitive: Boolean = false,
    val mode: MacroMode = MacroMode.MANUAL,
) {
    val automaticDisabled: Boolean get() = expansions.size > 1

    fun addExpansion(): MacroEditorDraft = copy(expansions = expansions + "", mode = MacroMode.MANUAL)

    fun removeExpansion(index: Int): MacroEditorDraft =
        copy(
            expansions =
                expansions.filterIndexed { i, _ -> i != index }.ifEmpty {
                    listOf("")
                },
        )

    fun toDefinition(): MacroDefinition = MacroDefinition(id, key, expansions, caseSensitive, mode)

    companion object {
        fun from(macro: MacroDefinition?): MacroEditorDraft =
            macro?.let {
                MacroEditorDraft(it.id, it.key, it.expansions, it.caseSensitive, it.mode)
            } ?: MacroEditorDraft()
    }
}

class MacroSettingsViewModel(
    private val repository: MacroRepository,
) : ViewModel() {
    val macros: StateFlow<List<MacroDefinition>> =
        repository.macros.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    fun save(
        draft: MacroEditorDraft,
        onSaved: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.save(draft.toDefinition()) }.onSuccess { onSaved() }.onFailure { onError(it.message.orEmpty()) }
        }
    }

    fun delete(
        id: Long,
        onDeleted: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }
}

class MacroSettingsViewModelFactory(
    private val repository: MacroRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MacroSettingsViewModel(repository) as T
}
