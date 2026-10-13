package com.referral.app.ui.referrer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.ActivePostLimitException
import com.referral.app.data.AuthRepository
import com.referral.app.data.ReferrerRepository
import com.referral.app.data.model.MyVerification
import com.referral.app.data.model.NewJob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WorkMode(val apiValue: String, val label: String) {
    ONSITE("onsite", "Onsite"), HYBRID("hybrid", "Hybrid"), REMOTE("remote", "Remote")
}

data class PostJobForm(
    val title: String = "",
    val team: String = "",
    val location: String = "Chennai",
    val expMin: String = "",
    val expMax: String = "",
    val workMode: WorkMode = WorkMode.HYBRID,
    val salary: String = "",
    val officialUrl: String = "",
    val jobRef: String = "",
    val questions: List<String> = listOf("", "", ""),
    val skills: String = "",              // comma-separated; used for the match score
    val description: String = "",
    val cap: String = "30",
) {
    private val min get() = expMin.toIntOrNull()
    private val max get() = expMax.toIntOrNull()
    private val capInt get() = cap.toIntOrNull()
    val skillList: List<String>
        get() = com.referral.app.resume.Skills.normalizeList(skills, limit = 50)   // same spelling as resumes, so scores match

    /** First problem with the form, or null when it can be posted. Mirrors the database checks. */
    val problem: String?
        get() = when {
            title.isBlank() -> "Add a job title."
            location.isBlank() -> "Add a location."
            min == null || max == null -> "Add the experience range in years."
            min!! > max!! -> "Minimum experience can't be more than maximum."
            max!! > 50 -> "Maximum experience looks too high."
            officialUrl.isBlank() && jobRef.isBlank() -> "Add the official job link or the job ID."
            officialUrl.isNotBlank() && !officialUrl.trim().startsWith("https://") -> "The job link should start with https://"
            capInt == null || capInt!! !in 1..50 -> "Applicant limit must be between 1 and 50."
            skillList.size > 10 -> "List at most 10 skills."
            else -> null
        }

    fun toNewJob(companyId: String, referrerId: String) = NewJob(
        companyId = companyId,
        referrerId = referrerId,
        title = title.trim(),
        team = team.trim().ifBlank { null },
        location = location.trim(),
        expMin = min!!,
        expMax = max!!,
        workMode = workMode.apiValue,
        salaryRange = salary.trim().ifBlank { null },
        officialUrl = officialUrl.trim().ifBlank { null },
        jobRef = jobRef.trim().ifBlank { null },
        questions = questions.map { it.trim() }.filter { it.isNotEmpty() },
        skills = skillList,
        applicantCap = capInt!!,
        description = description.trim().ifBlank { null },
    )

    /** Known skills mentioned in the description that aren't in the skills field yet (tap to add). */
    val suggestedSkills: List<String>
        get() = com.referral.app.resume.Skills.find(description, limit = 12)
            .filter { s -> skillList.none { it.equals(s, ignoreCase = true) } }
            .take(6)

    fun withSkill(skill: String): PostJobForm =
        copy(skills = (skillList + skill).joinToString(", "))
}

data class PostJobUiState(
    val loading: Boolean = true,
    val verification: MyVerification? = null,
    val form: PostJobForm = PostJobForm(),
    val showErrors: Boolean = false,
    val posting: Boolean = false,
    val posted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PostJobViewModel @Inject constructor(
    private val repo: ReferrerRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PostJobUiState())
    val ui: StateFlow<PostJobUiState> = _ui.asStateFlow()

    init { reload() }

    /** Called on start and again after returning from verification. */
    fun reload() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { repo.myVerification() }
                .onSuccess { v -> _ui.update { it.copy(loading = false, verification = v) } }
                .onFailure { e ->
                    Log.e("PostJob", "Verification lookup failed", e)
                    _ui.update { it.copy(loading = false, error = "Couldn't check your verification.") }
                }
        }
    }

    fun edit(change: (PostJobForm) -> PostJobForm) = _ui.update { it.copy(form = change(it.form), error = null) }

    fun post() {
        val s = _ui.value
        val v = s.verification ?: return
        if (s.form.problem != null) {
            _ui.update { it.copy(showErrors = true) }
            return
        }
        val uid = auth.currentUserId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(posting = true, error = null) }
            runCatching { repo.postJob(s.form.toNewJob(v.companyId, uid)) }
                .onSuccess { _ui.update { it.copy(posting = false, posted = true) } }
                .onFailure { e ->
                    Log.e("PostJob", "Post failed", e)
                    _ui.update {
                        it.copy(posting = false, error = when (e) {
                            is ActivePostLimitException ->
                                "You already have 5 active posts. Close one in My posts first."
                            else -> "Couldn't post the job. Try again."
                        })
                    }
                }
        }
    }
}
