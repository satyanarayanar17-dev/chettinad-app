package com.example.patient.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.AuthRepository
import com.example.data.repository.PatientAuthException
import com.example.domain.model.User
import com.example.domain.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val phone: String = "",
    val otpRequested: Boolean = false,
    val challengeId: String? = null,
    val profileRequired: Boolean = false,
    val demoCode: String? = null,
    val expiresAt: Long = 0,
    val resendAt: Long = 0,
    val error: String? = null,
    val user: User? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val user = authRepository.getStoredUser()
        if (user != null && user.role == Role.PATIENT) {
            _uiState.update { it.copy(user = user) }
        } else if (user != null) {
            // Wrong app, clear session
            logout()
        }
        
        viewModelScope.launch {
            authRepository.currentUser.collect { currentUser ->
                if (currentUser != null && currentUser.role != Role.PATIENT) {
                    logout()
                } else {
                    _uiState.update { it.copy(user = currentUser) }
                }
            }
        }
    }

    fun requestOtp(phone: String) {
        if (_uiState.value.isLoading) return
        val mobile = normalizePhone(phone)
        if (mobile == null) {
            _uiState.update { it.copy(error = "Enter a valid Indian mobile number.") }
            return
        }
        if (mobile == _uiState.value.phone && System.currentTimeMillis() < _uiState.value.resendAt) {
            _uiState.update { it.copy(error = "Please wait a minute before requesting a new code.") }; return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.requestPatientOtp(mobile).onSuccess { response ->
                _uiState.update {
                    it.copy(isLoading = false, phone = mobile,
                        otpRequested = response.sent, challengeId = response.challenge_id,
                        expiresAt = System.currentTimeMillis() + response.expires_in * 1000L,
                        resendAt = System.currentTimeMillis() + response.retry_after * 1000L,
                        demoCode = if (com.example.core.BuildConfig.DEBUG && com.example.core.BuildConfig.FLAVOR != "production") response.development_code else null,
                        profileRequired = false,
                        error = if (response.sent) null else "Code could not be sent. Please try again.")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = authMessage(error)) }
            }
        }
    }

    fun verifyOtp(code: String, profile: Map<String, String>? = null) {
        val state = _uiState.value
        if (state.isLoading || !state.otpRequested) return
        if (!Regex("^[0-9]{6}$").matches(code)) {
            _uiState.update { it.copy(error = "Enter the six-digit SMS code.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.verifyPatientOtp(state.phone, code, state.challengeId, profile)
                .onSuccess { user ->
                    if (user.role != Role.PATIENT) {
                        authRepository.logout()
                        _uiState.update { it.copy(isLoading = false, user = null,
                            error = "This account belongs to the Staff application. Access denied.") }
                    } else {
                        _uiState.value = AuthUiState(user = user)
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(isLoading = false,
                        profileRequired = it.profileRequired ||
                            (error as? PatientAuthException)?.code == "PROFILE_REQUIRED",
                        error = authMessage(error)) }
                }
        }
    }

    fun changePhone() {
        if (!_uiState.value.isLoading) _uiState.value = AuthUiState()
    }

    private fun authMessage(error: Throwable): String = when ((error as? PatientAuthException)?.code) {
        "PROFILE_REQUIRED" -> "Complete your details to register, then verify the same code."
        "OTP_INVALID", "OTP_EXPIRED", "OTP_CHALLENGE_MISMATCH" -> "Code is invalid or expired. Check it or request a new code."
        "OTP_WAIT", "HTTP_429", "RATE_LIMITED" -> "Please wait before requesting another code."
        "SMS_NOT_CONFIGURED", "SMS_DELIVERY_FAILED" -> "SMS is unavailable. Please contact hospital reception."
        "ACCOUNT_DISABLED" -> "Please contact hospital reception for help accessing your account."
        else -> "Unable to sign in. Check your connection and try again."
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState()
        }
    }
    
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun provideFactory(authRepository: AuthRepository): ViewModelProvider.Factory = 
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository) as T
                }
            }
    }
}

internal fun normalizePhone(input: String): String? {
    val compact = input.trim().replace(Regex("""[\s()-]"""), "")
    val local = when {
        compact.startsWith("+91") -> compact.drop(3)
        compact.length == 11 && compact.startsWith("0") -> compact.drop(1)
        compact.length == 12 && compact.startsWith("91") -> compact.drop(2)
        else -> compact
    }
    return if (Regex("^[6-9][0-9]{9}$").matches(local)) "+91$local" else null
}
