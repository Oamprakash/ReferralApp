package com.referral.app.ui.referrer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest
import com.referral.app.ui.common.EmptyState
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.PrimaryButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.common.VerifiedBadge
import com.referral.app.ui.theme.Space

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
        topBar = { AppTopBar("Post a job", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            ui.loading -> LoadingBox(Modifier.padding(padding))
            ui.verification == null -> Column(Modifier.padding(padding).padding(horizontal = Space.screen)) {
                EmptyState(
                    icon = Icons.Filled.Email,
                    title = "Verify your work email first",
                    body = "Seekers trust this app because every post comes from a verified employee. " +
                        "It takes a minute and lasts 90 days.",
                    action = { PrimaryButton("Verify work email", onNeedsVerification) },
                )
                ui.error?.let { Banner(it, Tone.ERROR) }
            }
            else -> Form(Modifier.padding(padding), ui, viewModel::edit, viewModel::post)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            .padding(horizontal = Space.screen, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) { VerifiedBadge("Posting as ${v.companyName}") }
            Text("Verification valid for ${v.daysLeft} more days", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("The role") {
            Field("Job title *", f.title) { s -> onEdit { it.copy(title = s.take(120)) } }
            Field("Team", f.team, placeholder = "e.g. Payments, Mobile Platform") { s -> onEdit { it.copy(team = s.take(80)) } }
            Field("Location *", f.location) { s -> onEdit { it.copy(location = s.take(60)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Field("Min yrs *", f.expMin, Modifier.weight(1f), KeyboardType.Number) { s ->
                    onEdit { it.copy(expMin = s.filter(Char::isDigit).take(2)) }
                }
                Field("Max yrs *", f.expMax, Modifier.weight(1f), KeyboardType.Number) { s ->
                    onEdit { it.copy(expMax = s.filter(Char::isDigit).take(2)) }
                }
            }
            Text("Work mode", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                WorkMode.entries.forEach { mode ->
                    FilterChip(
                        selected = f.workMode == mode,
                        onClick = { onEdit { it.copy(workMode = mode) } },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Field("Salary range (optional)", f.salary, placeholder = "e.g. ₹18–25 LPA") { s ->
                onEdit { it.copy(salary = s.take(40)) }
            }
        }

        Section("Description & skills") {
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
                placeholder = { Text("Kotlin, Jetpack Compose, Coroutines") },
                supportingText = { Text("Used to rank applicants. Up to 10.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.suggestedSkills.isNotEmpty()) {
                Text("From your description, tap to add", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    f.suggestedSkills.forEach { skill ->
                        AssistChip(onClick = { onEdit { it.withSkill(skill) } }, label = { Text("+ $skill") })
                    }
                }
            }
        }

        Section("Official listing", "Add the careers page link, the job ID, or both, so seekers can check it's real.") {
            Field("Careers page link", f.officialUrl, keyboard = KeyboardType.Uri, placeholder = "https://…") { s ->
                onEdit { it.copy(officialUrl = s.trim().take(500)) }
            }
            Field("Job ID / requisition number", f.jobRef) { s -> onEdit { it.copy(jobRef = s.take(60)) } }
        }

        Section("Applicants", "Screening questions are optional, up to 3.") {
            f.questions.forEachIndexed { i, q ->
                Field("Question ${i + 1}", q) { s ->
                    onEdit { form -> form.copy(questions = form.questions.toMutableList().also { it[i] = s.take(200) }) }
                }
            }
            Field("Applicant limit (1–50)", f.cap, keyboard = KeyboardType.Number) { s ->
                onEdit { it.copy(cap = s.filter(Char::isDigit).take(2)) }
            }
        }

        Banner("Only post roles your company lists publicly. Never ask applicants for money.", Tone.WARNING)
        if (ui.showErrors) f.problem?.let { Banner(it, Tone.ERROR) }
        ui.error?.let { Banner(it, Tone.ERROR) }

        PrimaryButton(
            "Post job",
            loading = ui.posting,
            onClick = {
                // Form problems show inline without a dialog; only a valid form asks for confirmation
                if (f.problem != null) onPost()
                else confirm = ConfirmRequest(
                    "Post \"${f.title.trim()}\"?",
                    "It goes live in the feed right away as posted by a verified ${v.companyName} employee. " +
                        "Make sure it's a real, publicly listed opening.",
                    "Post job", onConfirm = onPost)
            },
        )
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
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
