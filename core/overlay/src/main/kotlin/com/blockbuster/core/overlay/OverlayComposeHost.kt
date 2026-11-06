package com.blockbuster.core.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.blockbuster.core.common.contract.FrictionConfig
import com.blockbuster.core.common.contract.FrictionMode

/**
 * Hosts the Compose friction screen inside a WindowManager overlay.
 *
 * The owners are single-use. The session itself is owned by OverlayManager so
 * it can survive recreation of this view.
 */
internal class OverlayComposeHost(
    context: Context,
    config: FrictionConfig,
    session: FrictionSession?,
    onInputChanged: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    private val owners = OverlayOwners().apply { onCreate() }

    init {
        clearClipboard(context)
    }

    val view: ComposeView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(owners)
        setViewTreeSavedStateRegistryOwner(owners)
        setViewTreeViewModelStoreOwner(owners)
        setContent {
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owners) {
                if (config.mode == FrictionMode.PROTECTION) {
                    ProtectionBlockingScreen(config)
                } else {
                    FrictionScreen(
                        session = requireNotNull(session) {
                            "A standard friction screen requires a FrictionSession"
                        },
                        onInputChanged = onInputChanged,
                        onUnlock = onUnlock,
                    )
                }
            }
        }
    }

    internal val isDestroyed: Boolean
        get() = owners.isDestroyed

    fun show() {
        owners.onStart()
        owners.onResume()
    }

    fun hide() {
        if (owners.isDestroyed) return
        owners.onPause()
        owners.onStop()
        view.disposeComposition()
        view.removeAllViews()
        owners.onDestroy()
    }

    private fun clearClipboard(context: Context) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}

private class OverlayOwners :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()

    private var created = false
    var isDestroyed: Boolean = false
        private set

    fun onCreate() {
        if (created) return
        created = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        if (!created || isDestroyed) return
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        if (!created || isDestroyed) return
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        if (isDestroyed) return
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
    }

    fun onStop() {
        if (isDestroyed) return
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    fun onDestroy() {
        if (isDestroyed) return
        isDestroyed = true
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
