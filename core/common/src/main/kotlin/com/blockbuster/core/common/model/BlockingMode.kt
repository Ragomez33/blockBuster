package com.blockbuster.core.common.model

/**
 * How a blocked package is enforced once it reaches the foreground.
 */
enum class BlockingMode {
    /** The friction screen is shown every time the package is opened. */
    ALWAYS,

    /** The friction screen stays until the user dismisses it. */
    UNTIL_DISMISSED,

    /** Enforcement ends when [BlockTimer.expiresAt] is reached. */
    TIMED,
}
