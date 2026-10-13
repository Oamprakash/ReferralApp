package com.referral.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.AuthRepository
import com.referral.app.data.ProfileRepository
import com.referral.app.push.PushRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data object SignedOut : RootState
    /** [needsProfile] decides whether the signed-in graph starts at the profile form or home. */
    data class SignedIn(val needsProfile: Boolean) : RootState
    /** An admin suspended this account. The database already refuses posting and applying. */
    data class Suspended(val reason: String?) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val push: PushRegistrar,
) : ViewModel() {

    private val _state = MutableStateFlow<RootState>(RootState.Loading)
    val state: StateFlow<RootState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        // Only re-route on the transition into a session, not on token refreshes
                        if (_state.value !is RootState.SignedIn && _state.value !is RootState.Suspended) {
                            val profile = runCatching { profileRepository.myProfile() }.getOrNull()
                            if (profile?.isBanned == true) {
                                _state.value = RootState.Suspended(profile.banReason)
                            } else {
                                _state.value = RootState.SignedIn(needsProfile = profile?.isComplete != true)
                                viewModelScope.launch { push.register() }   // no-op until Firebase is configured
                            }
                        }
                    }
                    is SessionStatus.NotAuthenticated -> _state.value = RootState.SignedOut
                    else -> Unit // Initializing / RefreshFailure: keep the current screen
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { push.unregister() }   // while the session is still valid
            runCatching { authRepository.signOut() }
        }
    }
}
