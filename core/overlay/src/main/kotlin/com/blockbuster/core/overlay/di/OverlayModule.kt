package com.blockbuster.core.overlay.di

import com.blockbuster.core.common.contract.OverlayController
import com.blockbuster.core.overlay.OverlayManager
import com.blockbuster.core.overlay.OverlayScope
import com.blockbuster.core.overlay.matching.BlockRuleMatcher
import com.blockbuster.core.overlay.matching.DefaultBlockRuleMatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OverlayModule {

    @Binds
    @Singleton
    abstract fun bindOverlayController(
        implementation: OverlayManager,
    ): OverlayController

    @Binds
    @Singleton
    abstract fun bindBlockRuleMatcher(
        implementation: DefaultBlockRuleMatcher,
    ): BlockRuleMatcher
}

@Module
@InstallIn(SingletonComponent::class)
object OverlayScopeModule {

    @Provides
    @Singleton
    @OverlayScope
    fun provideOverlayScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
