package com.blockbuster.app.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BootReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun ignoresUnrelatedBroadcasts() {
        BootReceiver().onReceive(context, Intent("com.example.UNRELATED"))
    }

    @Test
    fun handlesBootBroadcastWithoutHiltCrash() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun handlesPackageReplacedWithoutHiltCrash() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
    }
}
