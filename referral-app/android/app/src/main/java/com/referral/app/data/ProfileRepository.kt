package com.referral.app.data

import com.referral.app.data.model.Profile
import com.referral.app.data.model.ProfileUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) {
    private fun uid(): String = auth.currentUserId ?: error("Not signed in")

    /** The row is created by a database trigger at sign-up, so it normally exists. */
    suspend fun myProfile(): Profile? =
        supabase.from(PROFILES).select {
            filter { eq("id", uid()) }
        }.decodeSingleOrNull<Profile>()

    suspend fun updateProfile(update: ProfileUpdate) {
        supabase.from(PROFILES).update(update) {
            filter { eq("id", uid()) }
        }
    }

    /**
     * Uploads the resume to the private bucket at "<uid>/resume.pdf" and records the path.
     * Storage policies only allow writes inside the caller's own folder.
     */
    suspend fun uploadResume(pdfBytes: ByteArray): String {
        require(pdfBytes.size <= MAX_RESUME_BYTES) { "Resume must be 5 MB or smaller" }
        val path = "${uid()}/resume.pdf"
        supabase.storage.from(RESUMES_BUCKET).upload(path, pdfBytes) { upsert = true }
        supabase.from(PROFILES).update(ResumePath(path)) {
            filter { eq("id", uid()) }
        }
        return path
    }

    /** Email/phone for referral forms. Only the seeker can read it; referrers see it after shortlisting. */
    suspend fun myContact(): ContactDetails? =
        supabase.from(CONTACTS).select { filter { eq("user_id", uid()) } }.decodeSingleOrNull<ContactDetails>()

    suspend fun saveContact(email: String?, phone: String?) {
        if (email == null && phone == null) {
            supabase.from(CONTACTS).delete { filter { eq("user_id", uid()) } }
        } else {
            supabase.from(CONTACTS).upsert(ContactDetails(uid(), email, phone))
        }
    }

    @Serializable
    data class ContactDetails(
        @kotlinx.serialization.SerialName("user_id") val userId: String,
        val email: String? = null,
        val phone: String? = null,
    )

    @Serializable
    private data class ResumePath(@kotlinx.serialization.SerialName("resume_path") val resumePath: String)

    companion object {
        private const val PROFILES = "profiles"
        private const val CONTACTS = "profile_contacts"
        const val RESUMES_BUCKET = "resumes"
        const val MAX_RESUME_BYTES = 5 * 1024 * 1024
    }
}
