package com.referral.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jobs the user bookmarked to come back to. Kept on this phone only (cleared with app data);
 * jobs that close or expire simply stop showing under "Saved".
 */
@Singleton
class SavedJobs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("saved_jobs", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet())!!.toSet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** Returns true if the job is now saved. */
    fun toggle(jobId: String): Boolean {
        val now = if (jobId in _ids.value) _ids.value - jobId else (_ids.value + jobId).let { set ->
            if (set.size > MAX) set.drop(set.size - MAX).toSet() else set
        }
        _ids.value = now
        prefs.edit().putStringSet(KEY, now).apply()
        return jobId in now
    }

    private companion object {
        const val KEY = "ids"
        const val MAX = 100   // the feed query takes at most this many ids
    }
}
