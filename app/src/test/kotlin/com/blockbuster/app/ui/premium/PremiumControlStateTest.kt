package com.blockbuster.app.ui.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumControlStateTest {

    @Test
    fun freeLockedToggleMapsToSubscription() {
        val state = premiumControlState(
            isPremium = false,
            premiumAction = PremiumControlAction.TOGGLE,
        )

        assertFalse(state.isPremium)
        assertTrue(state.isLocked)
        assertFalse(state.enabled)
        assertEquals(DISABLED_ALPHA, state.alpha, 0.001f)
        assertTrue(state.showBadge)
        assertEquals(PremiumControlAction.OPEN_SUBSCRIPTION, state.action)
    }

    @Test
    fun freeLockedEditMapsToSubscription() {
        val state = premiumControlState(
            isPremium = false,
            premiumAction = PremiumControlAction.EDIT,
        )

        assertTrue(state.isLocked)
        assertFalse(state.enabled)
        assertEquals(PremiumControlAction.OPEN_SUBSCRIPTION, state.action)
    }

    @Test
    fun premiumToggleIsEnabled() {
        val state = premiumControlState(
            isPremium = true,
            premiumAction = PremiumControlAction.TOGGLE,
        )

        assertTrue(state.isPremium)
        assertFalse(state.isLocked)
        assertTrue(state.enabled)
        assertEquals(ENABLED_ALPHA, state.alpha, 0.001f)
        assertFalse(state.showBadge)
        assertEquals(PremiumControlAction.TOGGLE, state.action)
    }

    @Test
    fun premiumEditIsEnabled() {
        val state = premiumControlState(
            isPremium = true,
            premiumAction = PremiumControlAction.EDIT,
        )

        assertTrue(state.enabled)
        assertEquals(PremiumControlAction.EDIT, state.action)
    }

    @Test
    fun allPremiumOptionsUseTheSameMappingContract() {
        PremiumOption.entries.forEach { option ->
            val free = premiumControlState(
                isPremium = false,
                premiumAction = PremiumControlAction.TOGGLE,
            )
            val premium = premiumControlState(
                isPremium = true,
                premiumAction = PremiumControlAction.TOGGLE,
            )

            assertTrue("$option should be locked for Free", free.isLocked)
            assertTrue("$option should be enabled for Premium", premium.enabled)
        }
    }
}
