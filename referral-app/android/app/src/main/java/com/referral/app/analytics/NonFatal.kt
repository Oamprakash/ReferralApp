package com.referral.app.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException

/**
 * Sends a caught error to Crashlytics (as a non-fatal), so users see a friendly message while we
 * still get the details. Does nothing without Firebase, for cancellations, or for being offline.
 */
object NonFatal {
    fun record(e: Throwable) {
        if (e is CancellationException || isNetwork(e)) return
        runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
    }

    /** True for "couldn't reach the server" failures (no connection, DNS, timeout). */
    fun isNetwork(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is java.net.UnknownHostException || t is java.net.SocketTimeoutException ||
                t is java.net.ConnectException || t is java.io.InterruptedIOException ||
                t::class.simpleName?.contains("HttpRequestTimeout") == true
            ) return true
            t = t.cause
        }
        return false
    }
}
