package com.blockbuster.app

import android.app.Application
import com.blockbuster.core.overlay.OverlayCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BlockBusterApplication : Application() {

    @Inject
    lateinit var overlayCoordinator: OverlayCoordinator

    override fun onCreate() {
        super.onCreate()
        // Start listening as soon as the process exists. start() is idempotent,
        // so the foreground service can call it again safely.
        overlayCoordinator.start()
    }
}
