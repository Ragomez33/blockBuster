package com.blockbuster.core.overlay

import android.os.CountDownTimer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockbuster.core.common.contract.FrictionConfig
import kotlinx.coroutines.delay

/**
 * Full-screen friction UI. It intentionally has no close action: the only exit
 * is completing the 60-second wait and transcribing the exact required text.
 */
@Composable
internal fun FrictionScreen(
    session: FrictionSession,
    onInputChanged: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    BackHandler(enabled = true) {}

    var fieldValue by remember(session.target) {
        mutableStateOf(
            TextFieldValue(
                text = session.input,
                selection = TextRange(session.input.length),
            ),
        )
    }
    var warningVisible by rememberSaveable(session.target) { mutableStateOf(false) }
    var remainingMillis by remember(session.startedAtElapsedRealtime) {
        mutableLongStateOf(session.remainingMillis())
    }

    LaunchedEffect(warningVisible) {
        if (warningVisible) {
            delay(FrictionSession.WARNING_DURATION_MILLIS)
            warningVisible = false
        }
    }

    DisposableEffect(session.startedAtElapsedRealtime) {
        val timer = object : CountDownTimer(
            remainingMillis.coerceAtLeast(0L),
            TIMER_TICK_MILLIS,
        ) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
            }

            override fun onFinish() {
                remainingMillis = 0L
            }
        }
        timer.start()
        onDispose { timer.cancel() }
    }

    val canSubmit = remainingMillis == 0L && fieldValue.text == session.requiredText
    val remainingSeconds = ((remainingMillis + 999L) / 1_000L).toInt()

    CompositionLocalProvider(LocalTextToolbar provides NoOpTextToolbar) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B0B0D),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.overlay_friction_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.friction_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC9C9D1),
                    )
                    Text(
                        text = formatRemaining(remainingSeconds),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.friction_timer_remaining),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFC9C9D1),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.friction_required_text_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE6E6EA),
                    )
                    Text(
                        text = session.requiredText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD7D7DE),
                    )
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { newValue ->
                            val newText = newValue.text
                            if (FrictionInput.isPaste(fieldValue.text, newText)) {
                                fieldValue = TextFieldValue("", TextRange.Zero)
                                onInputChanged("")
                                warningVisible = true
                            } else {
                                fieldValue = newValue.copy(selection = TextRange(newText.length))
                                onInputChanged(newText)
                            }
                        },
                        label = { Text(text = stringResource(R.string.friction_input_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        maxLines = 12,
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                    if (warningVisible) {
                        Text(
                            text = stringResource(R.string.friction_paste_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = onUnlock,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.friction_unlock))
                    }
                    if (!canSubmit) {
                        Text(
                            text = stringResource(R.string.friction_wait_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9B9BA5),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProtectionBlockingScreen(config: FrictionConfig) {
    BackHandler(enabled = true) {}

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B0B0D),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.protection_overlay_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Text(
                    text = config.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFD7D7DE),
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.friction_wait_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9B9BA5),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

private fun formatRemaining(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private object NoOpTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}

private const val TIMER_TICK_MILLIS = 250L
