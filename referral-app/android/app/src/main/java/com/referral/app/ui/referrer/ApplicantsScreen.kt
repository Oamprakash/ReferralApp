package com.referral.app.ui.referrer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.data.model.Applicant
import com.referral.app.data.model.applicationStatusLabel
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest
import com.referral.app.ui.common.ReportDialog
import com.referral.app.data.ReportReason
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantsScreen(
    onBack: () -> Unit,
    viewModel: ApplicantsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    var reporting by remember { mutableStateOf<Applicant?>(null) }

    LaunchedEffect(Unit) {
        viewModel.openUrl.collect { url -> runCatching { uriHandler.openUri(url) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.jobTitle.ifBlank { "Applicants" }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = ui.tab.ordinal, edgePadding = 16.dp) {
                ApplicantTab.entries.forEach { t ->
                    Tab(
                        selected = ui.tab == t,
                        onClick = { viewModel.selectTab(t) },
                        text = { Text("${t.label} (${ui.count(t)})") },
                    )
                }
            }
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
                ui.message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
                if (ui.visible.isEmpty()) {
                    item {
                        Text(
                            when (ui.tab) {
                                ApplicantTab.TO_REVIEW -> "No one is waiting for review."
                                ApplicantTab.IN_PROGRESS -> "No one in progress yet."
                                ApplicantTab.DONE -> "No finished applications yet."
                                ApplicantTab.WAITLIST -> "No one on the waitlist."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(ui.visible, key = { it.id }) { a ->
                    ApplicantCard(
                        applicant = a,
                        questions = ui.questions,
                        busy = ui.busyId == a.id,
                        onResume = { viewModel.openResume(a.profile?.resumePath) },
                        // Every step notifies the seeker and can't be undone, so each one is confirmed
                        onStep = { status -> confirm = stepConfirmation(a, status) { viewModel.setStatus(a.id, status) } },
                        onReport = { reporting = a },
                        onContact = { viewModel.showContact(a) },
                    )
                }
                if (ui.tab == ApplicantTab.IN_PROGRESS) {
                    item {
                        Text(
                            "After you submit a referral in your company's portal, tap Mark referred. " +
                                "Update the outcome later so seekers know where they stand.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    ConfirmDialog(confirm, onDismiss = { confirm = null })

    ui.contact?.let { c -> ContactDialog(c, onDismiss = viewModel::hideContact) }

    reporting?.let { a ->
        ReportDialog(
            title = "Report ${a.profile?.fullName?.ifBlank { null } ?: "this applicant"}",
            reasons = ReportReason.entries.filter { it != ReportReason.CLOSED },
            onDismiss = { reporting = null },
            onSubmit = { reason, details ->
                reporting = null
                a.seekerId?.let { viewModel.report(it, reason, details) }
            },
        )
    }
}

/** Wording for each status step; all of them notify the seeker and move forward only. */
private fun stepConfirmation(a: Applicant, status: String, onConfirm: () -> Unit): ConfirmRequest {
    val name = a.profile?.fullName?.ifBlank { null } ?: "this applicant"
    return when (status) {
        "shortlisted" -> ConfirmRequest(
            "Shortlist $name?",
            "$name will be notified that you shortlisted them. Your next step is to refer them in your company's referral portal.",
            "Shortlist", onConfirm = onConfirm)
        "referred" -> ConfirmRequest(
            "Mark $name as referred?",
            "Only do this after you've submitted the referral in your company's portal. $name will be notified, and it counts toward your public record.",
            "Mark referred", onConfirm = onConfirm)
        "interview" -> ConfirmRequest(
            "Record an interview for $name?",
            "$name will be notified. This can't be undone.",
            "Record interview", onConfirm = onConfirm)
        "offer" -> ConfirmRequest(
            "Record an offer for $name?",
            "Only mark this once $name actually has the offer. They'll be notified, and it shows on your public record.",
            "Record offer", onConfirm = onConfirm)
        "declined" -> ConfirmRequest(
            "Decline $name?",
            "$name will be notified that you're not taking their application forward. This can't be undone.",
            "Decline", destructive = true, onConfirm = onConfirm)
        else -> ConfirmRequest(   // not_selected
            "Mark $name as not selected?",
            "$name will be notified that the company didn't move forward. This can't be undone.",
            "Not selected", destructive = true, onConfirm = onConfirm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplicantCard(
    applicant: Applicant,
    questions: List<String>,
    busy: Boolean,
    onResume: () -> Unit,
    onStep: (String) -> Unit,
    onReport: () -> Unit,
    onContact: () -> Unit,
) {
    val p = applicant.profile
    var showAnswers by rememberSaveable(applicant.id) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p?.fullName ?: "Applicant", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                applicant.matchScore?.let {
                    Text("$it% match", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                listOfNotNull(p?.experienceYears?.let { "$it yrs" }, p?.city).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!p?.skills.isNullOrEmpty()) {
                Text(p!!.skills.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
            Text(applicationStatusLabel(applicant.status), style = MaterialTheme.typography.labelMedium)

            if (applicant.answers.isNotEmpty()) {
                TextButton(onClick = { showAnswers = !showAnswers }) {
                    Text(if (showAnswers) "Hide answers" else "Show answers (${applicant.answers.size})")
                }
                if (showAnswers) {
                    applicant.answers.forEachIndexed { i, ans ->
                        Text(questions.getOrNull(i) ?: "Question ${i + 1}", style = MaterialTheme.typography.labelLarge)
                        Text(ans, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // wraps on narrow phones
                OutlinedButton(onClick = onResume, enabled = !busy) { Text("Resume") }
                if (applicant.status in CONTACT_STATUSES) {
                    OutlinedButton(onClick = onContact, enabled = !busy) { Text("Contact") }
                }
                applicant.nextSteps.forEachIndexed { i, (status, label) ->
                    if (i == 0) Button(onClick = { onStep(status) }, enabled = !busy) { Text(label) }
                    else TextButton(onClick = { onStep(status) }, enabled = !busy) { Text(label) }
                }
                if (applicant.seekerId != null) {
                    TextButton(onClick = onReport, enabled = !busy) { Text("Report", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Email and phone for the company's referral form, with copy buttons. */
@Composable
private fun ContactDialog(c: ShownContact, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For your company's referral form only. Please don't share it.",
                    style = MaterialTheme.typography.bodySmall)
                listOfNotNull(c.email?.let { "Email" to it }, c.phone?.let { "Phone" to it }).forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
                        }
                        TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) { Text("Copy") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
