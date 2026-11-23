package com.referral.app.ui.seeker

import com.referral.app.analytics.NonFatal
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.JobRepository
import com.referral.app.data.model.MyApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime
import javax.inject.Inject

data class MyApplicationsUiState(
    val loading: Boolean = true,
    val applications: List<MyApplication> = emptyList(),
    /** Referrer first name per job id; filled after the list, missing ones just show no name. */
    val referrers: Map<String, String> = emptyMap(),
    val busyId: String? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
) {
    val usedThisWeek: Int
        get() = applications.count {
            runCatching { Duration.between(OffsetDateTime.parse(it.createdAt), OffsetDateTime.now()).toDays() < 7 }
                .getOrDefault(false)
        }
}

@HiltViewModel
class MyApplicationsViewModel @Inject constructor(
    private val jobs: JobRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MyApplicationsUiState())
    val ui: StateFlow<MyApplicationsUiState> = _ui.asStateFlow()

    /** Pull to refresh. */
    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        load()
    }

    /** "Try again" after a failed load. */
    fun retry() {
        _ui.update { it.copy(loading = true, error = null) }
        load()
    }

    /** Called on every resume (first open included). */
    fun load() {
        viewModelScope.launch {
            runCatching { jobs.myApplications() }
                .onSuccess { list ->
                    _ui.update { it.copy(loading = false, refreshing = false, applications = list, error = null) }
                    runCatching { jobs.posters(list.map { it.jobId }.distinct()) }
                        .onSuccess { p ->
                            val names = p.mapNotNull { (id, poster) -> poster.firstName?.ifBlank { null }?.let { id to it } }.toMap()
                            _ui.update { it.copy(referrers = it.referrers + names) }
                        }
                        .onFailure { e -> Log.w("MyApplications", "Referrer names failed", e) }
                }
                .onFailure { e ->
                    Log.e("MyApplications", "Load failed", e)
                    NonFatal.record(e)
                    _ui.update { it.copy(loading = false, refreshing = false, error = "Couldn't load your applications") }
                }
        }
    }

    fun withdraw(id: String) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, error = null) }
            runCatching { jobs.withdraw(id) }
                .onSuccess { ok ->
                    if (!ok) _ui.update { it.copy(error = "That application was already reviewed, so it can't be withdrawn.") }
                }
                .onFailure { e ->
                    Log.e("MyApplications", "Withdraw failed", e)
                    _ui.update { it.copy(error = "Couldn't withdraw. Try again.") }
                }
            _ui.update { it.copy(busyId = null) }
            load()
        }
    }
}
