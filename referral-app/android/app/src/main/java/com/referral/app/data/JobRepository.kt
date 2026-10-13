package com.referral.app.data

import com.referral.app.data.model.AppliedJobId
import com.referral.app.data.model.CompanyOption
import com.referral.app.data.model.Job
import com.referral.app.data.model.JobDetail
import com.referral.app.data.model.JobMatch
import kotlinx.serialization.builtins.ListSerializer
import com.referral.app.data.model.MyApplication
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class FeedFilter(
    val query: String = "",
    val workModes: Set<String> = emptySet(),     // "onsite", "hybrid", "remote"
    val companyId: String? = null,
    val fitsYears: Int? = null,                  // set = only jobs whose range includes these years
) {
    val isActive get() = query.isNotBlank() || workModes.isNotEmpty() || companyId != null || fitsYears != null
}

sealed class ApplyException(message: String) : Exception(message) {
    class WeeklyLimit : ApplyException("You've used all 10 applications for this week.")
    class AlreadyApplied : ApplyException("You've already applied to this job.")
    class InvalidAnswers : ApplyException("Answer every screening question.")
    class NotAllowed : ApplyException("This job isn't open, or your profile needs a resume.")
    class AlreadyReapplied : ApplyException("You've already reapplied to this job once.")
}

@Singleton
class JobRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun uid(): String = auth.currentUserId ?: error("Not signed in")

    /** Active posts confirmed within 14 days, freshest first, narrowed by [filter]. */
    suspend fun feed(filter: FeedFilter = FeedFilter(), limit: Long = 50): List<Job> {
        val cutoff = Instant.now().minus(FRESHNESS_DAYS, ChronoUnit.DAYS).toString()
        return supabase.from("jobs")
            .select(Columns.raw(FEED_COLUMNS)) {
                filter {
                    eq("status", "active")
                    gt("last_confirmed_at", cutoff)
                    if (filter.query.isNotBlank()) {
                        ilike("title", "%${filter.query.trim().replace("%", "").replace("_", "")}%")
                    }
                    if (filter.workModes.isNotEmpty()) isIn("work_mode", filter.workModes.toList())
                    filter.companyId?.let { eq("company_id", it) }
                    filter.fitsYears?.let { years ->
                        lte("exp_min", years)
                        gte("exp_max", years)
                    }
                }
                order("last_confirmed_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<Job>()
    }

    /** Open jobs ranked against my saved profile (skills + years), best first. Excludes ones I applied to. */
    suspend fun bestMatches(limit: Int = 30): List<JobMatch> =
        json.decodeFromString(
            ListSerializer(JobMatch.serializer()),
            supabase.postgrest.rpc("best_matches", buildJsonObject { put("p_limit", limit) }).data,
        )

    suspend fun companies(): List<CompanyOption> =
        supabase.from("companies").select(Columns.list("id", "name")) {
            filter { eq("status", "approved") }
            order("name", Order.ASCENDING)
        }.decodeList<CompanyOption>()

    /** Job ids with a live application from the signed-in user (feed "Applied" badge). Withdrawn ones don't count. */
    suspend fun myAppliedJobIds(): Set<String> =
        supabase.from("applications").select(Columns.list("job_id")) {
            filter {
                eq("seeker_id", uid())
                neq("status", "withdrawn")
            }
        }.decodeList<AppliedJobId>().map { it.jobId }.toSet()

    /** Null when the job doesn't exist or isn't visible to this user. */
    suspend fun jobDetail(jobId: String): JobDetail? {
        val result = supabase.postgrest.rpc("get_job_detail", buildJsonObject { put("p_job", jobId) })
        val body = result.data.trim()
        if (body.isEmpty() || body == "null") return null
        return json.decodeFromString(JobDetail.serializer(), body)
    }

    suspend fun apply(jobId: String, answers: List<String>): String {
        try {
            val created = supabase.from("applications")
                .insert(NewApplication(jobId, uid(), answers.map { it.trim() })) { select(Columns.list("status")) }
                .decodeSingle<JsonObject>()
            return created["status"]?.jsonPrimitive?.content ?: "applied"
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            throw when {
                "WEEKLY_LIMIT" in m -> ApplyException.WeeklyLimit()
                "ANSWERS_INVALID" in m -> ApplyException.InvalidAnswers()
                "applications_job_id_seeker_id_key" in m || "duplicate key" in m -> ApplyException.AlreadyApplied()
                "row-level security" in m -> ApplyException.NotAllowed()
                else -> e
            }
        }
    }

    /** Second (and last) try after withdrawing. Returns "applied" or "waitlisted". */
    suspend fun reapply(applicationId: String, answers: List<String>): String {
        val body = try {
            supabase.postgrest.rpc("reapply_application", buildJsonObject {
                put("p_application", applicationId)
                put("p_answers", kotlinx.serialization.json.JsonArray(answers.map { kotlinx.serialization.json.JsonPrimitive(it.trim()) }))
            }).data
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            throw when {
                "WEEKLY_LIMIT" in m -> ApplyException.WeeklyLimit()
                "ANSWERS_INVALID" in m -> ApplyException.InvalidAnswers()
                else -> e
            }
        }
        val status = (json.parseToJsonElement(body) as? JsonObject)?.get("status")?.jsonPrimitive?.content
        return when (status) {
            "applied", "waitlisted" -> status
            "already_reapplied" -> throw ApplyException.AlreadyReapplied()
            else -> throw ApplyException.NotAllowed()     // job_closed, profile_incomplete, not_found, not_withdrawn
        }
    }

    suspend fun myApplications(): List<MyApplication> =
        supabase.from("applications")
            .select(Columns.raw("id, job_id, status, created_at, reapplied_at, jobs(title, status, companies(name))")) {
                filter { eq("seeker_id", uid()) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<MyApplication>()

    /** Returns true if withdrawn; false if it was already reviewed or not found. */
    suspend fun withdraw(applicationId: String): Boolean {
        val result = supabase.postgrest.rpc(
            "withdraw_application",
            buildJsonObject { put("p_application", applicationId) },
        )
        return json.parseToJsonElement(result.data).let {
            (it as? JsonObject)?.get("status")?.jsonPrimitive?.content == "withdrawn"
        }
    }

    @Serializable
    private data class NewApplication(
        @kotlinx.serialization.SerialName("job_id") val jobId: String,
        @kotlinx.serialization.SerialName("seeker_id") val seekerId: String,
        val answers: List<String>,
    )

    companion object {
        const val FRESHNESS_DAYS = 14L
        private const val FEED_COLUMNS =
            "id, title, team, location, exp_min, exp_max, work_mode, salary_range, last_confirmed_at, companies(name)"
    }
}
