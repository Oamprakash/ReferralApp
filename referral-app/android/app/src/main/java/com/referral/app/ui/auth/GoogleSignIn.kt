package com.referral.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/** A raw nonce goes to Supabase; its SHA-256 goes to Google, so the token can't be replayed. */
data class Nonce(val raw: String, val hashed: String) {
    companion object {
        fun create(): Nonce {
            val raw = UUID.randomUUID().toString()
            val hashed = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return Nonce(raw, hashed)
        }
    }
}

/**
 * Shows Google's full account chooser and returns an ID token.
 * Uses the "Sign in with Google" button flow, not the One Tap sheet: it always opens, lists every account
 * on the phone, offers "Add account", and isn't hidden for a while after the user dismisses it.
 * [activityContext] must be an Activity context; the chooser is a UI flow.
 */
suspend fun requestGoogleIdToken(activityContext: Context, webClientId: String, hashedNonce: String): String {
    check(webClientId.isNotBlank()) {
        "This build has no Google client ID. Add GOOGLE_WEB_CLIENT_ID to android/local.properties, then Clean and rebuild."
    }
    val option = GetSignInWithGoogleOption.Builder(webClientId)
        .setNonce(hashedNonce)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(activityContext)
        .getCredential(activityContext, request)
        .credential

    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    error("Unexpected credential type: ${credential.type}")
}

/** Plain-language message for a failed account chooser (before anything reaches Supabase). */
fun googleErrorText(e: Throwable): String = when (e) {
    is androidx.credentials.exceptions.GetCredentialCancellationException -> "Google sign-in was cancelled."
    is androidx.credentials.exceptions.NoCredentialException ->
        "No Google account found on this phone. Add one in Settings → Accounts, then try again."
    is IllegalStateException -> e.message ?: "Google sign-in isn't set up in this build."
    else -> "Google sign-in didn't work. Check your connection and that Google Play services is up to date.\n${e.message.orEmpty().take(120)}"
}
