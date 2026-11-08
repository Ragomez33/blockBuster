package com.blockbuster.app.ui.blocklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blockbuster.app.R
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType

@Composable
fun AddBlocklistEntryDialog(
    listType: BlocklistType,
    targetType: BlocklistTargetType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    val trimmed = value.trim()
    val valid = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.blocklist_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        R.string.blocklist_add_context,
                        stringResource(
                            if (listType == BlocklistType.BLACKLIST) {
                                R.string.blocklist_blacklist
                            } else {
                                R.string.blocklist_whitelist
                            },
                        ),
                        stringResource(targetType.labelRes()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(targetType.valueLabelRes())) },
                    isError = value.isNotEmpty() && !valid,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = valid,
            ) {
                Text(text = stringResource(R.string.blocklist_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.rules_cancel))
            }
        },
    )
}

private fun BlocklistTargetType.labelRes(): Int = when (this) {
    BlocklistTargetType.APP -> R.string.blocklist_tab_apps
    BlocklistTargetType.WEBSITE -> R.string.blocklist_target_website
    BlocklistTargetType.KEYWORD -> R.string.blocklist_target_keyword
}

private fun BlocklistTargetType.valueLabelRes(): Int = when (this) {
    BlocklistTargetType.APP -> R.string.blocklist_value_app
    BlocklistTargetType.WEBSITE -> R.string.blocklist_value_website
    BlocklistTargetType.KEYWORD -> R.string.blocklist_value_keyword
}
