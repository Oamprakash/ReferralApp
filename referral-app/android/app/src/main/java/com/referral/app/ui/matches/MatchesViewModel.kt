package com.referral.app.ui.matches

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.JobRepository
import com.referral.app.data.ProfileRepository
import com.referral.app.data.model.JobMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val matches: List<JobMatch> = emptyList(),
    /** No skills on the profile: matches would only use experience, so nudge them to add skills. */
    val profileThin: Boolean = false,
    /** Years on the profile, to explain the experience half of each score. */
    val myYears: Int? = null,
    val error: String? = null,
)

@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val jobs: JobRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MatchesUiState())
    val ui: StateFlow<MatchesUiState> = _ui.asStateFlow()

    /** Called on every resume: the profile may have changed, or the user applied to one. */
    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = !it.loading, error = null) }
            runCatching {
                val p = profiles.myProfile()
                Triple(jobs.bestMatches(), p?.skills.isNullOrEmpty(), p?.experienceYears)
            }.onSuccess { (list, thin, years) ->
                _ui.update { it.copy(loading = false, refreshing = false, matches = list, profileThin = thin, myYears = years) }
            }.onFailure { e ->
                Log.e("Matches", "Load failed", e)
                _ui.update { it.copy(loading = false, refreshing = false, error = "Couldn't load matches. Pull to retry.") }
            }
        }
    }
}
