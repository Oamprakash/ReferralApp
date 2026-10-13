package com.referral.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Links this device's FCM token to the signed-in user. Every call is a no-op until
 * app/google-services.json is added (Firebase isn't initialised without it).
 */
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) {
    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    /** Call after sign-in. */
    suspend fun register() {
        if (!isAvailable) return
        val token = currentToken() ?: return
        sendToServer(token)
    }

    /** Called by PushService when FCM rotates the token. */
    suspend fun sendToServer(token: String) {
        runCatching {
            supabase.postgrest.rpc("register_device_token", buildJsonObject {
                put("p_token", token)
                put("p_platform", "android")
            })
        }.onFailure { Log.w(TAG, "register_device_token failed", it) }
    }

    /** Call before sign-out, while the session is still valid, so the next user doesn't get these pushes. */
    suspend fun unregister() {
        if (!isAvailable) return
        val token = currentToken() ?: return
        runCatching {
            supabase.postgrest.rpc("unregister_device_token", buildJsonObject { put("p_token", token) })
        }.onFailure { Log.w(TAG, "unregister_device_token failed", it) }
    }

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            cont.resume(if (task.isSuccessful) task.result else null.also { Log.w(TAG, "FCM token failed", task.exception) })
        }
    }

    companion object {
        private const val TAG = "Push"
        const val CHANNEL_ID = "updates"

        /** Same id as the server sends in android.notification.channel_id. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Application updates", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Shortlists, referrals and new applicants" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
