package com.blockbuster.app.ui.premium

import com.blockbuster.app.support.FakePremiumRepository
import com.blockbuster.core.common.model.IsPremiumState
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {

    private val repository = FakePremiumRepository()
    private lateinit var viewModel: SubscriptionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SubscriptionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun confirmedPaymentPublishesSuccess() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        repository.verifyResult = AppResult.Success(IsPremiumState(isPremium = true))

        viewModel.verifyPayment()

        assertEquals(PaymentVerificationStatus.CONFIRMED, viewModel.uiState.value.verificationStatus)
        assertFalse(viewModel.uiState.value.isCheckingPayment)
        assertNotNull(viewModel.uiState.value.messageRes)
    }

    @Test
    fun undetectedPaymentStaysFree() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        repository.verifyResult = AppResult.Success(IsPremiumState(isPremium = false))

        viewModel.verifyPayment()

        assertEquals(
            PaymentVerificationStatus.NOT_DETECTED,
            viewModel.uiState.value.verificationStatus,
        )
        assertFalse(viewModel.uiState.value.isPremium)
    }

    @Test
    fun offlineVerificationPublishesOffline() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        repository.verifyResult = AppResult.Failure(AppError.Storage("offline"))

        viewModel.verifyPayment()

        assertEquals(PaymentVerificationStatus.OFFLINE, viewModel.uiState.value.verificationStatus)
    }

    @Test
    fun invalidVerificationPublishesError() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        repository.verifyResult = AppResult.Failure(AppError.Unexpected("invalid"))

        viewModel.verifyPayment()

        assertEquals(PaymentVerificationStatus.ERROR, viewModel.uiState.value.verificationStatus)
    }

    @Test
    fun repeatedTapsDoNotDuplicateVerification() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        repository.verifyDelayMillis = 1_000L

        viewModel.verifyPayment()
        viewModel.verifyPayment()
        advanceTimeBy(1_001L)
        runCurrent()

        assertEquals(1, repository.verifyCalls)
    }

    @Test
    fun gatewayUnavailablePublishesMessage() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.onGatewayUnavailable()

        assertNotNull(viewModel.uiState.value.messageRes)
        viewModel.consumeMessage()
        assertEquals(null, viewModel.uiState.value.messageRes)
    }

    @Test
    fun premiumRepositoryStateIsReflected() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        repository.premium.value = IsPremiumState(
            isPremium = true,
            expiresAtEpochMillis = 123_000L,
            paymentProvider = "paypal",
        )

        assertTrue(viewModel.uiState.value.isPremium)
        assertEquals(123_000L, viewModel.uiState.value.expiresAtEpochMillis)
        assertEquals("paypal", viewModel.uiState.value.paymentProvider)
    }
}
