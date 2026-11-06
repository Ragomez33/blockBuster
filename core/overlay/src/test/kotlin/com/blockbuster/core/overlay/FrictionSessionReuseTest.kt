package com.blockbuster.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FrictionSessionReuseTest {

    @Test
    fun reusesSameTargetAndInputAfterHostRecreation() {
        var now = 1_000L
        val store = FrictionSessionStore(nowElapsedRealtime = { now })

        val first = store.obtain("example.com", "texto")
        store.updateInput("parcial")
        now = 5_000L
        val second = store.obtain("example.com", "texto")

        assertSame(second, store.current)
        assertEquals("parcial", second.input)
        assertEquals(1_000L, second.startedAtElapsedRealtime)    }

    @Test
    fun resetsForDifferentTarget() {
        var now = 1_000L
        val store = FrictionSessionStore(nowElapsedRealtime = { now })

        val first = store.obtain("one.test", "texto")
        store.updateInput("parcial")
        now = 2_000L
        val second = store.obtain("two.test", "texto")

        assertNotSame(first, second)
        assertEquals("two.test", second.target)
        assertEquals("", second.input)
        assertEquals(2_000L, second.startedAtElapsedRealtime)
    }

    @Test
    fun clearDropsSession() {
        val store = FrictionSessionStore(nowElapsedRealtime = { 1_000L })
        store.obtain("example.com", "texto")

        store.clear()

        assertNull(store.current)
    }
}
