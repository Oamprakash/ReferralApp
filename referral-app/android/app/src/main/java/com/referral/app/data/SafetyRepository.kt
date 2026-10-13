package com.referral.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

enum class ReportReason(val apiValue: String, val label: String) {
    PAYMENT("payment_request", "Asked for money"),
    FAKE("fake", "Fake or misleading"),
    CLOSED("closed_role", "Role is already closed"),
    ABUSE("abuse", "Abusive or spam"),
    OTHER("other", "Something else"),
}

class ReportException(message: String) : Exception(message)

/** Reports, data export and account deletion. */
@Singleton
class SafetyRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** [targetType] is "job" or "user". */
    suspend fun report(targetType: String, targetId: String, reason: ReportReason, details: String) {
        val me = auth.currentUserId ?: error("Not signed in")
        try {
            supabase.from("reports").insert(NewReport(me, targetType, targetId, reason.apiValue, details.trim().ifBlank { null }))
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            throw when {
                "reports_reporter_id_target_type_target_id_key" in m || "duplicate key" in m ->
                    ReportException("You've already reported this. We'll review it.")
                "REPORT_LIMIT" in m -> ReportException("You've sent a lot of reports today. Try again tomorrow.")
                "REPORT_INVALID" in m -> ReportException(m.substringAfter("REPORT_INVALID: ").substringBefore("\n").trim())
                else -> e
            }
        }
    }

    /** Everything stored about you, as pretty-printed JSON. */
    suspend fun exportMyData(): String {
        val raw = supabase.postgrest.rpc("export_my_data").data
        val pretty = Json { prettyPrint = true }
        return pretty.encodeToString(JsonObject.serializer(), json.parseToJsonElement(raw) as JsonObject)
    }

    /** Deletes the account on the server. The caller should clear the local session afterwards. */
    suspend fun deleteAccount() {
        val response = supabase.functions.invoke(function = "delete-account", body = DeleteBody("DELETE"))
        val status = (json.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.get("status")?.jsonPrimitive?.content
        if (status != "deleted") error("Account deletion failed")
    }

    suspend fun amIAdmin(): Boolean =
        supabase.postgrest.rpc("am_i_admin").data.trim() == "true"

    @Serializable
    private data class NewReport(
        @SerialName("reporter_id") val reporterId: String,
        @SerialName("target_type") val targetType: String,
        @SerialName("target_id") val targetId: String,
        val reason: String,
        val details: String?,
    )

    @Serializable private data class DeleteBody(val confirm: String)
}
