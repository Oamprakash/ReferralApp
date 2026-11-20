package com.referral.app.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.ReferrerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Which bottom-bar tabs to show. "My posts" only appears for people who have verified a work email,
 * so job seekers see four tabs instead of five. The last answer is cached on the phone so referrers
 * don't see the tab pop in after a network round trip.
 */
@HiltViewModel
class TabsViewModel @Inject constructor(
    private val referrers: ReferrerRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("tabs", Context.MODE_PRIVATE)

    private val _showPosts = MutableStateFlow(prefs.getBoolean(KEY, false))
    val showPosts: StateFlow<Boolean> = _showPosts.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            runCatching { referrers.hasEverVerified() }.onSuccess(::set)
        }
    }

    /** Called right after a successful work-email verification. */
    fun markReferrer() = set(true)

    private fun set(value: Boolean) {
        _showPosts.value = value
        prefs.edit().putBoolean(KEY, value).apply()
    }

    private companion object { const val KEY = "show_posts_tab" }
}
