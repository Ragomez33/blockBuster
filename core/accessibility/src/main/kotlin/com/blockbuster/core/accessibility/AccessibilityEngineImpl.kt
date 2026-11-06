package com.blockbuster.core.accessibility

import com.blockbuster.core.common.contract.AccessibilityConnectionState
import com.blockbuster.core.common.contract.AccessibilityEngine
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityEngineImpl @Inject constructor(
    stateHolder: AccessibilityEngineStateHolder,
) : AccessibilityEngine {
    override val connectionState: StateFlow<AccessibilityConnectionState> =
        stateHolder.connectionState
}
