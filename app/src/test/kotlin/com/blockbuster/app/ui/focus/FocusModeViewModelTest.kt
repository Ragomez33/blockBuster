package com.blockbuster.app.ui.focus

import androidx.lifecycle.viewModelScope
import com.blockbuster.app.support.FakeFocusSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusModeViewModelTest {

    private val repository = FakeFocusSessionRepository()
    private lateinit var viewModel: FocusModeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = FocusModeViewModel(repository)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun startPauseResumeStopFlow() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.start(10, 2)
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.pause()
        assertTrue(viewModel.uiState.value.isPaused)

        viewModel.resume()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.stop()
        assertFalse(viewModel.uiState.value.isRunning)
        assertFalse(viewModel.uiState.value.isPaused)
        assertEquals(0L, viewModel.uiState.value.remainingMillis)
    }

    @Test
    fun startFailureExposesError() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        repository.failNextStart = true

        viewModel.start(10, 2)

        assertTrue(viewModel.uiState.value.actionError != null)
    }
}
