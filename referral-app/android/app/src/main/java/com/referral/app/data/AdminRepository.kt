package com.referral.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ReportGroup(
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val label: String? = null,
    @SerialName("target_status") val targetStatus: String? = null,
    @SerialName("report_count") val reportCount: Int,
    val reasons: List<String> = emptyList(),
    val details: List<String>? = null,
    @SerialName("last_at") val lastAt: String,
) {
    val key get() = "$targetType:$targetId"
}

@Serializable
data class DomainRequest(
    val domain: String,
    @SerialName("request_count") val requestCount: Int,
    @SerialName("last_requested_at") val lastRequestedAt: String,
)

/** A verified employee an admin can post a job for. */
@Serializable
data class AdminReferrer(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("company_id") val companyId: String,
    @SerialName("company_name") val companyName: String,
    @SerialName("work_email") val workEmail: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("active_posts") val activePosts: Int = 0,
) {
    val name: String get() = fullName?.ifBlank { null } ?: workEmail.substringBefore('@')
}

/** Admin-only calls. The database refuses every one of them for non-admins (NOT_ADMIN). */
@Singleton
class AdminRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun openReports(): List<ReportGroup> =
        json.decodeFromString(ListSerializer(ReportGroup.serializer()), supabase.postgrest.rpc("admin_open_reports").data)

    /** [action] is "dismiss" or "uphold" (uphold closes a post / bans a user). */
    suspend fun resolve(group: ReportGroup, action: String) {
        supabase.postgrest.rpc("admin_resolve_reports", buildJsonObject {
            put("p_target_type", group.targetType)
            put("p_target_id", group.targetId)
            put("p_action", action)
        })
    }

    suspend fun domainRequests(): List<DomainRequest> =
        json.decodeFromString(ListSerializer(DomainRequest.serializer()), supabase.postgrest.rpc("admin_domain_requests").data)

    suspend fun approveDomain(domain: String, companyName: String) {
        supabase.postgrest.rpc("admin_approve_domain", buildJsonObject {
            put("p_domain", domain)
            put("p_company_name", companyName.trim())
        })
    }

    /** Verified employees (unexpired), for "Post for a referrer". */
    suspend fun referrers(): List<AdminReferrer> =
        json.decodeFromString(ListSerializer(AdminReferrer.serializer()), supabase.postgrest.rpc("admin_referrers").data)

    suspend fun rejectDomain(domain: String) {
        supabase.postgrest.rpc("admin_reject_domain", buildJsonObject { put("p_domain", domain) })
    }
}
