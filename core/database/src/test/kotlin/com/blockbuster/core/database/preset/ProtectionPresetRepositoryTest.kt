package com.blockbuster.core.database.preset

import com.blockbuster.core.common.model.ProtectionPreset
import com.blockbuster.core.database.RepositoryTestFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtectionPresetRepositoryTest {

    private lateinit var fixture: RepositoryTestFixture

    @Before
    fun setUp() {
        fixture = RepositoryTestFixture()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun presetsAreDisabledByDefault() = runTest {
        val enabled = fixture.presetRepository.observeEnabledPresets().first()

        assertTrue(enabled.isEmpty())
        assertFalse(
            fixture.presetRepository.isPresetEnabled(ProtectionPreset.ADULT_CONTENT),
        )
    }

    @Test
    fun enablingPresetEmitsItReactively() = runTest {
        fixture.presetRepository.setPresetEnabled(ProtectionPreset.SHORT_VIDEOS, true)

        assertEquals(
            setOf(ProtectionPreset.SHORT_VIDEOS),
            fixture.presetRepository.observeEnabledPresets().first(),
        )
        assertTrue(
            fixture.presetRepository.isPresetEnabled(ProtectionPreset.SHORT_VIDEOS),
        )
    }

    @Test
    fun multiplePresetsCanBeEnabledTogether() = runTest {
        fixture.presetRepository.setPresetEnabled(ProtectionPreset.ADULT_CONTENT, true)
        fixture.presetRepository.setPresetEnabled(ProtectionPreset.VISUAL_SEARCH, true)

        assertEquals(
            setOf(ProtectionPreset.ADULT_CONTENT, ProtectionPreset.VISUAL_SEARCH),
            fixture.presetRepository.observeEnabledPresets().first(),
        )
    }

    @Test
    fun disablingPresetRemovesIt() = runTest {
        fixture.presetRepository.setPresetEnabled(ProtectionPreset.SHORT_VIDEOS, true)
        fixture.presetRepository.setPresetEnabled(ProtectionPreset.SHORT_VIDEOS, false)

        assertTrue(fixture.presetRepository.observeEnabledPresets().first().isEmpty())
    }
}
