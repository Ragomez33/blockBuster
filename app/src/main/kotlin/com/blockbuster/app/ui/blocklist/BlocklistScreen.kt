package com.blockbuster.app.ui.blocklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockbuster.app.R
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(
    viewModel: BlocklistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionError) {
        val messageRes = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(messageRes))
        viewModel.consumeError()
    }

    val appsTabSelected = uiState.selectedTargetType == BlocklistTargetType.APP

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.tab_blocklist),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.blocklist_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TabRow(
                selectedTabIndex = if (appsTabSelected) 0 else 1,
            ) {
                Tab(
                    selected = appsTabSelected,
                    onClick = { viewModel.selectTargetType(BlocklistTargetType.APP) },
                    text = { Text(text = stringResource(R.string.blocklist_tab_apps)) },
                )
                Tab(
                    selected = !appsTabSelected,
                    onClick = {
                        if (appsTabSelected) {
                            viewModel.selectTargetType(BlocklistTargetType.WEBSITE)
                        }
                    },
                    text = { Text(text = stringResource(R.string.blocklist_tab_sites_keywords)) },
                )
            }

            if (!appsTabSelected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.selectedTargetType == BlocklistTargetType.WEBSITE,
                        onClick = { viewModel.selectTargetType(BlocklistTargetType.WEBSITE) },
                        label = { Text(text = stringResource(R.string.blocklist_target_website)) },
                    )
                    FilterChip(
                        selected = uiState.selectedTargetType == BlocklistTargetType.KEYWORD,
                        onClick = { viewModel.selectTargetType(BlocklistTargetType.KEYWORD) },
                        label = { Text(text = stringResource(R.string.blocklist_target_keyword)) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.selectedListType == BlocklistType.BLACKLIST,
                    onClick = { viewModel.selectListType(BlocklistType.BLACKLIST) },
                    label = { Text(text = stringResource(R.string.blocklist_blacklist)) },
                )
                FilterChip(
                    selected = uiState.selectedListType == BlocklistType.WHITELIST,
                    onClick = { viewModel.selectListType(BlocklistType.WHITELIST) },
                    label = { Text(text = stringResource(R.string.blocklist_whitelist)) },
                )
            }

            if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.blocklist_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.blocklist_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.entries,
                        key = { entry -> entry.id },
                    ) { entry ->
                        BlocklistEntryCard(
                            entry = entry,
                            onDelete = { viewModel.deleteEntry(entry) },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )

        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            },
            text = { Text(text = stringResource(R.string.blocklist_add)) },
        )
    }

    if (showAddDialog) {
        AddBlocklistEntryDialog(
            listType = uiState.selectedListType,
            targetType = if (appsTabSelected) {
                BlocklistTargetType.APP
            } else {
                uiState.selectedTargetType
            },
            onDismiss = { showAddDialog = false },
            onConfirm = { value ->
                viewModel.addEntry(value)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun BlocklistEntryCard(
    entry: BlocklistEntry,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.value,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        when (entry.targetType) {
                            BlocklistTargetType.APP -> R.string.blocklist_target_app
                            BlocklistTargetType.WEBSITE -> R.string.blocklist_target_website
                            BlocklistTargetType.KEYWORD -> R.string.blocklist_target_keyword
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.blocklist_delete),
                )
            }
        }
    }
}
