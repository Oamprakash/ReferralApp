package com.referral.app.ui.referrer

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.referral.app.data.JobRepository
import com.referral.app.data.ReferrerRepository
import com.referral.app.data.ReportException
import com.referral.app.data.ReportReason
import com.referral.app.data.SafetyRepository
import com.referral.app.data.StatusTransitionException
import com.referral.app.data.model.Applicant
import com.referral.app.ui.navigation.ApplicantsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ApplicantTab(val label: String, val statuses: Set<String>) {
    TO_REVIEW("To review", setOf("applied")),
    IN_PROGRESS("In progress", setOf("shortlisted", "referred", "interview")),
    DONE("Done", setOf("offer", "not_selected", "declined")),
    WAITLIST("Waitlist", setOf("waitlisted")),
}

data class ApplicantsUiState(
    val loading: Boolean = true,
    val jobTitle: String = "",
    val questions: List<String> = emptyList(),
    val applicants: List<Applicant> = emptyList(),
    val tab: ApplicantTab = ApplicantTab.TO_REVIEW,
    val busyId: String? = null,
    val error: String? = null,
    val message: String? = null,
    /** Contact details being shown in a dialog. */
    val contact: ShownContact? = null,
) {
    val visible: List<Applicant> get() = applicants.filter { it.status in tab.statuses }
    fun count(t: ApplicantTab) = applicants.count { it.status in t.statuses }
}

data class ShownContact(val name: String, val email: String?, val phone: String?)

/** Statuses where the referrer may see contact details (mirrors applicant_contact in the database). */
val CONTACT_STATUSES = setOf("shortlisted", "referred", "interview", "offer")

@HiltViewModel
class ApplicantsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ReferrerRepository,
    private val jobs: JobRepository,
    private val safety: SafetyRepository,
) : ViewModel() {

    private val jobId = savedStateHandle.toRoute<ApplicantsRoute>().jobId

    private val _ui = MutableStateFlow(ApplicantsUiState())
    val ui: StateFlow<ApplicantsUiState> = _ui.asStateFlow()

    /** One-off: a signed resume URL to open in the browser. */
    private val _openUrl = Channel<String>(Channel.BUFFERED)
    val openUrl = _openUrl.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching { jobs.jobDetail(jobId) }.onSuccess { d ->
                _ui.update { it.copy(jobTitle = d?.title.orEmpty(), questions = d?.questions.orEmpty()) }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { repo.applicants(jobId) }
                .onSuccess { list -> _ui.update { it.copy(loading = false, applicants = list, error = null) } }
                .onFailure { e ->
                    Log.e(TAG, "Load failed", e)
                    _ui.update { it.copy(loading = false, error = "Couldn't load applicants.") }
                }
        }
    }

    fun selectTab(tab: ApplicantTab) = _ui.update { it.copy(tab = tab) }

    fun setStatus(applicationId: String, status: String) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = applicationId, error = null) }
            runCatching { repo.setApplicationStatus(applicationId, status) }
                .onFailure { e ->
                    Log.e(TAG, "Status change failed", e)
                    _ui.update {
                        it.copy(error = if (e is StatusTransitionException)
                            "That step isn't available any more. Refreshing the list."
                        else "Couldn't update. Try again.")
                    }
                }
            _ui.update { it.copy(busyId = null) }
            load()
        }
    }

    fun openResume(path: String?) {
        if (path == null) {
            _ui.update { it.copy(error = "This applicant has no resume on file.") }
            return
        }
        viewModelScope.launch {
            runCatching { repo.resumeUrl(path) }
                .onSuccess { _openUrl.send(it) }
                .onFailure { e ->
                    Log.e(TAG, "Resume link failed", e)
                    _ui.update { it.copy(error = "Couldn't open the resume.") }
                }
        }
    }

    fun report(seekerId: String, reason: ReportReason, details: String) {
        viewModelScope.launch {
            _ui.update { it.copy(error = null, message = null) }
            runCatching { safety.report("user", seekerId, reason, details) }
                .onSuccess { _ui.update { it.copy(message = "Thanks. Moderators will review this applicant.") } }
                .onFailure { e ->
                    Log.e(TAG, "Report failed", e)
                    _ui.update { it.copy(error = (e as? ReportException)?.message ?: "Couldn't send the report.") }
                }
        }
    }

    fun showContact(a: Applicant) {
        viewModelScope.launch {
            _ui.update { it.copy(error = null, message = null) }
            runCatching { repo.applicantContact(a.id) }
                .onSuccess { c ->
                    val name = a.profile?.fullName?.ifBlank { null } ?: "This applicant"
                    if (c == null) _ui.update { it.copy(message = "$name hasn't added contact details. Their resume may have them.") }
                    else _ui.update { it.copy(contact = ShownContact(name, c.email, c.phone)) }
                }
                .onFailure { e ->
                    Log.e(TAG, "Contact failed", e)
                    _ui.update { it.copy(error = "Couldn't load contact details.") }
                }
        }
    }

    fun hideContact() = _ui.update { it.copy(contact = null) }

    private companion object { const val TAG = "Applicants" }
}
