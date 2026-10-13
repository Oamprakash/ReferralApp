package com.referral.app.ui.profile

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.AuthRepository
import com.referral.app.data.ProfileRepository
import com.referral.app.data.SafetyRepository
import com.referral.app.data.model.ProfileUpdate
import com.referral.app.resume.ParsedResume
import com.referral.app.resume.ResumeParser
import com.referral.app.resume.ResumeTextExtractor
import com.referral.app.resume.Skills
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val fullName: String = "",
    val city: String = "Chennai",
    val skillsText: String = "",          // comma-separated in the form
    val experienceText: String = "",
    /** Contact for referral forms; shown to a referrer only after they shortlist you. */
    val contactEmail: String = "",
    val contactPhone: String = "",        // 10 digits, +91 is shown as a prefix
    /** What the resume filled in, e.g. "name, 6 yrs, 9 skills", or why it couldn't. */
    val autofillNote: String? = null,
    val hasResume: Boolean = false,
    val uploading: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /** Export JSON waiting for the user to pick where to save it. */
    val pendingExport: String? = null,
    val exporting: Boolean = false,
    val deleting: Boolean = false,
    val privacyMessage: String? = null,
) {
    val experienceYears get() = experienceText.toIntOrNull()
    val emailProblem: String?
        get() = if (contactEmail.isBlank() || EMAIL.matches(contactEmail.trim())) null else "Check the email address"
    val phoneProblem: String?
        get() = if (contactPhone.isEmpty() || PHONE.matches(contactPhone)) null else "10-digit mobile number"
    val canSave get() = fullName.isNotBlank() && experienceYears in 0..50 && hasResume && !saving && !uploading &&
        emailProblem == null && phoneProblem == null

    private companion object {
        val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
        val PHONE = Regex("""[6-9]\d{9}""")
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val safety: SafetyRepository,
    private val auth: AuthRepository,
    private val resumeText: ResumeTextExtractor,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val resolver: ContentResolver = context.contentResolver
    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { profiles.myProfile() }
                .onSuccess { p ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            fullName = p?.fullName.orEmpty(),
                            city = p?.city ?: "Chennai",
                            skillsText = p?.skills.orEmpty().joinToString(", "),
                            experienceText = p?.experienceYears?.toString().orEmpty(),
                            hasResume = p?.resumePath != null,
                        )
                    }
                    cityWasSaved = p?.city != null
                    runCatching { profiles.myContact() }.onSuccess { c ->
                        _ui.update {
                            it.copy(contactEmail = c?.email.orEmpty(), contactPhone = c?.phone?.removePrefix("+91").orEmpty())
                        }
                    }
                }
                .onFailure { _ui.update { it.copy(loading = false, error = "Couldn't load your profile.") } }
        }
    }

    fun onName(v: String) = _ui.update { it.copy(fullName = v.take(80)) }
    fun onCity(v: String) = _ui.update { it.copy(city = v.take(40)) }
    fun onSkills(v: String) = _ui.update { it.copy(skillsText = v.take(300)) }
    fun onExperience(v: String) = _ui.update { it.copy(experienceText = v.filter(Char::isDigit).take(2)) }
    fun onContactEmail(v: String) = _ui.update { it.copy(contactEmail = v.trim().take(254)) }
    fun onContactPhone(v: String) = _ui.update { it.copy(contactPhone = v.filter(Char::isDigit).takeLast(12).let { d ->
        if (d.length > 10 && d.startsWith("91")) d.drop(2) else d.take(10) }) }

    private var cityWasSaved = false

    /**
     * Fill the form from the resume. Empty fields are filled; skills are merged; experience goes up
     * if the resume says more (people gain years). Nothing is saved until the user taps Save.
     */
    private fun autofill(r: ParsedResume) {
        if (r.isEmpty) {
            _ui.update { it.copy(autofillNote = "Couldn't read text from this PDF (it may be a scanned image). Fill in the details yourself.") }
            return
        }
        val filled = mutableListOf<String>()
        _ui.update { s ->
            var n = s
            if (n.fullName.isBlank() && r.name != null) { n = n.copy(fullName = r.name); filled += "name" }
            r.experienceYears?.let { y ->
                if (n.experienceYears == null || y > n.experienceYears!!) { n = n.copy(experienceText = y.toString()); filled += "$y yrs experience" }
            }
            if (!cityWasSaved && r.city != null && r.city != n.city) { n = n.copy(city = r.city); filled += "city" }
            if (n.contactEmail.isBlank() && r.email != null) { n = n.copy(contactEmail = r.email); filled += "email" }
            if (n.contactPhone.isBlank() && r.phone != null) { n = n.copy(contactPhone = r.phone.removePrefix("+91")); filled += "phone" }
            val current = Skills.normalizeList(n.skillsText)
            val added = r.skills.filter { sk -> current.none { it.equals(sk, ignoreCase = true) } }
            if (added.isNotEmpty()) {
                n = n.copy(skillsText = (current + added).take(20).joinToString(", "))
                filled += "${added.size} skill${if (added.size == 1) "" else "s"}"
            }
            n.copy(autofillNote = if (filled.isEmpty()) "Your resume matches what's already here."
                else "Filled from your resume: ${filled.joinToString(", ")}. Check them before saving.")
        }
    }

    fun onResumePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _ui.update { it.copy(uploading = true, error = null) }
            runCatching {
                if (resolver.getType(uri) != "application/pdf") error("Please pick a PDF file.")
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read that file.")
                }
                if (bytes.size > ProfileRepository.MAX_RESUME_BYTES) error("Resume must be 5 MB or smaller.")
                profiles.uploadResume(bytes)
                bytes
            }.onSuccess { bytes ->
                _ui.update { it.copy(uploading = false, hasResume = true) }
                // Parsing is a bonus: a PDF we can't read still uploads fine
                runCatching { resumeText.extract(bytes).let { t -> withContext(Dispatchers.Default) { ResumeParser.parse(t) } } }
                    .onSuccess(::autofill)
                    .onFailure { e ->
                        Log.w("Profile", "Resume parse failed", e)
                        _ui.update { it.copy(autofillNote = "Resume uploaded. We couldn't read its text, so fill in the details yourself.") }
                    }
            }.onFailure { e ->
                _ui.update { it.copy(uploading = false, error = e.message ?: "Upload failed.") }
            }
        }
    }

    fun save() {
        val s = _ui.value
        if (!s.canSave) return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            runCatching {
                profiles.updateProfile(
                    ProfileUpdate(
                        fullName = s.fullName.trim(),
                        city = s.city.trim(),
                        skills = Skills.normalizeList(s.skillsText),   // same spelling as job posts, so matches score
                        experienceYears = s.experienceYears!!,
                    ),
                )
                profiles.saveContact(
                    email = s.contactEmail.trim().ifBlank { null }?.lowercase(),
                    phone = s.contactPhone.ifBlank { null }?.let { "+91$it" },
                )
            }.onSuccess {
                _ui.update { it.copy(saving = false, saved = true) }
            }.onFailure {
                _ui.update { it.copy(saving = false, error = "Couldn't save. Try again.") }
            }
        }
    }

    fun onSavedHandled() = _ui.update { it.copy(saved = false) }

    // ---------- Privacy (DPDP): export and delete ----------

    /** Step 1: fetch the export. The screen then asks where to save it. */
    fun prepareExport() {
        if (_ui.value.exporting) return
        viewModelScope.launch {
            _ui.update { it.copy(exporting = true, privacyMessage = null) }
            runCatching { safety.exportMyData() }
                .onSuccess { json -> _ui.update { it.copy(exporting = false, pendingExport = json) } }
                .onFailure { _ui.update { it.copy(exporting = false, privacyMessage = "Couldn't prepare your data. Try again.") } }
        }
    }

    /** Step 2: write it to the file the user picked (null = they cancelled). */
    fun saveExport(uri: Uri?) {
        val json = _ui.value.pendingExport ?: return
        _ui.update { it.copy(pendingExport = null) }
        if (uri == null) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("Couldn't open file")
                }
            }.onSuccess { _ui.update { it.copy(privacyMessage = "Your data was saved.") } }
                .onFailure { _ui.update { it.copy(privacyMessage = "Couldn't save the file.") } }
        }
    }

    /** Deletes the account for good, then forgets the session; the app returns to sign-in. */
    fun deleteAccount() {
        if (_ui.value.deleting) return
        viewModelScope.launch {
            _ui.update { it.copy(deleting = true, privacyMessage = null) }
            runCatching { safety.deleteAccount() }
                .onSuccess { auth.clearLocalSession() }
                .onFailure {
                    _ui.update { it.copy(deleting = false, privacyMessage = "Couldn't delete your account. Check your connection and try again.") }
                }
        }
    }
}
