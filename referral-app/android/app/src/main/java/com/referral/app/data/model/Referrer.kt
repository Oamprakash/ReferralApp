package com.referral.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.OffsetDateTime

/** The signed-in user's own verification row (RLS lets them read only theirs). */
@Serializable
data class MyVerification(
    @SerialName("company_id") val companyId: String,
    @SerialName("work_email") val workEmail: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("companies") val company: CompanyName? = null,
) {
    val companyName: String get() = company?.name ?: "your company"
    val daysLeft: Long
        get() = runCatching {
            Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(expiresAt)).toDays()
        }.getOrDefault(0)
}

/**
 * Response from both work-email Edge Functions. Business outcomes arrive in [status];
 * [error] is set only for real failures (unauthorized, server_error).
 */
@Serializable
data class OtpResponse(
    val status: String? = null,
    val error: String? = null,
    @SerialName("company_name") val companyName: String? = null,
    val email: String? = null,
    val domain: String? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
    @SerialName("attempts_left") val attemptsLeft: Int? = null,
    @SerialName("closed_posts") val closedPosts: Int? = null,
    /** Only present when the server runs with OTP_DEV_MODE=true. */
    @SerialName("dev_code") val devCode: String? = null,
)

@Serializable
data class NewJob(
    @SerialName("company_id") val companyId: String,
    @SerialName("referrer_id") val referrerId: String,
    val title: String,
    val team: String?,
    val location: String,
    @SerialName("exp_min") val expMin: Int,
    @SerialName("exp_max") val expMax: Int,
    @SerialName("work_mode") val workMode: String,
    @SerialName("salary_range") val salaryRange: String?,
    @SerialName("official_url") val officialUrl: String?,
    @SerialName("job_ref") val jobRef: String?,
    val questions: List<String>,
    val skills: List<String>,
    @SerialName("applicant_cap") val applicantCap: Int,
    val description: String? = null,
)

@Serializable
data class MyPost(
    val id: String,
    val title: String,
    val status: String,
    val location: String,
    @SerialName("last_confirmed_at") val lastConfirmedAt: String,
    @SerialName("applicant_cap") val applicantCap: Int,
    /** Why it's hidden or closed: stale, verification, reports (held for review) or admin (removed). */
    @SerialName("hidden_reason") val hiddenReason: String? = null,
) {
    val isActive get() = status == "active"
    /** Moderation holds: the referrer can't bring these back themselves. */
    val isModerated get() = hiddenReason == "reports" || hiddenReason == "admin"
    val confirmedDaysAgo: Long
        get() = runCatching {
            Duration.between(OffsetDateTime.parse(lastConfirmedAt), OffsetDateTime.now()).toDays()
        }.getOrDefault(0)
    /** Active but older than 14 days: hidden from the feed until confirmed. */
    val isStale get() = isActive && confirmedDaysAgo >= 14
}

/** Per-post counts from my_posts_summary(). */
@Serializable
data class PostSummary(
    @SerialName("job_id") val jobId: String,
    val applicants: Int,
    @SerialName("new_count") val newCount: Int,
    val shortlisted: Int,
    val referred: Int,
    val waitlisted: Int,
)

@Serializable
data class ReferrerStats(val referred: Int = 0, val interviews: Int = 0, val offers: Int = 0)

/** One applicant on the referrer's review screen. */
@Serializable
data class Applicant(
    val id: String,
    @SerialName("seeker_id") val seekerId: String? = null,
    val status: String,
    @SerialName("match_score") val matchScore: Int? = null,
    val answers: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("profiles") val profile: ApplicantProfile? = null,
) {
    /** The next steps a referrer may take; mirrors the database's status rules. */
    val nextSteps: List<Pair<String, String>>
        get() = when (status) {
            "applied" -> listOf("shortlisted" to "Shortlist", "declined" to "Decline")
            "shortlisted" -> listOf("referred" to "Mark referred", "declined" to "Decline")
            "referred" -> listOf("interview" to "Got interview", "not_selected" to "Not selected")
            "interview" -> listOf("offer" to "Got offer", "not_selected" to "Not selected")
            else -> emptyList()
        }
}

@Serializable
data class ApplicantProfile(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("experience_years") val experienceYears: Int? = null,
    val skills: List<String> = emptyList(),
    val city: String? = null,
    @SerialName("resume_path") val resumePath: String? = null,
)
