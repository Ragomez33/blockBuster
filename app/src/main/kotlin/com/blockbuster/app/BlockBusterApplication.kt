package com.blockbuster.app

import android.app.Application
import com.blockbuster.core.common.contract.PremiumRepository
import com.blockbuster.core.overlay.OverlayCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class BlockBusterApplication : Application() {

    @Inject
    lateinit var overlayCoordinator: OverlayCoordinator

    @Inject
    lateinit var premiumRepository: PremiumRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Start listening as soon as the process exists. start() is idempotent,
        // so the foreground service can call it again safely.
        overlayCoordinator.start()

        // License sync must never block startup; the local DataStore value is
        // already available to the UI while this runs.
        applicationScope.launch {
            premiumRepository.checkSubscriptionStatus()
        }
    }
}
