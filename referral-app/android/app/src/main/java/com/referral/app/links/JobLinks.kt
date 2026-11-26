package com.referral.app.links

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shareable job links: https://oamprakash.github.io/ReferralApp/job.html?id=<job id>
 *
 * - With the app installed, Android opens them straight in WarmIntro (App Links, verified by
 *   /.well-known/assetlinks.json on oamprakash.github.io).
 * - Without it, the web page (docs/job.html) offers "Open in app" and the Play Store.
 */
object JobLinks {
    private const val BASE = "https://oamprakash.github.io/ReferralApp/job.html"
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun url(jobId: String): String = "$BASE?id=$jobId"

    fun isJobId(s: String): Boolean = UUID.matches(s)

    /** The job id from an incoming link, or null if it isn't one of ours. */
    fun jobIdFrom(uri: Uri?): String? {
        if (uri == null || uri.host != "oamprakash.github.io") return null
        if (uri.path?.startsWith("/ReferralApp/job") != true) return null
        return uri.getQueryParameter("id")?.takeIf { UUID.matches(it) }
    }

    /** Opens the system share sheet. */
    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share job"))
    }

    /** Text for a seeker sharing someone else's post. */
    fun seekerText(title: String, company: String?, location: String, jobId: String) =
        buildString {
            append(title)
            company?.let { append(" at ").append(it) }
            append(" (").append(location).append(")\n")
            append("Posted by a verified employee who can refer you. See it on WarmIntro:\n")
            append(url(jobId))
        }

    /** Text for a referrer sharing their own post. */
    fun referrerText(title: String, company: String?, location: String, jobId: String) =
        buildString {
            append("We're hiring: ").append(title)
            company?.let { append(" at ").append(it) }
            append(" (").append(location).append(")\n")
            append("I can refer you. Apply for a referral on WarmIntro:\n")
            append(url(jobId))
        }
}

/** A job link that opened the app, waiting for the signed-in screens to show it. */
object PendingJobLink {
    private val _jobId = MutableStateFlow<String?>(null)
    val jobId: StateFlow<String?> = _jobId.asStateFlow()
    fun set(id: String?) { if (id != null) _jobId.value = id }
    fun consume() { _jobId.value = null }
}
