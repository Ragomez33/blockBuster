package com.blockbuster.core.database.protection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProtectionStateRepositoryTest {

    private lateinit var mainDataStore: DataStore<Preferences>
    private lateinit var bootDataStore: DataStore<Preferences>
    private lateinit var repository: ProtectionStateRepositoryImpl
    private lateinit var mainScope: CoroutineScope
    private lateinit var bootScope: CoroutineScope
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("protection-state-test").toFile()
        mainScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mainDataStore = PreferenceDataStoreFactory.create(
            scope = mainScope,
            produceFile = { File(directory, "main.preferences_pb") },
        )
        bootDataStore = PreferenceDataStoreFactory.create(
            scope = bootScope,
            produceFile = { File(directory, "boot.preferences_pb") },
        )
        repository = ProtectionStateRepositoryImpl(mainDataStore, bootDataStore)
    }

    @After
    fun tearDown() {
        mainScope.cancel()
        bootScope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun writesBothMainAndBootState() = runBlocking {
        repository.setProtectionEnabled(true, "test")

        assertTrue(repository.isProtectionEnabled())
        assertTrue(repository.isBootProtectionEnabled())
        assertTrue(repository.observeProtectionEnabled().first())
    }

    @Test
    fun canDisableProtectionInBothStores() = runBlocking {
        repository.setProtectionEnabled(true, "test")
        repository.setProtectionEnabled(false, "test")

        assertFalse(repository.isProtectionEnabled())
        assertFalse(repository.isBootProtectionEnabled())
    }

    @Test
    fun bootStateCanBeWrittenIndependently() = runBlocking {
        repository.setBootProtectionEnabled(true)

        assertTrue(repository.isBootProtectionEnabled())
        assertFalse(repository.isProtectionEnabled())
    }
}
