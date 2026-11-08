package com.blockbuster.core.network.di

import com.blockbuster.core.common.contract.SubscriptionRemoteDataSource
import com.blockbuster.core.network.subscription.SupabaseSubscriptionRemoteDataSource
import com.blockbuster.core.network.supabase.SupabaseClientProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = SupabaseClientProvider.create()

    @Provides
    @Singleton
    fun provideSubscriptionRemoteDataSource(
        client: SupabaseClient,
    ): SubscriptionRemoteDataSource = SupabaseSubscriptionRemoteDataSource(client)
}
