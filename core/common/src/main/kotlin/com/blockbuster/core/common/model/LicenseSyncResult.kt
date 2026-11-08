package com.blockbuster.core.common.model

/**
 * Result of a remote license synchronization attempt.
 */
sealed interface LicenseSyncResult {
    data class Updated(val license: IsPremiumState) : LicenseSyncResult

    data class Offline(val cached: IsPremiumState) : LicenseSyncResult

    data class Invalid(val cached: IsPremiumState) : LicenseSyncResult

    data object NoIdentity : LicenseSyncResult
}
