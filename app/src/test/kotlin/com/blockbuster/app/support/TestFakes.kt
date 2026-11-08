package com.blockbuster.app.support

import com.blockbuster.core.common.contract.AccessibilityConnectionState
import com.blockbuster.core.common.contract.AccessibilityEngine
import com.blockbuster.core.common.contract.BlockRulesRepository
import com.blockbuster.core.common.contract.BlocklistRepository
import com.blockbuster.core.common.contract.FocusSessionRepository
import com.blockbuster.core.common.contract.PremiumRepository
import com.blockbuster.core.common.contract.ProtectionPresetRepository
import com.blockbuster.core.common.model.BlockLog
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.BlockTimer
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.common.model.FocusPhase
import com.blockbuster.core.common.model.FocusRunState
import com.blockbuster.core.common.model.FocusSession
import com.blockbuster.core.common.model.IsPremiumState
import com.blockbuster.core.common.model.LicenseSyncResult
import com.blockbuster.core.common.model.PremiumSettings
import com.blockbuster.core.common.model.ProtectionPreset
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeAccessibilityEngine : AccessibilityEngine {
    override val connectionState =
        MutableStateFlow(AccessibilityConnectionState.DISCONNECTED)
}

class FakeProtectionPresetRepository : ProtectionPresetRepository {
    val enabled = MutableStateFlow<Set<ProtectionPreset>>(emptySet())

    override fun observeEnabledPresets(): Flow<Set<ProtectionPreset>> = enabled

    override suspend fun isPresetEnabled(preset: ProtectionPreset): Boolean =
        preset in enabled.value

    override suspend fun setPresetEnabled(preset: ProtectionPreset, enabled: Boolean) {
        this.enabled.value = if (enabled) {
            this.enabled.value + preset
        } else {
            this.enabled.value - preset
        }
    }
}

class FakeBlockRulesRepository : BlockRulesRepository {
    val activeRules = MutableStateFlow<List<BlockRule>>(emptyList())
    val timer = MutableStateFlow<BlockTimer?>(null)

    override fun observeActiveRules(): Flow<List<BlockRule>> = activeRules
    override fun observeAllRules(): Flow<List<BlockRule>> = activeRules
    override fun observeRule(id: Long): Flow<BlockRule?> =
        activeRules.map { rules -> rules.firstOrNull { it.id == id } }
    override suspend fun getActiveRulesByType(type: BlockRuleType): AppResult<List<BlockRule>> =
        AppResult.Success(activeRules.value.filter { it.type == type })
    override suspend fun insertRule(draft: BlockRuleDraft): AppResult<Long> =
        AppResult.Success(1L)
    override suspend fun updateRule(rule: BlockRule): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun deleteRule(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun recordBlock(
        ruleId: Long,
        target: String,
        timestamp: Long,
    ): AppResult<Long> = AppResult.Success(1L)
    override fun observeBlockLogs(ruleId: Long): Flow<List<BlockLog>> = flowOf(emptyList())
    override fun observeTimer(): Flow<BlockTimer?> = timer
    override suspend fun startTimer(timer: BlockTimer): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun stopTimer(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun clearAll(): AppResult<Unit> = AppResult.Success(Unit)
}

class FakePremiumRepository : PremiumRepository {
    val premium = MutableStateFlow(IsPremiumState())
    val settings = MutableStateFlow(PremiumSettings())
    var failNextWrite = false

    override fun observePremiumState(): Flow<IsPremiumState> = premium
    override fun observeSettings(): Flow<PremiumSettings> = settings

    var verifyResult: AppResult<IsPremiumState> = AppResult.Success(IsPremiumState())
    var verifyDelayMillis: Long = 0L
    var verifyCalls: Int = 0
        private set

    override suspend fun checkSubscriptionStatus(): LicenseSyncResult =
        LicenseSyncResult.Updated(premium.value)

    override suspend fun verifyPayment(): AppResult<IsPremiumState> {
        verifyCalls++
        if (verifyDelayMillis > 0L) delay(verifyDelayMillis)
        return verifyResult
    }

    override suspend fun setPremium(premium: Boolean): AppResult<Unit> {
        if (failNextWrite) {
            failNextWrite = false
            return AppResult.Failure(AppError.Storage("failure"))
        }
        this.premium.value = IsPremiumState(premium)
        return AppResult.Success(Unit)
    }

    override suspend fun setStrictShortsReels(enabled: Boolean): AppResult<Unit> {
        if (!premium.value.isPremium) {
            return AppResult.Failure(AppError.Permission("premium"))
        }
        settings.value = settings.value.copy(strictShortsReels = enabled)
        return AppResult.Success(Unit)
    }

    override suspend fun setFrictionWindowMillis(value: Long): AppResult<Unit> {
        if (!premium.value.isPremium) {
            return AppResult.Failure(AppError.Permission("premium"))
        }
        settings.value = settings.value.copy(frictionWindowMillis = value)
        return AppResult.Success(Unit)
    }

    override suspend fun setAccountabilityPartner(value: String?): AppResult<Unit> {
        if (!premium.value.isPremium) {
            return AppResult.Failure(AppError.Permission("premium"))
        }
        settings.value = settings.value.copy(accountabilityPartner = value)
        return AppResult.Success(Unit)
    }
}

class FakeFocusSessionRepository : FocusSessionRepository {
    val session = MutableStateFlow(FocusSession())
    var advancePhaseCalls = 0
    var failNextStart = false

    override fun observeSession(): Flow<FocusSession> = session

    override suspend fun start(workMinutes: Int, breakMinutes: Int): AppResult<Unit> {
        if (failNextStart) {
            failNextStart = false
            return AppResult.Failure(AppError.Unexpected("invalid durations"))
        }
        session.value = FocusSession(
            runState = FocusRunState.RUNNING,
            phase = FocusPhase.WORK,
            workMinutes = workMinutes,
            breakMinutes = breakMinutes,
            phaseEndsAtEpochMillis = System.currentTimeMillis() + workMinutes * 60_000L,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun pause(): AppResult<Unit> {
        session.value = session.value.copy(
            runState = FocusRunState.PAUSED,
            pausedRemainingMillis = 60_000L,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun resume(): AppResult<Unit> {
        session.value = session.value.copy(runState = FocusRunState.RUNNING)
        return AppResult.Success(Unit)
    }

    override suspend fun stop(): AppResult<Unit> {
        session.value = FocusSession()
        return AppResult.Success(Unit)
    }

    override suspend fun saveDurations(workMinutes: Int, breakMinutes: Int): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun advancePhase(nowEpochMillis: Long): AppResult<Unit> {
        advancePhaseCalls++
        return AppResult.Success(Unit)
    }
}

class FakeBlocklistRepository : BlocklistRepository {
    val entries = MutableStateFlow<List<BlocklistEntry>>(emptyList())
    private var nextId = 1L
    var failNextAdd = false

    override fun observeEntries(): Flow<List<BlocklistEntry>> = entries

    override fun observeEntries(
        listType: BlocklistType,
        targetType: BlocklistTargetType,
    ): Flow<List<BlocklistEntry>> = entries.map { list ->
        list.filter { it.listType == listType && it.targetType == targetType }
    }

    override suspend fun addEntry(draft: BlocklistEntryDraft): AppResult<Long> {
        if (failNextAdd) {
            failNextAdd = false
            return AppResult.Failure(AppError.Conflict("duplicate"))
        }
        val id = nextId++
        entries.value = entries.value + BlocklistEntry(
            id = id,
            value = draft.value.trim(),
            targetType = draft.targetType,
            listType = draft.listType,
            createdAt = draft.createdAt,
        )
        return AppResult.Success(id)
    }

    override suspend fun deleteEntry(id: Long): AppResult<Unit> {
        entries.value = entries.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }
}
