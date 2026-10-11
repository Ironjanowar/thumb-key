package com.dessalines.thumbkey.ui.components.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.dictation.DictationState

@Composable
fun DictationPanel(
    state: DictationState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            when (state) {
                is DictationState.Preparing -> stringResource(R.string.dictation_preparing)
                is DictationState.Recording -> stringResource(R.string.dictation_listening, state.elapsedSeconds)
                is DictationState.Transcribing -> stringResource(R.string.dictation_transcribing)
                is DictationState.Error -> stringResource(R.string.dictation_error, state.message)
                DictationState.Idle -> ""
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state is DictationState.Recording) Button(onClick = onStop) { Text(stringResource(R.string.dictation_stop)) }
            Button(onClick = onCancel) {
                Text(stringResource(if (state is DictationState.Error) R.string.dictation_return else R.string.dictation_cancel))
            }
        }
    }
}
