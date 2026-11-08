package com.blockbuster.app.ui.blocklist

import com.blockbuster.app.support.FakeBlocklistRepository
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlocklistViewModelTest {

    private val repository = FakeBlocklistRepository()
    private lateinit var viewModel: BlocklistViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = BlocklistViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addEntryAppearsInSelectedList() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.addEntry("com.example.app")

        assertEquals(1, viewModel.uiState.value.entries.size)
        assertEquals("com.example.app", viewModel.uiState.value.entries.first().value)
        assertEquals(BlocklistType.BLACKLIST, viewModel.uiState.value.selectedListType)
        assertEquals(BlocklistTargetType.APP, viewModel.uiState.value.selectedTargetType)
    }

    @Test
    fun duplicateErrorIsExposed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        repository.failNextAdd = true

        viewModel.addEntry("com.example.app")

        assertTrue(viewModel.uiState.value.actionError != null)
    }

    @Test
    fun deleteRemovesEntry() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        viewModel.addEntry("com.example.app")
        val entry = viewModel.uiState.value.entries.first()

        viewModel.deleteEntry(entry)

        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }

    @Test
    fun filterChangeUpdatesSelectedTypes() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.selectListType(BlocklistType.WHITELIST)
        viewModel.selectTargetType(BlocklistTargetType.WEBSITE)

        assertEquals(BlocklistType.WHITELIST, viewModel.uiState.value.selectedListType)
        assertEquals(BlocklistTargetType.WEBSITE, viewModel.uiState.value.selectedTargetType)
    }
}
