package com.referral.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(
    onSaved: () -> Unit,
    /** False during first-time setup: there's nothing to download or delete yet. */
    showPrivacy: Boolean = true,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onResumePicked(uri)
    }
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        viewModel.saveExport(uri)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ui.pendingExport) {
        if (ui.pendingExport != null) saveExport.launch("my-referral-data.json")
    }

    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            viewModel.onSavedHandled()
            onSaved()
        }
    }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your profile", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Only the employees whose jobs you apply to can see your profile and resume.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Resume first: it fills in the rest of the form
        OutlinedButton(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            enabled = !ui.uploading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    ui.uploading -> "Uploading and reading…"
                    ui.hasResume -> "Resume uploaded · Replace PDF"
                    else -> "Upload resume (PDF, max 5 MB)"
                },
            )
        }
        Text(
            ui.autofillNote ?: "We read your resume on this phone to fill in the details below. You can edit anything.",
            style = MaterialTheme.typography.bodySmall,
            color = if (ui.autofillNote != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(ui.fullName, viewModel::onName, label = { Text("Full name") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ui.city, viewModel::onCity, label = { Text("City") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            ui.experienceText, viewModel::onExperience, label = { Text("Years of experience") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.skillsText, viewModel::onSkills, label = { Text("Skills, comma-separated") },
            supportingText = { Text("Used to match you with jobs, e.g. Kotlin, Jetpack Compose, Android") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Contact for referrals", style = MaterialTheme.typography.titleSmall)
        Text(
            "Referrers need these for their company's referral form. They see them only after shortlisting you.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            ui.contactEmail, viewModel::onContactEmail, label = { Text("Email (optional)") },
            singleLine = true, isError = ui.emailProblem != null,
            supportingText = ui.emailProblem?.let { msg -> { Text(msg) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            ui.contactPhone, viewModel::onContactPhone, label = { Text("Mobile (optional)") },
            prefix = { Text("+91 ") }, singleLine = true, isError = ui.phoneProblem != null,
            supportingText = ui.phoneProblem?.let { msg -> { Text(msg) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = viewModel::save, enabled = ui.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(if (ui.saving) "Saving…" else "Save profile")
        }

        if (showPrivacy) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Your data", style = MaterialTheme.typography.titleMedium)
            Text(
                "Download a copy of everything we store about you, or delete your account. " +
                    "Deleting removes your profile, resume, posts and applications for good.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = viewModel::prepareExport, enabled = !ui.exporting, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.exporting) "Preparing…" else "Download my data")
            }
            TextButton(onClick = { confirmDelete = true }, enabled = !ui.deleting, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.deleting) "Deleting…" else "Delete my account", color = MaterialTheme.colorScheme.error)
            }
            ui.privacyMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.deleteAccount() },
        )
    }
}

/** Typing DELETE makes an accidental tap impossible; the server checks the same word. */
@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This can't be undone. Your profile, resume, job posts, applications and notifications are deleted. " +
                        "People who applied to your open posts are told the post closed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it.take(10) },
                    label = { Text("Type DELETE to confirm") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == "DELETE") {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
