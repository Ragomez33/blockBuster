package com.blockbuster.core.database

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.database.dao.BlockLogDao
import com.blockbuster.core.database.dao.BlockRuleDao
import com.blockbuster.core.database.dao.BlocklistEntryDao
import com.blockbuster.core.database.datastore.BlockTimerDataSource
import com.blockbuster.core.database.datastore.FocusSessionRepositoryImpl
import com.blockbuster.core.database.datastore.LicenseIdentityStore
import com.blockbuster.core.database.datastore.PremiumRepositoryImpl
import com.blockbuster.core.database.preset.ProtectionPresetRepositoryImpl
import com.blockbuster.core.database.repository.BlockRulesRepositoryImpl
import com.blockbuster.core.database.repository.BlocklistRepositoryImpl
import com.blockbuster.core.database.support.FakeSubscriptionRemoteDataSource
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class RepositoryTestFixture {
    val database: AppDatabase
    val ruleDao: BlockRuleDao
    val logDao: BlockLogDao
    val blocklistDao: BlocklistEntryDao

    private val timerDataSource: BlockTimerDataSource
    val presetRepository: ProtectionPresetRepositoryImpl
    val remoteDataSource = FakeSubscriptionRemoteDataSource()
    val premiumRepository: PremiumRepositoryImpl
    val focusRepository: FocusSessionRepositoryImpl
    private val dataStoreScope: CoroutineScope
    private val tempDirectory: File

    init {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ruleDao = database.blockRuleDao()
        logDao = database.blockLogDao()
        blocklistDao = database.blocklistEntryDao()

        tempDirectory = Files.createTempDirectory("blockbuster-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDirectory, "test.preferences_pb") },
        )
        timerDataSource = BlockTimerDataSource(dataStore)
        presetRepository = ProtectionPresetRepositoryImpl(dataStore)
        premiumRepository = PremiumRepositoryImpl(
            dataStore = dataStore,
            remoteDataSource = remoteDataSource,
            identityStore = LicenseIdentityStore(dataStore),
            ioDispatcher = Dispatchers.Unconfined,
        )
        focusRepository = FocusSessionRepositoryImpl(dataStore)
    }

    fun blocklistRepository(dispatcher: CoroutineDispatcher): BlocklistRepositoryImpl =
        BlocklistRepositoryImpl(
            dao = blocklistDao,
            ioDispatcher = dispatcher,
        )

    fun repository(
        dispatcher: CoroutineDispatcher,
        ruleDaoOverride: BlockRuleDao = ruleDao,
        logDaoOverride: BlockLogDao = logDao,
    ): BlockRulesRepositoryImpl =
        BlockRulesRepositoryImpl(
            blockRuleDao = ruleDaoOverride,
            blockLogDao = logDaoOverride,
            timerDataSource = timerDataSource,
            presetRepository = presetRepository,
            ioDispatcher = dispatcher,
        )

    fun close() {
        runCatching { database.close() }
        dataStoreScope.cancel()
        tempDirectory.deleteRecursively()
    }
}
