package com.referral.app.ui.seeker

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.referral.app.data.ApplyException
import com.referral.app.data.JobRepository
import com.referral.app.data.model.JobDetail
import com.referral.app.ui.navigation.ApplyRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApplyUiState(
    val loading: Boolean = true,
    val job: JobDetail? = null,
    val answers: List<String> = emptyList(),
    val submitting: Boolean = false,
    /** "applied" or "waitlisted" once submitted. */
    val resultStatus: String? = null,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = job != null && !submitting && answers.size == job.questions.size && answers.all { it.isNotBlank() }
}

@HiltViewModel
class ApplyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobs: JobRepository,
) : ViewModel() {

    private val jobId: String = savedStateHandle.toRoute<ApplyRoute>().jobId

    private val _ui = MutableStateFlow(ApplyUiState())
    val ui: StateFlow<ApplyUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { jobs.jobDetail(jobId) }
                .onSuccess { d ->
                    _ui.update {
                        it.copy(loading = false, job = d, answers = List(d?.questions?.size ?: 0) { "" },
                            error = if (d == null) "This job is no longer available." else null)
                    }
                }
                .onFailure { e ->
                    Log.e("Apply", "Load failed", e)
                    _ui.update { it.copy(loading = false, error = "Couldn't load this job.") }
                }
        }
    }

    fun onAnswer(index: Int, text: String) = _ui.update {
        it.copy(answers = it.answers.toMutableList().also { list -> list[index] = text.take(1000) }, error = null)
    }

    fun submit() {
        if (!_ui.value.canSubmit) return
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true, error = null) }
            val job = _ui.value.job
            runCatching {
                val appId = job?.myApplicationId
                if (job?.myApplicationStatus == "withdrawn" && appId != null) jobs.reapply(appId, _ui.value.answers)
                else jobs.apply(jobId, _ui.value.answers)
            }
                .onSuccess { status -> _ui.update { it.copy(submitting = false, resultStatus = status) } }
                .onFailure { e ->
                    Log.e("Apply", "Submit failed", e)
                    _ui.update {
                        it.copy(submitting = false, error = (e as? ApplyException)?.message ?: "Couldn't apply. Try again.")
                    }
                }
        }
    }
}
