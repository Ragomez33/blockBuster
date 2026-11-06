package com.blockbuster.app.ui.rules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockbuster.app.R
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = { Text(text = stringResource(R.string.rules_add)) },
            )
        },
    ) { innerPadding ->
        if (uiState.rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.rules_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(
                    items = uiState.rules,
                    key = { rule -> rule.id },
                ) { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { enabled -> viewModel.setRuleEnabled(rule, enabled) },
                        onDelete = { viewModel.deleteRule(rule) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { pattern, type ->
                viewModel.addRule(pattern, type)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: BlockRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val typeLabel = typeLabel(rule.type)

    ListItem(
        headlineContent = { Text(text = rule.pattern) },
        supportingContent = { Text(text = typeLabel) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.rules_delete),
                    )
                }
            }
        },
    )
}

@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, BlockRuleType) -> Unit,
) {
    var pattern by rememberSaveable { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(BlockRuleType.DOMAIN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.rules_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(text = stringResource(R.string.rules_pattern_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = pattern.isNotEmpty() && pattern.isBlank(),
                    supportingText = {
                        if (pattern.isNotEmpty() && pattern.isBlank()) {
                            Text(text = stringResource(R.string.rules_pattern_required))
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.rules_type_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BlockRuleType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(text = typeLabel(type)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pattern, selectedType) },
                enabled = pattern.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.rules_add_confirm))
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
private fun typeLabel(type: BlockRuleType): String = when (type) {
    BlockRuleType.DOMAIN -> stringResource(R.string.rule_type_domain)
    BlockRuleType.KEYWORD_SHORTS -> stringResource(R.string.rule_type_shorts)
    BlockRuleType.KEYWORD_REELS -> stringResource(R.string.rule_type_reels)
}
