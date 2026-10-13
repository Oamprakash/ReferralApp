package com.referral.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.AuthRepository
import com.referral.app.data.FeedFilter
import com.referral.app.data.JobRepository
import com.referral.app.data.ProfileRepository
import com.referral.app.data.SafetyRepository
import com.referral.app.data.model.CompanyOption
import com.referral.app.data.model.Job
import com.referral.app.push.PushRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val jobs: List<Job> = emptyList(),
    val appliedJobIds: Set<String> = emptySet(),
    val filter: FeedFilter = FeedFilter(),
    val companies: List<CompanyOption> = emptyList(),
    val myYears: Int? = null,
    val isAdmin: Boolean = false,
    val error: String? = null,
) {
    val selectedCompanyName: String?
        get() = filter.companyId?.let { id -> companies.firstOrNull { it.id == id }?.name }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val jobs: JobRepository,
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
    private val push: PushRegistrar,
    private val safety: SafetyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FeedUiState())
    val ui: StateFlow<FeedUiState> = _ui.asStateFlow()

    private var loadJob: CoroutineJob? = null
    private var resumedOnce = false

    init {
        load(initial = true)
        viewModelScope.launch {
            runCatching { jobs.companies() }.onSuccess { list -> _ui.update { it.copy(companies = list) } }
            runCatching { profiles.myProfile() }.onSuccess { p -> _ui.update { it.copy(myYears = p?.experienceYears) } }
            runCatching { safety.amIAdmin() }.onSuccess { a -> _ui.update { it.copy(isAdmin = a) } }
        }
    }

    fun refresh() = load(initial = false)

    /** Refresh quietly when returning to the feed (after applying or posting); skips the first resume. */
    fun onResume() {
        if (resumedOnce) load(initial = false) else resumedOnce = true
    }

    fun onQuery(q: String) = setFilter(debounce = true) { it.copy(query = q.take(60)) }

    fun toggleWorkMode(mode: String) = setFilter {
        it.copy(workModes = if (mode in it.workModes) it.workModes - mode else it.workModes + mode)
    }

    fun setCompany(companyId: String?) = setFilter { it.copy(companyId = companyId) }

    fun toggleFitsMe() = setFilter {
        it.copy(fitsYears = if (it.fitsYears != null) null else _ui.value.myYears)
    }

    fun clearFilters() = setFilter { FeedFilter() }

    private fun setFilter(debounce: Boolean = false, change: (FeedFilter) -> FeedFilter) {
        _ui.update { it.copy(filter = change(it.filter)) }
        load(initial = false, debounceMs = if (debounce) 350 else 0)
    }

    private fun load(initial: Boolean, debounceMs: Long = 0) {
        loadJob?.cancel()   // newest filter wins
        loadJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _ui.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            runCatching {
                val list = jobs.feed(_ui.value.filter)
                val applied = runCatching { jobs.myAppliedJobIds() }.getOrDefault(_ui.value.appliedJobIds)
                list to applied
            }.onSuccess { (list, applied) ->
                _ui.update { it.copy(loading = false, refreshing = false, jobs = list, appliedJobIds = applied) }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Feed load failed", e)   // full cause in Logcat, filter by "Feed"
                _ui.update {
                    it.copy(loading = false, refreshing = false,
                        error = "Couldn't load jobs. Pull down to retry.\n${e.message.orEmpty().take(200)}")
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { push.unregister() }   // while the session is still valid
            runCatching { auth.signOut() }
        }
    }

    private companion object { const val TAG = "Feed" }
}
