package com.blockbuster.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.blockbuster.core.common.contract.FrictionConfig
import com.blockbuster.core.common.contract.FrictionMode
import com.blockbuster.core.common.contract.OverlayController
import com.blockbuster.core.common.contract.OverlayUnlock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WindowManager-backed overlay controller using TYPE_APPLICATION_OVERLAY.
 * All WindowManager mutations are marshalled to the main thread.
 */
@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : OverlayController {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionStore = FrictionSessionStore()

    private var host: OverlayComposeHost? = null
    private var currentConfig: FrictionConfig? = null

    private val _isOverlayVisible = MutableStateFlow(false)
    override val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _unlockEvents = MutableSharedFlow<OverlayUnlock>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val unlockEvents: SharedFlow<OverlayUnlock> = _unlockEvents.asSharedFlow()

    override fun showFriction(config: FrictionConfig) {
        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Overlay permission missing; friction screen was not shown")
                _isOverlayVisible.value = false
                return@post
            }

            val existingConfig = currentConfig
            if (host != null &&
                existingConfig?.packageName == config.packageName &&
                existingConfig.mode == config.mode
            ) {
                return@post
            }

            if (host != null) {
                dismissInternal()
            }

            if (config.mode == FrictionMode.PROTECTION) {
                sessionStore.clear()
            }

            val session = if (config.mode == FrictionMode.STANDARD) {
                sessionStore.obtain(
                    target = config.packageName,
                    requiredText = context.getString(R.string.friction_discipline_text),
                )
            } else {
                null
            }

            val newHost = OverlayComposeHost(
                context = context,
                config = config,
                session = session,
                onInputChanged = sessionStore::updateInput,
                onUnlock = {
                    sessionStore.current?.let { activeSession ->
                        _unlockEvents.tryEmit(
                            OverlayUnlock(
                                target = activeSession.target,
                                timestampEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
            )

            applyImmersiveFlags(newHost.view)

            val added = runCatching {
                windowManager.addView(newHost.view, buildLayoutParams())
            }.isSuccess

            if (added) {
                newHost.show()
                host = newHost
                currentConfig = config
                _isOverlayVisible.value = true
            } else {
                Log.w(TAG, "WindowManager rejected the overlay view")
                newHost.hide()
                _isOverlayVisible.value = false
            }
        }
    }

    override fun hideFriction() {
        mainHandler.post { dismissInternal() }
    }

    private fun dismissInternal() {
        val activeHost = host
        host = null
        currentConfig = null
        sessionStore.clear()

        if (activeHost != null) {
            runCatching { windowManager.removeViewImmediate(activeHost.view) }
            activeHost.hide()
        }
        _isOverlayVisible.value = false
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

    @Suppress("DEPRECATION")
    private fun applyImmersiveFlags(view: View) {
        // Best effort: hides system bars while the friction screen is visible.
        // Home/Recents cannot be suppressed permanently without device-owner or
        // lock-task privileges; that limitation is documented.
        view.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private companion object {
        const val TAG = "OverlayManager"
    }
}
