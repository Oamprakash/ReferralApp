package com.referral.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val city: String? = null,
    val skills: List<String> = emptyList(),
    @SerialName("experience_years") val experienceYears: Int? = null,
    @SerialName("resume_path") val resumePath: String? = null,
    @SerialName("banned_at") val bannedAt: String? = null,
    @SerialName("ban_reason") val banReason: String? = null,
) {
    val isBanned: Boolean get() = bannedAt != null

    /** A seeker needs these before they can apply (the database enforces the resume). */
    val isComplete: Boolean
        get() = !fullName.isNullOrBlank() && experienceYears != null && resumePath != null
}

/** Columns a user may edit on their own profile. */
@Serializable
data class ProfileUpdate(
    @SerialName("full_name") val fullName: String,
    val city: String,
    val skills: List<String>,
    @SerialName("experience_years") val experienceYears: Int,
)
