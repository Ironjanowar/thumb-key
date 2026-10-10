package com.dessalines.thumbkey.ui.components.settings.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.dessalines.thumbkey.utils.SimpleTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroSettingsScreen(
    navController: NavController,
    viewModel: MacroSettingsViewModel,
) {
    val macros by viewModel.macros.collectAsState()
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.macro_delete_confirm_title)) },
            text = { Text(stringResource(R.string.macro_delete_confirm_text)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { Button(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Scaffold(
        topBar = { SimpleTopAppBar(text = stringResource(R.string.macros), navController = navController) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("macroEditor/0") }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.macro_add))
            }
        },
    ) { padding ->
        if (macros.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.macros_empty))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(macros, key = { it.id }) { macro ->
                    Card(
                        onClick = { navController.navigate("macroEditor/${macro.id}") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(macro.key)
                                Text(macro.expansions.joinToString(" · "))
                            }
                            Button(onClick = { pendingDelete = macro.id }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
}
