package com.referral.app.ui.referrer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.ActivePostLimitException
import com.referral.app.data.ReferrerRepository
import com.referral.app.data.SuspendedException
import com.referral.app.data.UnderReviewException
import com.referral.app.data.model.MyPost
import com.referral.app.data.model.PostSummary
import com.referral.app.data.model.ReferrerStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPostsUiState(
    val loading: Boolean = true,
    val posts: List<MyPost> = emptyList(),
    val summaries: Map<String, PostSummary> = emptyMap(),
    val stats: ReferrerStats? = null,
    val busyId: String? = null,
    val error: String? = null,
) {
    val activeCount get() = posts.count { it.isActive }
}

@HiltViewModel
class MyPostsViewModel @Inject constructor(
    private val repo: ReferrerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MyPostsUiState())
    val ui: StateFlow<MyPostsUiState> = _ui.asStateFlow()

    // Loaded by the screen on every resume (first open included)

    fun load() {
        viewModelScope.launch {
            runCatching { repo.myPosts() }
                .onSuccess { list -> _ui.update { it.copy(loading = false, posts = list, error = null) } }
                .onFailure { e ->
                    Log.e("MyPosts", "Load failed", e)
                    _ui.update { it.copy(loading = false, error = "Couldn't load your posts.") }
                }
            // Counts are a nice-to-have: the list still shows if they fail
            runCatching { repo.postsSummary() }.onSuccess { m -> _ui.update { it.copy(summaries = m) } }
            runCatching { repo.myStats() }.onSuccess { s -> _ui.update { it.copy(stats = s) } }
        }
    }

    fun confirm(id: String) = act(id) { repo.confirmStillHiring(id) }
    fun close(id: String) = act(id) { repo.closePost(id) }

    private fun act(id: String, block: suspend () -> Unit) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, error = null) }
            runCatching { block() }
                .onFailure { e ->
                    Log.e("MyPosts", "Action failed", e)
                    _ui.update {
                        it.copy(error = when (e) {
                            is ActivePostLimitException -> "You already have 5 active posts. Close one first."
                            is UnderReviewException -> "This post is being reviewed by moderators. We'll notify you when it's done."
                            is SuspendedException -> "Your account is suspended, so posts can't be reopened."
                            else -> "Couldn't update the post. If your verification expired, verify again."
                        })
                    }
                }
            _ui.update { it.copy(busyId = null) }
            load()
        }
    }
}
