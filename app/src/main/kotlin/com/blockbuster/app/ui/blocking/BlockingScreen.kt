package com.blockbuster.app.ui.blocking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockbuster.app.R
import com.blockbuster.app.ui.premium.PremiumActionRow
import com.blockbuster.app.ui.premium.PremiumSwitchRow
import com.blockbuster.core.common.model.PremiumSettings
import com.blockbuster.core.common.model.ProtectionPreset

@Composable
fun BlockingScreen(
    onOpenSubscription: () -> Unit,
    viewModel: BlockingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showFrictionDialog by remember { mutableStateOf(false) }
    var showPartnerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionError) {
        val messageRes = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(messageRes))
        viewModel.consumeError()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

    val startMonitoring = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.startMonitoring()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.tab_blocking),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.dashboard_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ProtectionSummaryCard(
                uiState = uiState,
                onStartMonitoring = startMonitoring,
            )

            SectionTitle(stringResource(R.string.blocking_section_content))
            uiState.presets.forEach { presetState ->
                PresetSwitchCard(
                    state = presetState,
                    onEnabledChange = { enabled ->
                        viewModel.setPresetEnabled(presetState.preset, enabled)
                    },
                )
            }

            SectionTitle(stringResource(R.string.blocking_section_advanced))
            AdvancedBlockingCard(
                uiState = uiState,
                onOpenSubscription = onOpenSubscription,
                onStrictChange = viewModel::setStrictShortsReels,
                onEditPartner = { showPartnerDialog = true },
            )

            SectionTitle(stringResource(R.string.blocking_section_customize))
            CustomizeBlockedCard(
                uiState = uiState,
                onOpenSubscription = onOpenSubscription,
                onEditFriction = { showFrictionDialog = true },
            )
        }
    }

    if (showFrictionDialog && uiState.isPremium) {
        FrictionWindowDialog(
            currentMillis = uiState.frictionWindowMillis,
            onDismiss = { showFrictionDialog = false },
            onConfirm = { seconds ->
                viewModel.setFrictionWindowMillis(seconds * 1_000L)
                showFrictionDialog = false
            },
        )
    }

    if (showPartnerDialog && uiState.isPremium) {
        PartnerDialog(
            currentValue = uiState.accountabilityPartner.orEmpty(),
            onDismiss = { showPartnerDialog = false },
            onConfirm = { value ->
                viewModel.setAccountabilityPartner(value.ifBlank { null })
                showPartnerDialog = false
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ProtectionSummaryCard(
    uiState: BlockingUiState,
    onStartMonitoring: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_section_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.dashboard_active_protections,
                            uiState.activeProtectionCount,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StatusPill(
                    label = stringResource(
                        if (uiState.monitoringRunning) {
                            R.string.dashboard_monitoring_active
                        } else {
                            R.string.dashboard_monitoring_stopped
                        },
                    ),
                    active = uiState.monitoringRunning,
                )
            }
            Text(
                text = stringResource(
                    if (uiState.accessibilityConnected) {
                        R.string.dashboard_accessibility_connected
                    } else {
                        R.string.dashboard_accessibility_pending
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!uiState.monitoringRunning) {
                Button(
                    onClick = onStartMonitoring,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.dashboard_start_monitoring))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PresetSwitchCard(
    state: PresetSwitchUiState,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        onClick = { onEnabledChange(!state.enabled) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PresetBadge(preset = state.preset, enabled = state.enabled)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = presetTitle(state.preset),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = presetDescription(state.preset),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.preset_rules_count, state.ruleCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun AdvancedBlockingCard(
    uiState: BlockingUiState,
    onOpenSubscription: () -> Unit,
    onStrictChange: (Boolean) -> Unit,
    onEditPartner: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PremiumSwitchRow(
                isPremium = uiState.isPremium,
                checked = uiState.strictShortsReels,
                onCheckedChange = onStrictChange,
                onOpenSubscription = onOpenSubscription,
                title = stringResource(R.string.premium_strict_shorts_title),
                description = stringResource(R.string.premium_strict_shorts_description),
            )

            PremiumActionRow(
                isPremium = uiState.isPremium,
                title = stringResource(R.string.premium_partner_title),
                description = stringResource(R.string.premium_partner_description),
                valueLabel = uiState.accountabilityPartner.orEmpty(),
                onEdit = onEditPartner,
                onOpenSubscription = onOpenSubscription,
            )
        }
    }
}

@Composable
private fun CustomizeBlockedCard(
    uiState: BlockingUiState,
    onOpenSubscription: () -> Unit,
    onEditFriction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        PremiumActionRow(
            isPremium = uiState.isPremium,
            title = stringResource(R.string.premium_friction_title),
            description = stringResource(R.string.premium_friction_description, 60L),
            valueLabel = stringResource(
                R.string.premium_friction_description,
                uiState.frictionWindowMillis / 1_000L,
            ),
            onEdit = onEditFriction,
            onOpenSubscription = onOpenSubscription,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun FrictionWindowDialog(
    currentMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var value by remember {
        mutableStateOf((currentMillis / 1_000L).toString())
    }
    val seconds = value.toLongOrNull()
    val valid = seconds != null &&
        seconds in (PremiumSettings.MIN_FRICTION_WINDOW_MILLIS / 1_000L)..
        (PremiumSettings.MAX_FRICTION_WINDOW_MILLIS / 1_000L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.premium_friction_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text(text = stringResource(R.string.premium_friction_seconds_label)) },
                supportingText = {
                    Text(text = stringResource(R.string.premium_friction_range))
                },
                isError = !valid,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { seconds?.let(onConfirm) },
                enabled = valid,
            ) {
                Text(text = stringResource(R.string.premium_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.rules_cancel))
            }
        },
    )
}

@Composable
private fun PartnerDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.premium_partner_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.take(PremiumSettings.MAX_PARTNER_LENGTH)
                },
                singleLine = true,
                label = { Text(text = stringResource(R.string.premium_partner_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(text = stringResource(R.string.premium_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.rules_cancel))
            }
        },
    )
}

@Composable
private fun PresetBadge(
    preset: ProtectionPreset,
    enabled: Boolean,
) {
    val background = when (preset) {
        ProtectionPreset.ADULT_CONTENT -> Color(0xFFB3261E)
        ProtectionPreset.SHORT_VIDEOS -> Color(0xFF6750A4)
        ProtectionPreset.VISUAL_SEARCH -> Color(0xFF006A6A)
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (enabled) background else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (preset) {
                ProtectionPreset.ADULT_CONTENT -> "A"
                ProtectionPreset.SHORT_VIDEOS -> "V"
                ProtectionPreset.VISUAL_SEARCH -> "B"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun presetTitle(preset: ProtectionPreset): String = stringResource(
    when (preset) {
        ProtectionPreset.ADULT_CONTENT -> R.string.preset_adult_title
        ProtectionPreset.SHORT_VIDEOS -> R.string.preset_short_title
        ProtectionPreset.VISUAL_SEARCH -> R.string.preset_visual_title
    },
)

@Composable
private fun presetDescription(preset: ProtectionPreset): String = stringResource(
    when (preset) {
        ProtectionPreset.ADULT_CONTENT -> R.string.preset_adult_description
        ProtectionPreset.SHORT_VIDEOS -> R.string.preset_short_description
        ProtectionPreset.VISUAL_SEARCH -> R.string.preset_visual_description
    },
)
