package com.example.dreamescape_ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        fetchInitialData: () -> Unit = {}
    ): MainViewModel {
        return MainViewModel(
            fetchInitialData = fetchInitialData,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `initial state hides dialog and reports api unavailable`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isApiAvailable)
        assertFalse(state.showApiUnavailableDialog)
        assertNull(state.errorMessage)
    }

    @Test
    fun `checkBackendAvailability marks api available on success`() = runTest {
        val viewModel = createViewModel { /* successful request, no exception */ }

        viewModel.checkBackendAvailability()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isApiAvailable)
        assertFalse(state.showApiUnavailableDialog)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `checkBackendAvailability shows error dialog when api is not accessible`() = runTest {
        val viewModel = createViewModel {
            throw IOException("Failed to connect to /10.0.2.2:8000")
        }

        viewModel.checkBackendAvailability()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isApiAvailable)
        assertTrue(state.showApiUnavailableDialog)
        assertFalse(state.isLoading)
        assertEquals("Failed to connect to /10.0.2.2:8000", state.errorMessage)
    }

    @Test
    fun `checkBackendAvailability uses fallback message when exception has none`() = runTest {
        val viewModel = createViewModel {
            throw RuntimeException()
        }

        viewModel.checkBackendAvailability()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showApiUnavailableDialog)
        assertEquals("Backend API is not accessible", state.errorMessage)
    }

    @Test
    fun `dismissApiUnavailableDialog hides the dialog`() = runTest {
        val viewModel = createViewModel {
            throw IOException("Connection refused")
        }

        viewModel.checkBackendAvailability()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showApiUnavailableDialog)

        viewModel.dismissApiUnavailableDialog()

        assertFalse(viewModel.uiState.value.showApiUnavailableDialog)
    }

    @Test
    fun `retry after failure clears error and marks api available`() = runTest {
        var shouldFail = true
        val viewModel = createViewModel {
            if (shouldFail) throw IOException("Connection refused")
        }

        viewModel.checkBackendAvailability()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showApiUnavailableDialog)

        shouldFail = false
        viewModel.checkBackendAvailability()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isApiAvailable)
        assertFalse(state.showApiUnavailableDialog)
        assertNull(state.errorMessage)
    }
}
