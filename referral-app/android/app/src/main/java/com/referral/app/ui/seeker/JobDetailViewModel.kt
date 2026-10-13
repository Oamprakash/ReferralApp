package com.referral.app.ui.seeker

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.referral.app.data.JobRepository
import com.referral.app.data.ReportException
import com.referral.app.data.ReportReason
import com.referral.app.data.SafetyRepository
import com.referral.app.data.model.JobDetail
import com.referral.app.ui.navigation.JobDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the main button on the detail screen should do. */
enum class ApplyAction {
    APPLY, JOIN_WAITLIST, REAPPLY, ALREADY_APPLIED, WITHDRAWN_FINAL,
    OWN_POST, CLOSED, COMPLETE_PROFILE, WEEKLY_LIMIT,
}

data class JobDetailUiState(
    val loading: Boolean = true,
    val job: JobDetail? = null,
    val notFound: Boolean = false,
    val withdrawing: Boolean = false,
    val error: String? = null,
    val reporting: Boolean = false,
    val reportMessage: String? = null,
) {
    val action: ApplyAction?
        get() {
            val j = job ?: return null
            val withdrawn = j.myApplicationStatus == "withdrawn"
            return when {
                j.isMine -> ApplyAction.OWN_POST
                j.myApplicationStatus != null && !withdrawn -> ApplyAction.ALREADY_APPLIED
                withdrawn && !j.canReapply && j.hasReapplied -> ApplyAction.WITHDRAWN_FINAL
                !j.isOpen -> ApplyAction.CLOSED
                !j.profileComplete -> ApplyAction.COMPLETE_PROFILE
                j.weeklyLimitReached -> ApplyAction.WEEKLY_LIMIT
                withdrawn -> ApplyAction.REAPPLY
                j.spotsLeft == 0 -> ApplyAction.JOIN_WAITLIST
                else -> ApplyAction.APPLY
            }
        }
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobs: JobRepository,
    private val safety: SafetyRepository,
) : ViewModel() {

    val jobId: String = savedStateHandle.toRoute<JobDetailRoute>().jobId

    private val _ui = MutableStateFlow(JobDetailUiState())
    val ui: StateFlow<JobDetailUiState> = _ui.asStateFlow()

    /** Called on every resume so the screen reflects an application made on the apply screen. */
    fun load() {
        viewModelScope.launch {
            runCatching { jobs.jobDetail(jobId) }
                .onSuccess { d -> _ui.update { it.copy(loading = false, job = d, notFound = d == null, error = null) } }
                .onFailure { e ->
                    Log.e("JobDetail", "Load failed", e)
                    _ui.update { it.copy(loading = false, error = "Couldn't load this job.") }
                }
        }
    }

    fun withdraw() {
        val appId = _ui.value.job?.myApplicationId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(withdrawing = true, error = null) }
            runCatching { jobs.withdraw(appId) }
                .onSuccess { ok ->
                    if (!ok) _ui.update { it.copy(error = "This application has already been reviewed, so it can't be withdrawn.") }
                }
                .onFailure { e ->
                    Log.e("JobDetail", "Withdraw failed", e)
                    _ui.update { it.copy(error = "Couldn't withdraw. Try again.") }
                }
            _ui.update { it.copy(withdrawing = false) }
            load()
        }
    }

    fun report(reason: ReportReason, details: String) {
        if (_ui.value.reporting) return
        viewModelScope.launch {
            _ui.update { it.copy(reporting = true, reportMessage = null) }
            val msg = runCatching { safety.report("job", jobId, reason, details) }.fold(
                onSuccess = { "Thanks. Moderators will review this post." },
                onFailure = { e ->
                    Log.e("JobDetail", "Report failed", e)
                    (e as? ReportException)?.message ?: "Couldn't send the report. Try again."
                },
            )
            _ui.update { it.copy(reporting = false, reportMessage = msg) }
        }
    }
}
