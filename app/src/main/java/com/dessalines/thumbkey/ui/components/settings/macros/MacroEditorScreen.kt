package com.dessalines.thumbkey.ui.components.settings.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.macros.MacroMode
import com.dessalines.thumbkey.macros.MacroValidation
import com.dessalines.thumbkey.utils.SimpleTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    id: Long,
    navController: NavController,
    viewModel: MacroSettingsViewModel,
) {
    val macros by viewModel.macros.collectAsState()
    val existing = macros.firstOrNull { it.id == id }
    var draft by remember(id, existing) { mutableStateOf(MacroEditorDraft.from(existing)) }
    var error by remember { mutableStateOf<String?>(null) }
    val validation = MacroValidation.validate(draft.toDefinition(), macros)

    Scaffold(topBar = {
        SimpleTopAppBar(
            text =
                stringResource(
                    if (id ==
                        0L
                    ) {
                        R.string.macro_add
                    } else {
                        R.string.macro_edit
                    },
                ),
            navController = navController,
        )
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.key,
                onValueChange = {
                    draft = draft.copy(key = it)
                    error = null
                },
                label = { Text(stringResource(R.string.macro_key)) },
                isError = validation.errors.isNotEmpty() && draft.key.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            draft.expansions.forEachIndexed { index, expansion ->
                OutlinedTextField(
                    value = expansion,
                    onValueChange = { value ->
                        draft = draft.copy(expansions = draft.expansions.toMutableList().also { it[index] = value })
                    },
                    label = { Text(stringResource(R.string.macro_expansion_number, index + 1)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.expansions.size >
                    1
                ) {
                    Button(onClick = { draft = draft.removeExpansion(index) }) { Text(stringResource(R.string.remove)) }
                }
            }
            Button(onClick = { draft = draft.addExpansion() }) { Text(stringResource(R.string.macro_add_expansion)) }
            if (draft.automaticDisabled) Text(stringResource(R.string.macro_multiple_manual_explanation))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroMode.entries.forEach { mode ->
                    FilterChip(
                        selected = draft.mode == mode,
                        enabled = !(draft.automaticDisabled && mode != MacroMode.MANUAL),
                        onClick = { draft = draft.copy(mode = mode) },
                        label = { Text(stringResource(mode.labelResource())) },
                    )
                }
            }
            Row {
                Checkbox(checked = draft.caseSensitive, onCheckedChange = { draft = draft.copy(caseSensitive = it) })
                Text(stringResource(R.string.macro_case_sensitive), Modifier.padding(top = 12.dp))
            }
            if (validation.warnings.isNotEmpty()) Text(stringResource(R.string.macro_prefix_warning))
            if (validation.errors.isNotEmpty()) Text(stringResource(R.string.macro_validation_error))
            error?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = validation.isValid,
                    onClick = { viewModel.save(draft, { navController.popBackStack() }, { error = it }) },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

private fun MacroMode.labelResource(): Int =
    when (this) {
        MacroMode.MANUAL -> R.string.macro_mode_manual
        MacroMode.IMMEDIATE -> R.string.macro_mode_immediate
        MacroMode.ON_SEPARATOR -> R.string.macro_mode_separator
    }
