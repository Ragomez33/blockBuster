package com.blockbuster.core.overlay

import android.content.Context
import android.util.Log
import com.blockbuster.core.common.contract.BlockRulesRepository
import com.blockbuster.core.common.contract.BlocklistRepository
import com.blockbuster.core.common.contract.FocusSessionRepository
import com.blockbuster.core.common.contract.FrictionConfig
import com.blockbuster.core.common.contract.FrictionMode
import com.blockbuster.core.common.contract.OverlayController
import com.blockbuster.core.common.contract.PremiumRepository
import com.blockbuster.core.common.event.BlockEventBus
import com.blockbuster.core.common.event.ProtectionEventBus
import com.blockbuster.core.common.model.BlockTimer
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.DetectionSignal
import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.model.IsPremiumState
import com.blockbuster.core.common.model.PremiumSettings
import com.blockbuster.core.common.model.ProtectionThreat
import com.blockbuster.core.common.model.ProtectionThreatType
import com.blockbuster.core.common.model.SensitiveScreenCategory
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.overlay.focus.FocusModeRuleProvider
import com.blockbuster.core.overlay.matching.BlockRuleMatcher
import com.blockbuster.core.overlay.matching.BlocklistMatcher
import com.blockbuster.core.overlay.premium.PremiumRuleProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Central blocking dispatcher. It observes accessibility detections, active
 * rules and overlay visibility, then decides whether to show or hide the
 * friction window. Nothing here touches Compose or WindowManager directly.
 */
@Singleton
class OverlayCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: BlockEventBus,
    private val protectionEventBus: ProtectionEventBus,
    private val overlayController: OverlayController,
    private val repository: BlockRulesRepository,
    private val blocklistRepository: BlocklistRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val premiumRepository: PremiumRepository,
    private val matcher: BlockRuleMatcher,
    private val blocklistMatcher: BlocklistMatcher,
    private val focusModeRuleProvider: FocusModeRuleProvider,
    private val premiumRuleProvider: PremiumRuleProvider,
    @OverlayScope private val scope: CoroutineScope,
) {
    private val rules = repository.observeActiveRules()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val timer = repository.observeTimer()
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val blocklistEntries = blocklistRepository.observeEntries()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val focusSession = focusSessionRepository.observeSession()
        .stateIn(scope, SharingStarted.Eagerly, FocusSession())

    private val premiumState = premiumRepository.observePremiumState()
        .stateIn(scope, SharingStarted.Eagerly, IsPremiumState())

    private val premiumSettings = premiumRepository.observeSettings()
        .stateIn(scope, SharingStarted.Eagerly, PremiumSettings())

    private val externalContext = combine(
        blocklistEntries,
        focusSession,
        premiumState,
        premiumSettings,
    ) { entries, session, premium, settings ->
        ExternalContext(
            blocklistEntries = entries,
            focusSession = session,
            isPremium = premium.isPremium,
            premiumSettings = settings,
        )
    }

    private val _state = MutableStateFlow<OverlayState>(OverlayState.Idle)
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    private var started = false
    private var currentActivation: BlockActivationKey? = null
    private var dismissedActivation: BlockActivationKey? = null
    private var dismissedAtDetectionTimestamp: Long = 0L
    private var auditedActivation: BlockActivationKey? = null
    private var unlockedActivation: BlockActivationKey? = null
    private var unlockedAtEpochMillis: Long = 0L
    private var lastOverlayVisible = false
    private var protectionActive = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            combine(
                eventBus.currentDetection,
                rules,
                timer,
                overlayController.isOverlayVisible,
                externalContext,
            ) { detection, currentRules, currentTimer, overlayVisible, context ->
                CoordinatorSnapshot(
                    detection = detection,
                    rules = currentRules,
                    timer = currentTimer,
                    overlayVisible = overlayVisible,
                    blocklistEntries = context.blocklistEntries,
                    focusSession = context.focusSession,
                    isPremium = context.isPremium,
                    premiumSettings = context.premiumSettings,
                )
            }
                .distinctUntilChanged()
                .collect(::onSnapshot)
        }

        scope.launch {
            overlayController.unlockEvents.collect { unlock ->
                unlockedActivation = currentActivation
                unlockedAtEpochMillis = unlock.timestampEpochMillis
                currentActivation = null
                dismissedActivation = null
                dismissedAtDetectionTimestamp = 0L
                auditedActivation = null
                _state.value = OverlayState.Idle
                overlayController.hideFriction()
            }
        }

        scope.launch {
            protectionEventBus.currentThreat.collect { threat ->
                if (threat == null) {
                    if (protectionActive) {
                        protectionActive = false
                        currentActivation = null
                        dismissedActivation = null
                        auditedActivation = null
                        _state.value = OverlayState.Idle
                        overlayController.hideFriction()
                    }
                } else {
                    protectionActive = true
                    currentActivation = null
                    dismissedActivation = null
                    auditedActivation = null
                    _state.value = OverlayState.ProtectionBlocking(threat.screen)
                    overlayController.showFriction(buildProtectionConfig(threat))
                }
            }
        }
    }

    private fun onSnapshot(snapshot: CoordinatorSnapshot) {
        if (protectionActive) return
        val detection = snapshot.detection

        if (blocklistMatcher.isWhitelisted(
                packageName = detection?.packageName,
                target = detection?.target,
                className = detection?.className,
                entries = snapshot.blocklistEntries,
            )
        ) {
            if (snapshot.overlayVisible) {
                overlayController.hideFriction()
            }
            currentActivation = null
            dismissedActivation = null
            auditedActivation = null
            _state.value = OverlayState.Idle
            return
        }

        val effectiveRules = buildList {
            addAll(snapshot.rules)
            addAll(blocklistMatcher.blacklistRules(snapshot.blocklistEntries))
            if (snapshot.isPremium && snapshot.premiumSettings.strictShortsReels) {
                addAll(premiumRuleProvider.strictShortsReelsRules())
            }
            addAll(focusModeRuleProvider.rules(snapshot.focusSession))
        }

        val match = matcher.findMatch(
            target = detection?.target,
            rules = effectiveRules,
            detectedAtEpochMillis = detection?.timestampEpochMillis ?: 0L,
            packageName = detection?.packageName,
            className = detection?.className,
        )
        val activationKey = match?.let { BlockActivationKey(it.rule.id, it.normalizedTarget) }

        if (clearUnlockSuppressionIfNeeded(detection, activationKey)) {
            if (snapshot.overlayVisible) {
                overlayController.hideFriction()
            }
            currentActivation = null
            _state.value = OverlayState.Idle
            return
        }

        clearDismissalIfNeeded(detection, activationKey)
        detectManualDismissal(snapshot, activationKey, match, detection)
        lastOverlayVisible = snapshot.overlayVisible

        val timerExpired = snapshot.timer != null &&
            snapshot.timer.packageName == detection?.packageName &&
            snapshot.timer.isExpired()

        if (match == null || timerExpired) {
            if (snapshot.overlayVisible) {
                overlayController.hideFriction()
            }
            currentActivation = null
            dismissedActivation = null
            auditedActivation = null
            _state.value = OverlayState.Idle
            return
        }

        if (activationKey == dismissedActivation) {
            if (snapshot.overlayVisible) {
                overlayController.hideFriction()
            }
            _state.value = OverlayState.Dismissed(match)
            return
        }

        if (snapshot.overlayVisible && activationKey == currentActivation) {
            _state.value = OverlayState.Blocking(
                match = match,
                audited = activationKey == auditedActivation,
            )
            return
        }

        currentActivation = activationKey
        _state.value = OverlayState.Blocking(
            match = match,
            audited = activationKey == auditedActivation,
        )
        overlayController.showFriction(
            buildConfig(
                match = match,
                frictionWindowMillis = if (snapshot.isPremium) {
                    snapshot.premiumSettings.frictionWindowMillis
                } else {
                    PremiumSettings.DEFAULT_FRICTION_WINDOW_MILLIS
                },
            ),
        )

        if (activationKey != null && activationKey != auditedActivation) {
            auditedActivation = activationKey
            auditBlock(match)
        }
    }

    private fun clearUnlockSuppressionIfNeeded(
        detection: DetectionSignal?,
        activationKey: BlockActivationKey?,
    ): Boolean {
        val suppressed = unlockedActivation ?: return false
        val receiveNewDetection = detection != null &&
            detection.timestampEpochMillis > unlockedAtEpochMillis
        if (activationKey != suppressed || receiveNewDetection) {
            unlockedActivation = null
            unlockedAtEpochMillis = 0L
            return false
        }
        return true
    }

    private fun clearDismissalIfNeeded(
        detection: DetectionSignal?,
        activationKey: BlockActivationKey?,
    ) {        val dismissed = dismissedActivation ?: return
        val receiveNewDetection = detection != null &&
            detection.timestampEpochMillis > dismissedAtDetectionTimestamp
        if (activationKey != dismissed || receiveNewDetection) {
            dismissedActivation = null
            dismissedAtDetectionTimestamp = 0L
        }
    }

    private fun detectManualDismissal(
        snapshot: CoordinatorSnapshot,
        activationKey: BlockActivationKey?,
        match: BlockMatch?,
        detection: DetectionSignal?,
    ) {
        val wasVisible = lastOverlayVisible
        if (!snapshot.overlayVisible &&
            wasVisible &&
            activationKey != null &&
            activationKey == currentActivation &&
            match != null
        ) {
            dismissedActivation = activationKey
            dismissedAtDetectionTimestamp = detection?.timestampEpochMillis ?: 0L
            auditedActivation = null
            _state.value = OverlayState.Dismissed(match)
        }
    }

    private fun buildProtectionConfig(threat: ProtectionThreat): FrictionConfig {
        val screen = threat.screen
        val message = when (threat.type) {
            ProtectionThreatType.ADMIN_DISABLE_REQUESTED,
            ProtectionThreatType.ADMIN_DISABLED,
            -> context.getString(R.string.protection_overlay_admin_attempt)

            ProtectionThreatType.SENSITIVE_SCREEN -> when (screen?.category) {
                SensitiveScreenCategory.ACCESSIBILITY_SETTINGS ->
                    context.getString(R.string.protection_overlay_accessibility)

                SensitiveScreenCategory.APP_INFO ->
                    context.getString(R.string.protection_overlay_app_info)

                SensitiveScreenCategory.DEVICE_ADMIN_SETTINGS ->
                    context.getString(R.string.protection_overlay_device_admin)

                null -> context.getString(R.string.protection_overlay_admin_attempt)
            }
        }

        return FrictionConfig(
            packageName = screen?.packageName ?: PROTECTION_PACKAGE,
            appLabel = screen?.className ?: PROTECTION_PACKAGE,
            title = context.getString(R.string.protection_overlay_title),
            message = message,
            dismissLabel = "",
            mode = FrictionMode.PROTECTION,
        )
    }

    private fun buildConfig(
        match: BlockMatch,
        frictionWindowMillis: Long,
    ): FrictionConfig = FrictionConfig(
        packageName = match.target,
        appLabel = match.rule.pattern,
        title = context.getString(R.string.overlay_friction_title),
        message = context.getString(R.string.overlay_friction_message, match.rule.pattern),
        dismissLabel = context.getString(R.string.overlay_friction_dismiss),
        frictionWindowMillis = frictionWindowMillis,
    )

    private fun auditBlock(match: BlockMatch) {
        scope.launch {
            when (val result = repository.recordBlock(
                ruleId = match.rule.id,
                target = match.target,
                timestamp = match.detectedAtEpochMillis,
            )) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> Log.w(
                    TAG,
                    "Block audit failed for rule ${match.rule.id}: ${result.error}",
                )
            }
        }
    }

    private data class CoordinatorSnapshot(
        val detection: DetectionSignal?,
        val rules: List<BlockRule>,
        val timer: BlockTimer?,
        val overlayVisible: Boolean,
        val blocklistEntries: List<BlocklistEntry>,
        val focusSession: FocusSession,
        val isPremium: Boolean,
        val premiumSettings: PremiumSettings,
    )

    private data class ExternalContext(
        val blocklistEntries: List<BlocklistEntry>,
        val focusSession: FocusSession,
        val isPremium: Boolean,
        val premiumSettings: PremiumSettings,
    )

    private companion object {
        const val TAG = "OverlayCoordinator"
        const val PROTECTION_PACKAGE = "android.system.protection"
    }
}
