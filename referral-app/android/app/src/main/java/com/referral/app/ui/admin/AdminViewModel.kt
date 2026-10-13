package com.referral.app.ui.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.AdminRepository
import com.referral.app.data.DomainRequest
import com.referral.app.data.ReportGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdminTab(val label: String) { REPORTS("Reports"), COMPANIES("Company requests") }

data class AdminUiState(
    val loading: Boolean = true,
    val tab: AdminTab = AdminTab.REPORTS,
    val reports: List<ReportGroup> = emptyList(),
    val domains: List<DomainRequest> = emptyList(),
    val busyKey: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repo: AdminRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AdminUiState())
    val ui: StateFlow<AdminUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching { repo.openReports() to repo.domainRequests() }
                .onSuccess { (r, d) -> _ui.update { it.copy(loading = false, reports = r, domains = d, error = null) } }
                .onFailure { e ->
                    Log.e(TAG, "Load failed", e)
                    _ui.update {
                        it.copy(loading = false, error = if ("NOT_ADMIN" in e.message.orEmpty())
                            "This account isn't an admin." else "Couldn't load. Pull to retry.")
                    }
                }
        }
    }

    fun selectTab(tab: AdminTab) = _ui.update { it.copy(tab = tab) }

    fun resolve(group: ReportGroup, action: String) = act(group.key, when {
        action == "dismiss" -> "Reports dismissed."
        group.targetType == "job" -> "Post closed."
        else -> "Account suspended."
    }) { repo.resolve(group, action) }

    fun approve(domain: String, companyName: String) =
        act(domain, "$domain approved for $companyName.") { repo.approveDomain(domain, companyName) }

    fun reject(domain: String) = act(domain, "$domain rejected.") { repo.rejectDomain(domain) }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    private fun act(key: String, done: String, block: suspend () -> Unit) {
        if (_ui.value.busyKey != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyKey = key, error = null, message = null) }
            runCatching { block() }
                .onSuccess { _ui.update { it.copy(message = done) } }
                .onFailure { e ->
                    Log.e(TAG, "Action failed", e)
                    _ui.update { it.copy(error = e.message?.substringAfter("ERROR: ")?.take(160) ?: "Action failed.") }
                }
            _ui.update { it.copy(busyKey = null) }
            load()
        }
    }

    private companion object { const val TAG = "Admin" }
}
