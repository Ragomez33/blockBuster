package com.blockbuster.app.admin

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlockerDeviceAdminReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun disableRequestedReturnsAUserWarning() {
        val receiver = BlockerDeviceAdminReceiver()

        val warning = receiver.onDisableRequested(context, Intent())

        assertTrue(warning.isNotEmpty())
    }

    @Test
    fun lifecycleCallbacksDoNotCrashWithoutHiltEntryPoint() {
        val receiver = BlockerDeviceAdminReceiver()

        receiver.onEnabled(context, Intent())
        receiver.onDisabled(context, Intent())
    }
}
