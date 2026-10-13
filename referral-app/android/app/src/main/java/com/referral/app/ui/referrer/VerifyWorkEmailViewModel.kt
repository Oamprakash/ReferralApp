package com.referral.app.ui.referrer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.referral.app.data.ReferrerRepository
import com.referral.app.data.model.OtpResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyUiState(
    val email: String = "",
    val code: String = "",
    val codeSent: Boolean = false,
    val companyName: String? = null,
    val loading: Boolean = false,
    val message: String? = null,       // neutral info (e.g. queued for review)
    val error: String? = null,
    val verified: Boolean = false,
    val devCode: String? = null,
)

@HiltViewModel
class VerifyWorkEmailViewModel @Inject constructor(
    private val repo: ReferrerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(VerifyUiState())
    val ui: StateFlow<VerifyUiState> = _ui.asStateFlow()

    fun onEmail(v: String) = _ui.update { it.copy(email = v.trim().take(254), error = null, message = null) }
    fun onCode(v: String) = _ui.update { it.copy(code = v.filter(Char::isDigit).take(6), error = null) }
    fun changeEmail() = _ui.update { it.copy(codeSent = false, code = "", devCode = null, error = null) }

    fun sendCode() = launch {
        val r = repo.requestWorkEmailOtp(_ui.value.email)
        _ui.update {
            when (r.status) {
                "sent" -> it.copy(codeSent = true, companyName = r.companyName, devCode = r.devCode,
                    message = "We sent a 6-digit code to ${r.email ?: it.email}.")
                "invalid_email" -> it.copy(error = "That doesn't look like an email address.")
                "free_domain" -> it.copy(error = "Use your company email, not a personal one like Gmail.")
                "pending_review" -> it.copy(message =
                    "${r.domain ?: "This company"} isn't on our list yet. We've queued it for review; you can verify once it's approved.")
                "email_in_use" -> it.copy(error = "This work email is already verified on another account.")
                "cooldown" -> it.copy(error = "Please wait ${r.retryAfterSeconds ?: 60} seconds before asking for another code.")
                "rate_limited" -> it.copy(error = "Too many codes requested. Try again in an hour.")
                "send_failed" -> it.copy(error = "We couldn't send the email. Try again in a minute.")
                else -> it.copy(error = errorText(r))
            }
        }
    }

    fun verify() = launch {
        val r = repo.verifyWorkEmailOtp(_ui.value.code)
        _ui.update {
            when (r.status) {
                "verified" -> it.copy(verified = true, companyName = r.companyName,
                    message = if ((r.closedPosts ?: 0) > 0)
                        "Verified. ${r.closedPosts} post(s) at your previous company were closed." else null)
                "invalid_code" -> it.copy(error = "Wrong code. ${r.attemptsLeft ?: 0} tries left.")
                "invalid_format" -> it.copy(error = "Enter the 6-digit code.")
                "too_many_attempts" -> it.copy(error = "Too many wrong tries. Request a new code.", codeSent = false, code = "")
                "no_pending_code" -> it.copy(error = "This code expired. Request a new one.", codeSent = false, code = "")
                "email_in_use" -> it.copy(error = "This work email was just verified on another account.")
                else -> it.copy(error = errorText(r))
            }
        }
    }

    private fun errorText(r: OtpResponse) = when (r.error) {
        "unauthorized" -> "Your session expired. Sign in again."
        else -> "Something went wrong. Try again."
    }

    private fun launch(block: suspend () -> Unit) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { block() }.onFailure { e ->
                Log.e("VerifyWorkEmail", "Request failed", e)
                _ui.update { it.copy(error = "Couldn't reach the server. Check your connection.") }
            }
            _ui.update { it.copy(loading = false) }
        }
    }
}
