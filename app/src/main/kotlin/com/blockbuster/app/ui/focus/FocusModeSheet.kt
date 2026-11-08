package com.blockbuster.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockbuster.app.R
import com.blockbuster.core.common.model.FocusPhase
import com.blockbuster.core.common.model.FocusSession
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeSheet(
    onDismiss: () -> Unit,
    viewModel: FocusModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var workText by remember(uiState.session.workMinutes) {
        mutableStateOf(uiState.session.workMinutes.toString())
    }
    var breakText by remember(uiState.session.breakMinutes) {
        mutableStateOf(uiState.session.breakMinutes.toString())
    }
    val workMinutes = workText.toIntOrNull()
    val breakMinutes = breakText.toIntOrNull()
    val durationsValid = workMinutes != null &&
        breakMinutes != null &&
        workMinutes in FocusSession.MIN_WORK_MINUTES..FocusSession.MAX_WORK_MINUTES &&
        breakMinutes in FocusSession.MIN_BREAK_MINUTES..FocusSession.MAX_BREAK_MINUTES
    val editingDurations = !uiState.isRunning

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.focus_mode_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.session.phase == FocusPhase.WORK) {
                                R.string.focus_work
                            } else {
                                R.string.focus_break
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatRemaining(uiState.remainingMillis),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            R.string.focus_cycles,
                            uiState.session.completedWorkCycles,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = workText,
                    onValueChange = { workText = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text(text = stringResource(R.string.focus_work_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = editingDurations,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = breakText,
                    onValueChange = { breakText = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text(text = stringResource(R.string.focus_break_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = editingDurations,
                    singleLine = true,
                )
            }

            if (!durationsValid) {
                Text(
                    text = stringResource(R.string.focus_error_duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                !uiState.isRunning && !uiState.isPaused -> {
                    Button(
                        onClick = {
                            if (durationsValid && workMinutes != null && breakMinutes != null) {
                                viewModel.start(workMinutes, breakMinutes)
                            }
                        },
                        enabled = durationsValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.focus_start))
                    }
                }

                uiState.isRunning -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = viewModel::pause,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.focus_pause))
                        }
                        OutlinedButton(
                            onClick = viewModel::stop,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.focus_stop))
                        }
                    }
                }

                uiState.isPaused -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = viewModel::resume,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.focus_resume))
                        }
                        OutlinedButton(
                            onClick = viewModel::stop,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.focus_stop))
                        }
                    }
                }
            }

            uiState.actionError?.let { errorRes ->
                Text(
                    text = stringResource(errorRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatRemaining(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis + 999L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}
