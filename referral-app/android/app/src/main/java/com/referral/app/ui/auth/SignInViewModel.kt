package com.referral.app.ui.auth

import android.util.Log

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

/** Sign-in is Google only. */
data class SignInUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SignInUiState())
    val ui: StateFlow<SignInUiState> = _ui.asStateFlow()

    init {
        // This ViewModel outlives the sign-in screen (it's scoped to the activity), so clear any
        // error or spinner once a session starts; after sign-out the screen opens clean.
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) _ui.value = SignInUiState()
            }
        }
    }

    // On success the session status flips to Authenticated and AppRoot switches screens.
    fun onGoogleIdToken(idToken: String, rawNonce: String) = run("Google sign-in failed.", showReason = true) {
        auth.signInWithGoogle(idToken, rawNonce)
    }

    fun onGoogleError(message: String?) = _ui.update { it.copy(error = message ?: "Google sign-in was cancelled.") }

    /** [showReason]: append the server's reason (first line), e.g. a provider misconfiguration. Always logged. */
    private fun run(errorText: String, showReason: Boolean = false, block: suspend () -> Unit) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { block() }
                .onFailure { e ->
                    Log.e("SignIn", errorText, e)   // Logcat, filter by "SignIn"
                    val reason = e.message?.lineSequence()?.firstOrNull()?.take(160)
                    _ui.update { s -> s.copy(error = if (showReason && reason != null) "$errorText\n$reason" else errorText) }
                }
            _ui.update { it.copy(loading = false) }
        }
    }
}
