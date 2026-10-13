package com.referral.app.ui.referrer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostJobScreen(
    onBack: () -> Unit,
    onNeedsVerification: () -> Unit,
    onPosted: () -> Unit,
    viewModel: PostJobViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.posted) { if (ui.posted) onPosted() }

    // Back from the verification screen: check again
    LifecycleResumeEffect(ui.verification == null) {
        if (!ui.loading && ui.verification == null) viewModel.reload()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post a job") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.verification == null -> NeedsVerification(
                modifier = Modifier.padding(padding),
                error = ui.error,
                onVerify = onNeedsVerification,
            )
            else -> Form(
                modifier = Modifier.padding(padding),
                ui = ui,
                onEdit = viewModel::edit,
                onPost = viewModel::post,
            )
        }
    }
}

@Composable
private fun NeedsVerification(modifier: Modifier, error: String?, onVerify: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Verify your work email first", style = MaterialTheme.typography.titleMedium)
        Text(
            "Seekers trust this app because every post comes from a verified employee. " +
                "It takes a minute and lasts 90 days.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onVerify, modifier = Modifier.fillMaxWidth()) { Text("Verify work email") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Form(
    modifier: Modifier,
    ui: PostJobUiState,
    onEdit: ((PostJobForm) -> PostJobForm) -> Unit,
    onPost: () -> Unit,
) {
    val f = ui.form
    val v = ui.verification!!
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Posting as a verified employee of ${v.companyName} · verification valid for ${v.daysLeft} more days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Field("Job title *", f.title) { s -> onEdit { it.copy(title = s.take(120)) } }
        Field("Team", f.team) { s -> onEdit { it.copy(team = s.take(80)) } }
        Field("Location *", f.location) { s -> onEdit { it.copy(location = s.take(60)) } }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Min exp (yrs) *", f.expMin, Modifier.weight(1f), KeyboardType.Number) { s ->
                onEdit { it.copy(expMin = s.filter(Char::isDigit).take(2)) }
            }
            Field("Max exp (yrs) *", f.expMax, Modifier.weight(1f), KeyboardType.Number) { s ->
                onEdit { it.copy(expMax = s.filter(Char::isDigit).take(2)) }
            }
        }

        Text("Work mode", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkMode.entries.forEach { mode ->
                FilterChip(
                    selected = f.workMode == mode,
                    onClick = { onEdit { it.copy(workMode = mode) } },
                    label = { Text(mode.label) },
                )
            }
        }

        OutlinedTextField(
            value = f.description,
            onValueChange = { s -> onEdit { it.copy(description = s.take(4000)) } },
            label = { Text("Job description (optional)") },
            placeholder = { Text("What the team builds, what you'd work on, must-haves. Paste from the careers page if you like.") },
            supportingText = { Text("${f.description.length}/4000") },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = f.skills,
            onValueChange = { s -> onEdit { it.copy(skills = s.take(300)) } },
            label = { Text("Key skills, comma-separated") },
            placeholder = { Text("e.g. Kotlin, Jetpack Compose, Coroutines") },
            supportingText = { Text("Used to rank applicants. Up to 10.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SuggestedSkills(f.suggestedSkills) { skill -> onEdit { it.withSkill(skill) } }

        Field("Salary range (optional)", f.salary, placeholder = "e.g. ₹18–25 LPA") { s ->
            onEdit { it.copy(salary = s.take(40)) }
        }

        Text("Official link or job ID * (at least one)", style = MaterialTheme.typography.labelLarge)
        Field("Careers page link", f.officialUrl, keyboard = KeyboardType.Uri, placeholder = "https://…") { s ->
            onEdit { it.copy(officialUrl = s.trim().take(500)) }
        }
        Field("Job ID / requisition number", f.jobRef) { s -> onEdit { it.copy(jobRef = s.take(60)) } }

        Text("Screening questions (optional, up to 3)", style = MaterialTheme.typography.labelLarge)
        f.questions.forEachIndexed { i, q ->
            Field("Question ${i + 1}", q) { s ->
                onEdit { form -> form.copy(questions = form.questions.toMutableList().also { it[i] = s.take(200) }) }
            }
        }

        Field("Applicant limit (1–50)", f.cap, keyboard = KeyboardType.Number) { s ->
            onEdit { it.copy(cap = s.filter(Char::isDigit).take(2)) }
        }

        Text(
            "Only post roles that are publicly listed by your company. Never ask applicants for money.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (ui.showErrors) f.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                // Form problems show inline without a dialog; only a valid form asks for confirmation
                if (f.problem != null) onPost()
                else confirm = ConfirmRequest(
                    "Post \"${f.title.trim()}\"?",
                    "It goes live in the feed right away as posted by a verified ${v.companyName} employee. " +
                        "Make sure it's a real, publicly listed opening.",
                    "Post job", onConfirm = onPost)
            },
            enabled = !ui.posting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ui.posting) "Posting…" else "Post job")
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}

/** Skills found in the description, offered as one-tap additions to the skills field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestedSkills(skills: List<String>, onAdd: (String) -> Unit) {
    if (skills.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("From your description, tap to add:", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            skills.forEach { skill -> AssistChip(onClick = { onAdd(skill) }, label = { Text("+ $skill") }) }
        }
    }
}
