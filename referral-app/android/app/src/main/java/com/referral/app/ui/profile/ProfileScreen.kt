package com.referral.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.referral.app.resume.Skills
import com.referral.app.ui.common.AppCard
import com.referral.app.ui.common.AppTopBar
import com.referral.app.ui.common.Banner
import com.referral.app.ui.common.ConfirmDialog
import com.referral.app.ui.common.ConfirmRequest
import com.referral.app.ui.common.LoadingBox
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.PersonAvatar
import com.referral.app.ui.common.PrimaryButton
import com.referral.app.ui.common.Tone
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

/**
 * One screen for three uses: first-time setup (no privacy/account sections), the Profile tab
 * ([isTab]: adds Account with admin and sign out), and "complete your profile" from a job ([onBack] set).
 */
@Composable
fun ProfileScreen(
    onSaved: () -> Unit,
    showPrivacy: Boolean = true,
    isTab: Boolean = false,
    onBack: (() -> Unit)? = null,
    onAdmin: () -> Unit = {},
    onHowItWorks: () -> Unit = {},
    onSignOut: () -> Unit = {},
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
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmDialog(confirm, onDismiss = { confirm = null })

    LaunchedEffect(ui.pendingExport) {
        if (ui.pendingExport != null) saveExport.launch("my-referral-data.json")
    }
    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            viewModel.onSavedHandled()
            onSaved()
        }
    }

    val firstTime = !showPrivacy
    Scaffold(
        topBar = {
            AppTopBar(
                if (firstTime) "Set up your profile" else "Profile",
                onBack = onBack,
                subtitle = if (firstTime) "Takes a minute. Your resume does most of it." else null,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (ui.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            if (!firstTime) SummaryCard(ui)

            ResumeCard(ui, onPick = { pickPdf.launch(arrayOf("application/pdf")) })

            AppCard {
                Text("About you", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(ui.fullName, viewModel::onName, label = { Text("Full name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(ui.city, viewModel::onCity, label = { Text("City") },
                        singleLine = true, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(
                        ui.experienceText, viewModel::onExperience, label = { Text("Years") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SkillsCard(ui.skillsText, viewModel::onSkills)

            AppCard {
                Text("Contact for referrals", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Referrers need these for their company's referral form. They only see them after shortlisting you.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            }

            ui.error?.let { Banner(it, Tone.ERROR) }
            if (isTab) ui.savedNote?.let { Banner(it, Tone.SUCCESS) }
            if (!ui.hasResume) Banner("Upload your resume to save. Referrers need it to refer you.", Tone.INFO)
            PrimaryButton(if (firstTime) "Save and start" else "Save profile", viewModel::save,
                enabled = ui.canSave, loading = ui.saving)

            if (showPrivacy) {
                AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.sm)) {
                    Text("Your data", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs))
                    MenuRow(Icons.Filled.Share, if (ui.exporting) "Preparing…" else "Download my data",
                        "A copy of everything we store about you", onClick = viewModel::prepareExport)
                    HorizontalDivider(color = MaterialTheme.extra.cardBorder)
                    MenuRow(Icons.Filled.Delete, if (ui.deleting) "Deleting…" else "Delete my account",
                        "Removes your profile, resume, posts and applications", destructive = true,
                        onClick = { confirmDelete = true })
                    ui.privacyMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs))
                    }
                }
            }
            if (isTab) {
                AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.sm)) {
                    MenuRow(Icons.Filled.Info, "How the app works", "Tips for seekers and referrers", onClick = onHowItWorks)
                    HorizontalDivider(color = MaterialTheme.extra.cardBorder)
                    if (ui.isAdmin) {
                        MenuRow(Icons.Filled.Build, "Admin", "Reports and company requests", onClick = onAdmin)
                        HorizontalDivider(color = MaterialTheme.extra.cardBorder)
                    }
                    MenuRow(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", null, destructive = true, onClick = {
                        confirm = ConfirmRequest(
                            "Sign out?", "You'll stop getting notifications on this phone until you sign in again.",
                            "Sign out", destructive = true, onConfirm = onSignOut)
                    })
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.deleteAccount() },
        )
    }
}

@Composable
private fun SummaryCard(ui: ProfileUiState) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.extra.heroGradient).padding(Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        PersonAvatar(ui.fullName, size = 60.dp)
        Column(Modifier.weight(1f)) {
            Text(ui.fullName.ifBlank { "Your name" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.extra.onHero)
            Text(
                listOfNotNull(ui.experienceYears?.let { "$it yrs experience" }, ui.city.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extra.onHeroMuted,
            )
        }
    }
}

@Composable
private fun ResumeCard(ui: ProfileUiState, onPick: () -> Unit) {
    AppCard(onClick = if (ui.uploading) null else onPick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(
                Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)
                    .background(if (ui.hasResume) MaterialTheme.extra.successContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    ui.uploading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    ui.hasResume -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.extra.success)
                    else -> Icon(Icons.Filled.AccountBox, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        ui.uploading -> "Uploading and reading…"
                        ui.hasResume -> "Resume uploaded"
                        else -> "Upload your resume"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (ui.hasResume) "Tap to replace (PDF, max 5 MB)" else "PDF, max 5 MB. We'll fill in the rest for you.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ui.autofillNote?.let { Banner(it, if (it.startsWith("Filled")) Tone.SUCCESS else Tone.INFO) }
            ?: Text("Your resume is read on this phone. The text isn't sent anywhere.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Skills as an editable comma list, previewed as pills in the app's standard spelling. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsCard(text: String, onChange: (String) -> Unit) {
    AppCard {
        Text("Skills", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            text, onChange, label = { Text("Comma-separated") },
            placeholder = { Text("Kotlin, Jetpack Compose, Android") },
            minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
        val list = Skills.normalizeList(text)
        if (list.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
        Text("Used to rank you for jobs, with your years of experience.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(
                    if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Typing DELETE makes an accidental tap impossible; the server checks the same word. */
@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
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
