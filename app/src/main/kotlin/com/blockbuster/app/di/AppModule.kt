package com.blockbuster.app.di

import com.blockbuster.core.common.event.BlockEventBus
import com.blockbuster.core.common.event.MutableBlockEventBus
import com.blockbuster.core.common.event.MutableProtectionEventBus
import com.blockbuster.core.common.event.ProtectionEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Single in-process reactive bus shared by accessibility and overlay.
     */
    @Provides
    @Singleton
    fun provideBlockEventBus(): BlockEventBus = MutableBlockEventBus()

    @Provides
    @Singleton
    fun provideProtectionEventBus(): ProtectionEventBus = MutableProtectionEventBus()
}
