package com.referral.app.data

import com.referral.app.data.model.Applicant
import com.referral.app.data.model.MyPost
import com.referral.app.data.model.PostSummary
import com.referral.app.data.model.ReferrerStats
import com.referral.app.data.model.MyVerification
import com.referral.app.data.model.NewJob
import com.referral.app.data.model.OtpResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a referrer already has 5 active posts (enforced by a database trigger). */
class ActivePostLimitException : Exception("You can have at most 5 active posts")
class UnderReviewException : Exception("This post is held for moderator review")
class SuspendedException : Exception("This account is suspended")

/** Thrown when a status change skips a step (enforced by a database trigger). */
class StatusTransitionException : Exception("That step isn't allowed from the current status")

@Singleton
class ReferrerRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun uid(): String = auth.currentUserId ?: error("Not signed in")

    /** The caller's unexpired verification, or null if they still need to verify. */
    suspend fun myVerification(): MyVerification? =
        supabase.from("referrer_verifications")
            .select(Columns.raw("company_id, work_email, expires_at, companies(name)")) {
                filter {
                    eq("user_id", uid())
                    gt("expires_at", Instant.now().toString())
                }
                limit(1)
            }
            .decodeSingleOrNull<MyVerification>()

    suspend fun requestWorkEmailOtp(email: String): OtpResponse =
        invoke("request-work-email-otp", EmailBody(email.trim()))

    suspend fun verifyWorkEmailOtp(code: String): OtpResponse =
        invoke("verify-work-email-otp", CodeBody(code))

    private suspend inline fun <reified T : Any> invoke(function: String, body: T): OtpResponse {
        val response = supabase.functions.invoke(function = function, body = body)
        return json.decodeFromString(OtpResponse.serializer(), response.bodyAsText())
    }

    suspend fun postJob(job: NewJob) {
        try {
            supabase.from("jobs").insert(job)
        } catch (e: Exception) {
            if (e.message?.contains("ACTIVE_POST_LIMIT") == true) throw ActivePostLimitException()
            throw e
        }
    }

    suspend fun myPosts(): List<MyPost> =
        supabase.from("jobs")
            .select(Columns.raw("id, title, status, location, last_confirmed_at, applicant_cap, hidden_reason")) {
                filter { eq("referrer_id", uid()) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<MyPost>()

    /** "Still hiring": refreshes freshness and re-activates a hidden post. */
    suspend fun confirmStillHiring(postId: String) {
        try {
            supabase.from("jobs").update(ConfirmUpdate("active", Instant.now().toString())) {
                filter { eq("id", postId) }
            }
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            when {
                "ACTIVE_POST_LIMIT" in m -> throw ActivePostLimitException()
                "UNDER_REVIEW" in m -> throw UnderReviewException()
                "BANNED" in m -> throw SuspendedException()
            }
            throw e
        }
    }

    suspend fun closePost(postId: String) {
        supabase.from("jobs").update(StatusUpdate("closed")) {
            filter { eq("id", postId) }
        }
    }

    // ---------- Review loop (Sprint 4) ----------

    suspend fun postsSummary(): Map<String, PostSummary> =
        supabase.postgrest.rpc("my_posts_summary").decodeList<PostSummary>().associateBy { it.jobId }

    suspend fun myStats(): ReferrerStats =
        json.decodeFromString(
            ReferrerStats.serializer(),
            supabase.postgrest.rpc("referrer_stats", buildJsonObject { put("p_user", uid()) }).data,
        )

    /** Applicants for one of my posts, best match first. Withdrawn applicants are left out. */
    suspend fun applicants(jobId: String): List<Applicant> =
        supabase.from("applications")
            .select(Columns.raw(
                "id, seeker_id, status, match_score, answers, created_at, " +
                    "profiles(full_name, experience_years, skills, city, resume_path)",
            )) {
                filter {
                    eq("job_id", jobId)
                    neq("status", "withdrawn")
                }
                order("match_score", Order.DESCENDING, nullsFirst = false)
                order("created_at", Order.ASCENDING)
            }
            .decodeList<Applicant>()

    suspend fun setApplicationStatus(applicationId: String, status: String) {
        try {
            supabase.from("applications").update(StatusUpdate(status)) {
                filter { eq("id", applicationId) }
            }
        } catch (e: Exception) {
            if (e.message?.contains("STATUS_TRANSITION") == true) throw StatusTransitionException()
            throw e
        }
    }

    /** Short-lived link to an applicant's resume PDF. Storage rules allow it only for your own applicants. */
    suspend fun resumeUrl(path: String): String =
        supabase.storage.from("resumes").createSignedUrl(path, expiresIn = 5.minutes)

    /** Email/phone for the referral form; null until you've shortlisted them, or if they didn't add any. */
    suspend fun applicantContact(applicationId: String): ApplicantContact? {
        val raw = supabase.postgrest.rpc("applicant_contact", buildJsonObject { put("p_application", applicationId) }).data.trim()
        if (raw.isEmpty() || raw == "null") return null
        return json.decodeFromString(ApplicantContact.serializer(), raw).takeIf { it.email != null || it.phone != null }
    }

    @Serializable data class ApplicantContact(val email: String? = null, val phone: String? = null)

    @Serializable private data class EmailBody(val email: String)
    @Serializable private data class CodeBody(val code: String)
    @Serializable private data class StatusUpdate(val status: String)
    @Serializable private data class ConfirmUpdate(
        val status: String,
        @SerialName("last_confirmed_at") val lastConfirmedAt: String,
    )
}
