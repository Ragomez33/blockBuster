package com.blockbuster.app.ui.premium

enum class PremiumOption {
    STRICT_SHORTS_REELS,
    FRICTION_TIME_CUSTOMIZATION,
    ACCOUNTABILITY_PARTNER,
}

enum class PremiumControlAction {
    TOGGLE,
    EDIT,
    OPEN_SUBSCRIPTION,
}

data class PremiumControlState(
    val isPremium: Boolean,
    val isLocked: Boolean,
    val enabled: Boolean,
    val alpha: Float,
    val showBadge: Boolean,
    val action: PremiumControlAction,
)

/**
 * Pure mapping from subscription state to the visual/interaction contract of a
 * Premium control. Free always maps to OPEN_SUBSCRIPTION and never allows the
 * requested toggle/edit action.
 */
fun premiumControlState(
    isPremium: Boolean,
    premiumAction: PremiumControlAction,
): PremiumControlState = PremiumControlState(
    isPremium = isPremium,
    isLocked = !isPremium,
    enabled = isPremium,
    alpha = if (isPremium) ENABLED_ALPHA else DISABLED_ALPHA,
    showBadge = !isPremium,
    action = if (isPremium) premiumAction else PremiumControlAction.OPEN_SUBSCRIPTION,
)

const val DISABLED_ALPHA = 0.55f
const val ENABLED_ALPHA = 1f
