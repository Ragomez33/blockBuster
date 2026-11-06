package com.blockbuster.core.database.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.blockbuster.core.common.contract.BlockRulesRepository
import com.blockbuster.core.common.contract.ProtectionStateRepository
import com.blockbuster.core.database.AppDatabase
import com.blockbuster.core.database.dao.BlockLogDao
import com.blockbuster.core.database.dao.BlockRuleDao
import com.blockbuster.core.database.datastore.blockBusterDataStore
import com.blockbuster.core.database.protection.BootDataStore
import com.blockbuster.core.database.protection.ProtectionStateRepositoryImpl
import com.blockbuster.core.database.repository.BlockRulesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "blockbuster.db",
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideBlockRuleDao(database: AppDatabase): BlockRuleDao =
        database.blockRuleDao()

    @Provides
    fun provideBlockLogDao(database: AppDatabase): BlockLogDao =
        database.blockLogDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.blockBusterDataStore

    @Provides
    @Singleton
    @BootDataStore
    fun provideBootDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = {
            context.createDeviceProtectedStorageContext()
                .preferencesDataStoreFile("blockbuster_boot_preferences")
        },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBlockRulesRepository(
        implementation: BlockRulesRepositoryImpl,
    ): BlockRulesRepository

    @Binds
    @Singleton
    abstract fun bindProtectionStateRepository(
        implementation: ProtectionStateRepositoryImpl,
    ): ProtectionStateRepository
}
