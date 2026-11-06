package com.blockbuster.core.accessibility.di

import com.blockbuster.core.accessibility.AccessibilityEngineImpl
import com.blockbuster.core.accessibility.settings.DefaultSettingsInterceptor
import com.blockbuster.core.accessibility.settings.SettingsInterceptor
import com.blockbuster.core.accessibility.target.DefaultTargetExtractor
import com.blockbuster.core.accessibility.target.TargetExtractor
import com.blockbuster.core.common.contract.AccessibilityEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityModule {

    @Binds
    @Singleton
    abstract fun bindAccessibilityEngine(
        implementation: AccessibilityEngineImpl,
    ): AccessibilityEngine

    @Binds
    @Singleton
    abstract fun bindTargetExtractor(
        implementation: DefaultTargetExtractor,
    ): TargetExtractor

    @Binds
    @Singleton
    abstract fun bindSettingsInterceptor(
        implementation: DefaultSettingsInterceptor,
    ): SettingsInterceptor
}
