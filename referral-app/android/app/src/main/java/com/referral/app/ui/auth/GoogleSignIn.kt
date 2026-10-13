package com.referral.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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
 * Shows the Google account picker and returns an ID token.
 * [activityContext] must be an Activity context; the picker is a UI flow.
 */
suspend fun requestGoogleIdToken(activityContext: Context, webClientId: String, hashedNonce: String): String {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
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
