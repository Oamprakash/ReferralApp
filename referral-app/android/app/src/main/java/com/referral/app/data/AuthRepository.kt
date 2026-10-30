package com.referral.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    val sessionStatus: StateFlow<SessionStatus> = supabase.auth.sessionStatus

    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    /** Exchanges a Google ID token (from Credential Manager) for a Supabase session. */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String) {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    suspend fun signOut() = supabase.auth.signOut()

    /** After the account is deleted on the server: forget the session on this phone only. */
    suspend fun clearLocalSession() {
        runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
            .onFailure { supabase.auth.clearSession() }
    }
}
