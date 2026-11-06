package com.blockbuster.core.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.contract.FrictionConfig
import com.blockbuster.core.common.contract.FrictionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayComposeHostTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun hideDestroysLifecycleAndIsIdempotent() {
        val host = OverlayComposeHost(
            context = context,
            config = config(),
            session = session(),
            onInputChanged = {},
            onUnlock = {},
        )

        assertFalse(host.isDestroyed)
        host.show()
        host.hide()

        assertTrue(host.isDestroyed)
        host.hide()
        assertTrue(host.isDestroyed)
    }

    @Test
    fun aNewHostCanBeShownAfterAnotherWasDestroyed() {
        val first = OverlayComposeHost(
            context,
            config(),
            session(),
            onInputChanged = {},
            onUnlock = {},
        )
        first.show()
        first.hide()

        val second = OverlayComposeHost(
            context,
            config(),
            session(),
            onInputChanged = {},
            onUnlock = {},
        )
        second.show()
        assertFalse(second.isDestroyed)
        second.hide()
        assertTrue(second.isDestroyed)
    }

    private fun config() = FrictionConfig(
        packageName = "https://example.com",
        appLabel = "example.com",
        title = "Pausa",
        message = "Contenido bloqueado",
        dismissLabel = "Volver",
        mode = FrictionMode.STANDARD,
    )

    private fun session() = FrictionSession(
        target = "https://example.com",
        requiredText = "texto de prueba",
        startedAtElapsedRealtime = 0L,
    )
}
