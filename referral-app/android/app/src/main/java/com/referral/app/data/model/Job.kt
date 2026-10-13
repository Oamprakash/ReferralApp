package com.referral.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.OffsetDateTime

/** Feed card. */
@Serializable
data class Job(
    val id: String,
    val title: String,
    val team: String? = null,
    val location: String,
    @SerialName("exp_min") val expMin: Int,
    @SerialName("exp_max") val expMax: Int,
    @SerialName("work_mode") val workMode: String,
    @SerialName("salary_range") val salaryRange: String? = null,
    @SerialName("last_confirmed_at") val lastConfirmedAt: String,
    /** Embedded via select("..., companies(name)"); PostgREST names it after the table. */
    @SerialName("companies") val company: CompanyName? = null,
) {
    val companyName: String get() = company?.name ?: "Unknown company"
    val confirmedLabel: String get() = confirmedLabel(lastConfirmedAt)
    val experienceLabel: String get() = experienceLabel(expMin, expMax)
    val workModeLabel: String get() = workMode.replaceFirstChar { it.uppercase() }
}

@Serializable
data class CompanyName(val name: String)

@Serializable
data class CompanyOption(val id: String, val name: String)

/** Everything the detail screen needs, from the get_job_detail() database function. */
@Serializable
data class JobDetail(
    val id: String,
    val title: String,
    val team: String? = null,
    val location: String,
    @SerialName("exp_min") val expMin: Int,
    @SerialName("exp_max") val expMax: Int,
    @SerialName("work_mode") val workMode: String,
    @SerialName("salary_range") val salaryRange: String? = null,
    @SerialName("official_url") val officialUrl: String? = null,
    @SerialName("job_ref") val jobRef: String? = null,
    val questions: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val description: String? = null,
    val status: String,
    @SerialName("last_confirmed_at") val lastConfirmedAt: String,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("poster_first_name") val posterFirstName: String? = null,
    @SerialName("poster_referred") val posterReferred: Int = 0,
    @SerialName("poster_interviews") val posterInterviews: Int = 0,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("applicant_cap") val applicantCap: Int,
    @SerialName("spots_left") val spotsLeft: Int,
    @SerialName("waitlist_count") val waitlistCount: Int,
    @SerialName("my_application_id") val myApplicationId: String? = null,
    @SerialName("my_application_status") val myApplicationStatus: String? = null,
    @SerialName("has_reapplied") val hasReapplied: Boolean = false,
    @SerialName("can_reapply") val canReapply: Boolean = false,
    @SerialName("profile_complete") val profileComplete: Boolean = false,
    @SerialName("applications_this_week") val applicationsThisWeek: Int = 0,
) {
    val confirmedLabel: String get() = confirmedLabel(lastConfirmedAt)
    val experienceLabel: String get() = experienceLabel(expMin, expMax)
    val workModeLabel: String get() = workMode.replaceFirstChar { it.uppercase() }
    val isOpen: Boolean get() = status == "active"
    val weeklyLimitReached: Boolean get() = applicationsThisWeek >= WEEKLY_LIMIT

    companion object { const val WEEKLY_LIMIT = 10 }
}

/** A row of "My applications". */
@Serializable
data class MyApplication(
    val id: String,
    @SerialName("job_id") val jobId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("reapplied_at") val reappliedAt: String? = null,
    @SerialName("jobs") val job: AppliedJob? = null,
) {
    val canWithdraw: Boolean get() = status == "applied" || status == "waitlisted"
    val canReapply: Boolean get() = status == "withdrawn" && reappliedAt == null && job?.status == "active"
}

@Serializable
data class AppliedJob(
    val title: String,
    val status: String,
    @SerialName("companies") val company: CompanyName? = null,
)

@Serializable
data class AppliedJobId(@SerialName("job_id") val jobId: String)

/** Human wording for application statuses, shared by the detail and My applications screens. */
fun applicationStatusLabel(status: String?): String = when (status) {
    "applied" -> "Applied · waiting for review"
    "waitlisted" -> "Waitlisted · you'll move up if a spot opens"
    "shortlisted" -> "Shortlisted"
    "declined" -> "Not taken forward"
    "referred" -> "Referred to the company"
    "interview" -> "Interviewing"
    "offer" -> "Offer received"
    "not_selected" -> "Not selected"
    "withdrawn" -> "Withdrawn"
    else -> status.orEmpty()
}

internal fun confirmedLabel(lastConfirmedAt: String): String {
    val days = runCatching {
        Duration.between(OffsetDateTime.parse(lastConfirmedAt), OffsetDateTime.now()).toDays()
    }.getOrDefault(0)
    return when (days) {
        0L -> "Confirmed active today"
        1L -> "Confirmed active yesterday"
        else -> "Confirmed active $days days ago"
    }
}

internal fun experienceLabel(min: Int, max: Int): String =
    if (min == 0 && max <= 1) "Fresher" else "$min–$max yrs"

/** One row from best_matches(): an open job ranked against the seeker's profile. */
@Serializable
data class JobMatch(
    val id: String,
    val title: String,
    val team: String? = null,
    val location: String,
    @SerialName("exp_min") val expMin: Int,
    @SerialName("exp_max") val expMax: Int,
    @SerialName("work_mode") val workMode: String,
    @SerialName("salary_range") val salaryRange: String? = null,
    @SerialName("last_confirmed_at") val lastConfirmedAt: String,
    @SerialName("company_name") val companyName: String,
    /** 0–100: up to 50 for experience fit, up to 50 for the share of the job's skills you have. */
    val score: Int,
    val have: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
) {
    val experienceLabel: String get() = experienceLabel(expMin, expMax)
    val workModeLabel: String get() = workMode.replaceFirstChar { it.uppercase() }
    val confirmedLabel: String get() = confirmedLabel(lastConfirmedAt)

    /** Skills half of the score, as in match_score: share of the post's skills you have (25 if none listed). */
    val skillPoints: Int
        get() = (have.size + missing.size).let { total -> if (total == 0) 25 else Math.round(50.0 * have.size / total).toInt() }

    /** Experience half, as in match_score: 50 in range, 30 within 1 year, 10 within 2, else 0. */
    fun experiencePoints(years: Int?): Int = when {
        years == null -> 0
        years in expMin..expMax -> 50
        years in (expMin - 1)..(expMax + 1) -> 30
        years in (expMin - 2)..(expMax + 2) -> 10
        else -> 0
    }
}
