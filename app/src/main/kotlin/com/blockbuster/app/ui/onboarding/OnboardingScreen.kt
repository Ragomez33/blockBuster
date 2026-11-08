package com.blockbuster.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blockbuster.app.R
import com.blockbuster.app.onboarding.OnboardingUiState

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenDeviceAdminSettings: () -> Unit,
    onConsumeError: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.actionError) {
        val messageRes = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(messageRes))
        onConsumeError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PermissionCard(
                title = stringResource(R.string.onboarding_step_overlay_title),
                description = stringResource(R.string.onboarding_step_overlay_description),
                actionLabel = stringResource(R.string.onboarding_step_overlay_action),
                granted = uiState.permissions.overlayGranted,
                onAction = onOpenOverlaySettings,
            )

            PermissionCard(
                title = stringResource(R.string.onboarding_step_accessibility_title),
                description = stringResource(R.string.onboarding_step_accessibility_description),
                actionLabel = stringResource(R.string.onboarding_step_accessibility_action),
                granted = uiState.permissions.accessibilityEnabled,
                onAction = onOpenAccessibilitySettings,
            )

            PermissionCard(
                title = stringResource(R.string.onboarding_step_admin_title),
                description = stringResource(R.string.onboarding_step_admin_description),
                actionLabel = stringResource(R.string.onboarding_step_admin_action),
                granted = uiState.permissions.deviceAdminActive,
                onAction = onOpenDeviceAdminSettings,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    actionLabel: String,
    granted: Boolean,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        if (granted) {
                            R.string.onboarding_status_active
                        } else {
                            R.string.onboarding_status_pending
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}
