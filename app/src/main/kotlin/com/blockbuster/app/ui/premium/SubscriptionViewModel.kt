package com.blockbuster.app.ui.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.app.R
import com.blockbuster.core.common.contract.PremiumRepository
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PaymentVerificationStatus {
    IDLE,
    CHECKING,
    CONFIRMED,
    NOT_DETECTED,
    OFFLINE,
    ERROR,
}

private data class VerificationUiState(
    val isChecking: Boolean = false,
    val status: PaymentVerificationStatus = PaymentVerificationStatus.IDLE,
    val messageRes: Int? = null,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val repository: PremiumRepository,
) : ViewModel() {

    private val verification = MutableStateFlow(VerificationUiState())

    val uiState: StateFlow<SubscriptionUiState> = combine(
        repository.observePremiumState(),
        verification,
    ) { premium, verify ->
        SubscriptionUiState(
            isPremium = premium.isPremium,
            expiresAtEpochMillis = premium.expiresAtEpochMillis,
            paymentProvider = premium.paymentProvider,
            isCheckingPayment = verify.isChecking,
            verificationStatus = verify.status,
            messageRes = verify.messageRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubscriptionUiState(),
    )

    fun verifyPayment() {
        if (verification.value.isChecking) return

        viewModelScope.launch {
            verification.update {
                it.copy(
                    isChecking = true,
                    status = PaymentVerificationStatus.CHECKING,
                    messageRes = null,
                )
            }

            val result = repository.verifyPayment()
            verification.update { current ->
                when (result) {
                    is AppResult.Success -> {
                        val active = result.value.isActive()
                        current.copy(
                            isChecking = false,
                            status = if (active) {
                                PaymentVerificationStatus.CONFIRMED
                            } else {
                                PaymentVerificationStatus.NOT_DETECTED
                            },
                            messageRes = if (active) {
                                R.string.subscription_verify_success
                            } else {
                                R.string.subscription_verify_not_detected
                            },
                        )
                    }

                    is AppResult.Failure -> {
                        val offline = result.error is AppError.Storage
                        current.copy(
                            isChecking = false,
                            status = if (offline) {
                                PaymentVerificationStatus.OFFLINE
                            } else {
                                PaymentVerificationStatus.ERROR
                            },
                            messageRes = if (offline) {
                                R.string.subscription_verify_offline
                            } else {
                                R.string.subscription_verify_error
                            },
                        )
                    }
                }
            }
        }
    }

    fun onGatewayUnavailable() {
        verification.update {
            it.copy(messageRes = R.string.subscription_gateway_unavailable)
        }
    }

    fun consumeMessage() {
        verification.update { it.copy(messageRes = null) }
    }
}

data class SubscriptionUiState(
    val isPremium: Boolean = false,
    val expiresAtEpochMillis: Long? = null,
    val paymentProvider: String? = null,
    val isCheckingPayment: Boolean = false,
    val verificationStatus: PaymentVerificationStatus = PaymentVerificationStatus.IDLE,
    val messageRes: Int? = null,
)
