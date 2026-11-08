package com.blockbuster.app.ui.blocking

import androidx.test.core.app.ApplicationProvider
import com.blockbuster.app.support.FakeAccessibilityEngine
import com.blockbuster.app.support.FakeBlockRulesRepository
import com.blockbuster.app.support.FakePremiumRepository
import com.blockbuster.app.support.FakeProtectionPresetRepository
import com.blockbuster.core.common.model.ProtectionPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockingViewModelTest {

    private val presetRepository = FakeProtectionPresetRepository()
    private val premiumRepository = FakePremiumRepository()
    private lateinit var viewModel: BlockingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = BlockingViewModel(
            context = ApplicationProvider.getApplicationContext(),
            presetRepository = presetRepository,
            blockRulesRepository = FakeBlockRulesRepository(),
            accessibilityEngine = FakeAccessibilityEngine(),
            premiumRepository = premiumRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposesPresetStateAndPremiumState() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        assertFalse(viewModel.uiState.value.isPremium)
        assertTrue(viewModel.uiState.value.presets.any { it.preset == ProtectionPreset.ADULT_CONTENT })

        presetRepository.setPresetEnabled(ProtectionPreset.SHORT_VIDEOS, true)
        premiumRepository.setPremium(true)
        premiumRepository.setStrictShortsReels(true)

        assertTrue(viewModel.uiState.value.presets.first {
            it.preset == ProtectionPreset.SHORT_VIDEOS
        }.enabled)
        assertTrue(viewModel.uiState.value.isPremium)
        assertTrue(viewModel.uiState.value.strictShortsReels)
    }

    @Test
    fun freeCannotPersistPremiumSettings() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.setStrictShortsReels(true)

        assertFalse(viewModel.uiState.value.strictShortsReels)
        assertTrue(viewModel.uiState.value.actionError != null)
    }

    @Test
    fun premiumSettingsCanBeEdited() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        premiumRepository.setPremium(true)

        viewModel.setStrictShortsReels(true)
        viewModel.setFrictionWindowMillis(30_000L)
        viewModel.setAccountabilityPartner("Ada")

        assertTrue(viewModel.uiState.value.strictShortsReels)
        assertEquals(30_000L, viewModel.uiState.value.frictionWindowMillis)
        assertEquals("Ada", viewModel.uiState.value.accountabilityPartner)
    }

    @Test
    fun presetToggleChangesSelectedState() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.setPresetEnabled(ProtectionPreset.VISUAL_SEARCH, true)

        assertEquals(
            true,
            viewModel.uiState.value.presets.first {
                it.preset == ProtectionPreset.VISUAL_SEARCH
            }.enabled,
        )
    }
}
