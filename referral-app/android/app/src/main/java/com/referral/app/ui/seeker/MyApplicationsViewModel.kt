package com.referral.app.ui.seeker

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
    val busyId: String? = null,
    val error: String? = null,
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

    /** Called on every resume (first open included). */
    fun load() {
        viewModelScope.launch {
            runCatching { jobs.myApplications() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, applications = list, error = null) } }
                .onFailure { e ->
                    Log.e("MyApplications", "Load failed", e)
                    _ui.update { it.copy(loading = false, error = "Couldn't load your applications.") }
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
