package com.referral.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.referral.app.R
import com.referral.app.ui.common.CompanyAvatar
import com.referral.app.ui.common.LightStatusBarIcons
import com.referral.app.ui.common.MetaPill
import com.referral.app.ui.common.Pill
import com.referral.app.ui.common.PrimaryButton
import com.referral.app.ui.common.ScoreRing
import com.referral.app.ui.common.StatTile
import com.referral.app.ui.common.StatusPill
import com.referral.app.ui.common.VerifiedBadge
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra
import kotlinx.coroutines.launch

/** One page of the tour: a picture built from the app's own components, a title, a line, and how-to tips. */
private data class TourPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val tips: List<String>,
    val art: @Composable () -> Unit,
)

private val pages = listOf(
    TourPage(
        eyebrow = "Welcome",
        title = "Referrals from people who work there",
        body = "Every opening here is posted by a verified employee of that company, not a recruiter or a job board.",
        tips = listOf(
            "Posts link to the company's careers page or job ID, so you can check they're real",
            "Each post is re-confirmed every 14 days, so roles are still open",
        ),
        art = { WelcomeArt() },
    ),
    TourPage(
        eyebrow = "For job seekers",
        title = "Upload your resume, see your best matches",
        body = "Your resume fills in your profile on your phone. Open roles are then ranked by how well your skills and experience fit.",
        tips = listOf(
            "Open the For you tab to see your score for each role and the skills you're missing",
            "Keep your skills and years up to date; referrers see the same score",
        ),
        art = { MatchArt() },
    ),
    TourPage(
        eyebrow = "Apply and track",
        title = "Ask for a referral, follow every step",
        body = "Each post takes a limited number of applicants, so the employee can actually review yours. You get a notification at every step.",
        tips = listOf(
            "You can send 10 applications a week, so pick roles that fit",
            "Answer screening questions properly; they're often the deciding factor",
            "Track everything in the Applied tab",
        ),
        art = { TrackArt() },
    ),
    TourPage(
        eyebrow = "For employees",
        title = "Hiring at your company? Refer great people",
        body = "Verify your work email once, post openings in a minute, and review applicants ranked by match.",
        tips = listOf(
            "Go to My posts and tap Post a job; verification lasts 90 days",
            "Shortlist, then submit the referral in your company's portal and tap Mark referred",
            "Tap Still hiring every two weeks to keep a post live",
        ),
        art = { ReferrerArt() },
    ),
    TourPage(
        eyebrow = "Safe and free",
        title = "Referrals are always free",
        body = "No one can charge you for a referral. Report anyone who asks for money and the post is hidden right away.",
        tips = listOf(
            "Your resume is shown only to employees whose jobs you apply to",
            "Your contact details are shared only after you're shortlisted",
            "Download or delete your data any time from Profile",
        ),
        art = { SafeArt() },
    ),
)

/**
 * The welcome tour. Shown once before sign-in on a new phone, and any time from Profile → How the app works
 * ([replay] = true changes the last button to "Done").
 */
@OptIn(ExperimentalFoundationApi::class)   // no-op where the pager is already stable
@Composable
fun OnboardingScreen(onDone: () -> Unit, replay: Boolean = false) {
    LightStatusBarIcons()
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            PageContent(pages[index], onSkip = if (index < pages.lastIndex) onDone else null, replay = replay)
        }

        // Dots + main button
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.screen, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    val selected = i == pager.currentPage
                    val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "dotColor",
                    )
                    Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
                }
            }
            PrimaryButton(
                text = when {
                    !last -> "Next"
                    replay -> "Done"
                    else -> "Get started"
                },
                icon = if (last) null else Icons.AutoMirrored.Filled.ArrowForward,
                onClick = {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageContent(page: TourPage, onSkip: (() -> Unit)?, replay: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Gradient header with the picture
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(MaterialTheme.extra.heroGradient)
                .statusBarsPadding()
                .padding(horizontal = Space.screen)
                .padding(top = Space.sm, bottom = Space.xxl),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(page.eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.extra.accent, modifier = Modifier.weight(1f))
                    if (onSkip != null) {
                        TextButton(onClick = onSkip) {
                            Text(if (replay) "Close" else "Skip", color = MaterialTheme.extra.onHero)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) { page.art() }
            }
        }

        Column(
            Modifier.padding(horizontal = Space.screen, vertical = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(page.title, style = MaterialTheme.typography.headlineMedium)
            Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.xs))
            page.tips.forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Text(tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pictures, drawn with the app's own components so they always match the real screens
// ---------------------------------------------------------------------------------------------

/** A small white card floating on the gradient. */
@Composable
private fun MiniCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.width(280.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm), content = content)
    }
}

@Composable
private fun JobHeader(company: String, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        CompanyAvatar(company, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.extra.onHero.copy(alpha = 0.12f)),
        )
        MiniCard {
            JobHeader("Kaveri Cloud", "Senior Android Developer", "Kaveri Cloud · Chennai")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { VerifiedBadge("Verified employee") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchArt() {
    MiniCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { JobHeader("Marina Fintech", "Android Engineer", "Marina Fintech") }
            ScoreRing(92)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Kotlin", "Jetpack Compose", "Android").forEach {
                Pill("✓ $it", MaterialTheme.extra.successContainer, MaterialTheme.extra.onSuccessContainer)
            }
            MetaPill("Coroutines")
        }
    }
}

@Composable
private fun TrackArt() {
    val labels = listOf("Applied", "Shortlisted", "Referred", "Interview", "Offer")
    val reached = 2
    MiniCard {
        JobHeader("Tidel Labs", "Frontend Developer", "Tidel Labs")
        StatusPill("referred", "Referred to the company")
        Row(verticalAlignment = Alignment.CenterVertically) {
            labels.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == reached) 12.dp else 10.dp).clip(CircleShape).background(
                        if (i <= reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
                if (i < labels.lastIndex) {
                    Box(Modifier.weight(1f).height(3.dp).background(
                        if (i < reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
        Row {
            labels.forEachIndexed { i, l ->
                Text(l, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f),
                    color = if (i <= reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReferrerArt() {
    MiniCard {
        Text("Your impact", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth()) {
            StatTile("12", "Referred", Modifier.weight(1f))
            StatTile("5", "Interviews", Modifier.weight(1f))
            StatTile("2", "Offers", Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Senior Android Developer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Pill("4 new", MaterialTheme.extra.accent, MaterialTheme.extra.onAccent)
        }
    }
}

@Composable
private fun SafeArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.extra.onHero.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.extra.accent, modifier = Modifier.size(48.dp)) }
        Text(
            "₹0 for a referral. Always.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.extra.onHero,
            textAlign = TextAlign.Center,
        )
    }
}
