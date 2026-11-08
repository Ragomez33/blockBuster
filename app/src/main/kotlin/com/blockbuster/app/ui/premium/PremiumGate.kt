package com.blockbuster.app.ui.premium

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.blockbuster.app.R

@Composable
fun PremiumBadge(
    modifier: Modifier = Modifier,
) {
    val lockedDescription = stringResource(R.string.premium_locked_state)

    Surface(
        modifier = modifier.semantics {
            contentDescription = lockedDescription
        },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.premium_locked),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Premium-only switch that stays visible when locked. Free taps open the
 * subscription flow and never invoke [onCheckedChange].
 */
@Composable
fun PremiumSwitchRow(
    isPremium: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSubscription: () -> Unit,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val state = premiumControlState(isPremium, PremiumControlAction.TOGGLE)
    val stateDescription = stringResource(
        if (isPremium) R.string.premium_unlocked_state else R.string.premium_locked_state,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(state.alpha)
            .clickable(enabled = !isPremium, onClick = onOpenSubscription)
            .semantics { this.stateDescription = stateDescription },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isLocked) {
                Text(
                    text = stringResource(R.string.premium_locked_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.showBadge) {
            PremiumBadge()
        }

        Switch(
            checked = checked,
            onCheckedChange = if (state.enabled) onCheckedChange else null,
            enabled = state.enabled,
        )
    }
}

/**
 * Premium-only row that opens an editor when unlocked. Free taps open the
 * subscription flow and never invoke [onEdit].
 */
@Composable
fun PremiumActionRow(
    isPremium: Boolean,
    title: String,
    description: String,
    valueLabel: String,
    onEdit: () -> Unit,
    onOpenSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = premiumControlState(isPremium, PremiumControlAction.EDIT)
    val stateDescription = stringResource(
        if (isPremium) R.string.premium_unlocked_state else R.string.premium_locked_state,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(state.alpha)
            .clickable {
                if (state.isLocked) onOpenSubscription() else onEdit()
            }
            .semantics { this.stateDescription = stateDescription },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = valueLabel.ifBlank { description },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isLocked) {
                Text(
                    text = stringResource(R.string.premium_locked_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.showBadge) {
            PremiumBadge()
        } else {
            Text(
                text = stringResource(R.string.premium_edit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Wraps a Premium-only control. Free users can tap the row to open the simulated
 * subscription flow, while Premium users interact with the content normally.
 */
@Composable
fun PremiumGate(
    isPremium: Boolean,
    onOpenSubscription: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isPremium, onClick = onOpenSubscription),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        if (!isPremium) {
            PremiumBadge()
        }
    }
}
