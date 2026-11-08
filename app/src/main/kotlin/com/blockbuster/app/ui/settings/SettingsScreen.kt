package com.blockbuster.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockbuster.app.R
import com.blockbuster.app.ui.premium.PremiumBadge

@Composable
fun SettingsScreen(
    onOpenSubscription: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSupportDialog by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.tab_settings),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_account),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    if (uiState.isPremium) {
                                        R.string.settings_premium
                                    } else {
                                        R.string.settings_free
                                    },
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (uiState.isPremium) {
                            PremiumBadge()
                        }
                    }
                    if (!uiState.isPremium) {
                        Button(
                            onClick = onOpenSubscription,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.premium_subscribe))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_check_updates))
                    },
                    supportingContent = uiState.updateResult?.let { result ->
                        { Text(text = stringResource(result)) }
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    },
                    modifier = Modifier.clickable(
                        enabled = !uiState.isCheckingUpdates,
                        onClick = viewModel::checkForUpdates,
                    ),
                )
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_privacy))
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenPrivacy),
                )
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_support))
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = { showSupportDialog = true }),
                )
            }
        }
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text(text = stringResource(R.string.settings_support_title)) },
            text = { Text(text = stringResource(R.string.settings_support_body)) },
            confirmButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text(text = stringResource(R.string.settings_support_close))
                }
            },
        )
    }
}
