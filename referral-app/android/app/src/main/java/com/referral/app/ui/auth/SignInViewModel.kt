package com.referral.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val phoneDigits: String = "",
    val code: String = "",
    val otpSent: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val phoneValid get() = phoneDigits.length == 10 && phoneDigits.first() in '6'..'9'
    val e164 get() = "+91$phoneDigits"
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SignInUiState())
    val ui: StateFlow<SignInUiState> = _ui.asStateFlow()

    init {
        // This ViewModel outlives the sign-in screen (it's scoped to the activity), so wipe the
        // form once a session starts. After sign-out the screen then opens at the phone step.
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) _ui.value = SignInUiState()
            }
        }
    }

    fun onPhoneChange(v: String) = _ui.update { it.copy(phoneDigits = v.filter(Char::isDigit).take(10), error = null) }
    fun onCodeChange(v: String) = _ui.update { it.copy(code = v.filter(Char::isDigit).take(6), error = null) }
    fun editNumber() = _ui.update { it.copy(otpSent = false, code = "") }

    fun sendOtp() = run("Couldn't send the code. Check the number and try again.") {
        auth.sendPhoneOtp(_ui.value.e164)
        _ui.update { it.copy(otpSent = true) }
    }

    // On success the session status flips to Authenticated and AppRoot switches screens.
    fun verifyOtp() = run("That code didn't work. Try again or resend.") {
        auth.verifyPhoneOtp(_ui.value.e164, _ui.value.code)
    }

    fun onGoogleIdToken(idToken: String, rawNonce: String) = run("Google sign-in failed.") {
        auth.signInWithGoogle(idToken, rawNonce)
    }

    fun onGoogleError(message: String?) = _ui.update { it.copy(error = message ?: "Google sign-in was cancelled.") }

    private fun run(errorText: String, block: suspend () -> Unit) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { block() }
                .onFailure { _ui.update { s -> s.copy(error = errorText) } }
            _ui.update { it.copy(loading = false) }
        }
    }
}
