package com.dessalines.thumbkey.ui.components.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.macros.MacroUiState

@Composable
fun MacroBar(
    state: MacroUiState,
    onCandidate: (Int) -> Unit,
    onUndo: () -> Unit,
) {
    if (!state.canUndo && state.candidates.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(
                    max = 56.dp,
                ).horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canUndo) {
                Button(onClick = onUndo) { Text(stringResource(R.string.macro_undo)) }
            } else {
                state.candidates.forEachIndexed { index, text ->
                    Button(onClick = { onCandidate(index) }) {
                        Text(text.replace('\n', ' '), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
