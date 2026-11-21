package com.referral.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.AuthRepository
import com.referral.app.data.FeedFilter
import com.referral.app.data.JobRepository
import com.referral.app.data.OnboardingPrefs
import com.referral.app.data.ProfileRepository
import com.referral.app.data.SavedJobs
import com.referral.app.data.SafetyRepository
import com.referral.app.data.model.CompanyOption
import com.referral.app.data.model.Job
import com.referral.app.data.model.JobPoster
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
    val savedIds: Set<String> = emptySet(),
    /** Who posted each job (first name, referrals made). Missing entries just hide the line. */
    val posters: Map<String, JobPoster> = emptyMap(),
    val filter: FeedFilter = FeedFilter(),
    val companies: List<CompanyOption> = emptyList(),
    val locations: List<String> = emptyList(),
    val myYears: Int? = null,
    val isAdmin: Boolean = false,
    val firstName: String? = null,
    /** Null until loaded. False: show the "upload your resume" card (they can browse without it). */
    val profileComplete: Boolean? = null,
    val postPromptDismissed: Boolean = false,
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
    private val saved: SavedJobs,
    private val onboarding: OnboardingPrefs,
) : ViewModel() {

    private val _ui = MutableStateFlow(FeedUiState())
    val ui: StateFlow<FeedUiState> = _ui.asStateFlow()

    private var loadJob: CoroutineJob? = null
    private var resumedOnce = false

    init {
        _ui.update { it.copy(postPromptDismissed = onboarding.postPromptDismissed) }
        viewModelScope.launch { saved.ids.collect { ids -> _ui.update { it.copy(savedIds = ids) } } }
        load(initial = true)
        viewModelScope.launch {
            runCatching { jobs.companies() }.onSuccess { list -> _ui.update { it.copy(companies = list) } }
            runCatching { jobs.openLocations() }.onSuccess { list -> _ui.update { it.copy(locations = list) } }
            runCatching { profiles.myProfile() }.onSuccess { p ->
                _ui.update { it.copy(myYears = p?.experienceYears, firstName = p?.fullName?.trim()?.substringBefore(' ')?.ifBlank { null },
                    profileComplete = p?.isComplete == true) }
            }
            runCatching { safety.amIAdmin() }.onSuccess { a -> _ui.update { it.copy(isAdmin = a) } }
        }
    }

    fun refresh() = load(initial = false)

    /** Refresh quietly when returning to the feed (after applying or posting); skips the first resume. */
    fun onResume() {
        if (resumedOnce) {
            load(initial = false)
            // They may have just uploaded a resume from Profile
            viewModelScope.launch {
                runCatching { profiles.myProfile() }.onSuccess { p ->
                    _ui.update { it.copy(myYears = p?.experienceYears, profileComplete = p?.isComplete == true) }
                }
            }
        } else resumedOnce = true
    }

    fun dismissPostPrompt() {
        onboarding.dismissPostPrompt()
        _ui.update { it.copy(postPromptDismissed = true) }
    }

    fun onQuery(q: String) = setFilter(debounce = true) { it.copy(query = q.take(60)) }

    fun toggleWorkMode(mode: String) = setFilter {
        it.copy(workModes = if (mode in it.workModes) it.workModes - mode else it.workModes + mode)
    }

    fun setCompany(companyId: String?) = setFilter { it.copy(companyId = companyId) }

    fun setLocation(city: String?) = setFilter { it.copy(location = city) }

    fun toggleFitsMe() = setFilter {
        it.copy(fitsYears = if (it.fitsYears != null) null else _ui.value.myYears)
    }

    fun clearFilters() = setFilter { FeedFilter() }

    fun toggleSavedOnly() = setFilter { it.copy(savedIds = if (it.savedIds != null) null else saved.ids.value) }

    fun toggleSaved(jobId: String) {
        saved.toggle(jobId)
        // In the Saved view, an unsaved job drops out right away
        if (_ui.value.filter.savedIds != null) setFilter { it.copy(savedIds = saved.ids.value) }
    }

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
                // Poster names come second so the list never waits on them
                runCatching { jobs.posters(list.map { it.id }) }
                    .onSuccess { p -> _ui.update { it.copy(posters = it.posters + p) } }
                    .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; Log.w(TAG, "Posters failed", e) }
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
