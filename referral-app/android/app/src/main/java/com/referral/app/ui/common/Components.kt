package com.referral.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.referral.app.ui.theme.Space
import com.referral.app.ui.theme.extra

// ---------------------------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------------------------

/**
 * Brand gradient block at the top of a screen, under the status bar, with rounded bottom corners.
 * Used by the feed and the sign-in screen.
 */
@Composable
fun HeroHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(MaterialTheme.extra.heroGradient)
            .statusBarsPadding()
            .padding(horizontal = Space.screen, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        content = content,
    )
}

/** Top bar for inner screens: large bold title, optional back arrow, blends into the background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** White card with a hairline border; the app's basic container. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.extra.cardBorder)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(Space.sm), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = colors, border = border, elevation = elevation, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = colors, border = border, elevation = elevation, content = inner)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeCap = StrokeCap.Round)
    }
}

/** Friendly empty or error state: icon in a soft circle, title, body, optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(Space.xs))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        action?.let { Spacer(Modifier.height(Space.xs)); it() }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

private val ButtonHeight = 52.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Space.xl),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.sm)) }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Space.sm)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
    }
}

/** Compact button for card actions (Shortlist, Still hiring…). [filled] for the main one. */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = false,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    if (filled) {
        Button(
            onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    } else {
        TextButton(
            onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.textButtonColors(contentColor = color),
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    }
}

// ---------------------------------------------------------------------------------------------
// Badges and pills
// ---------------------------------------------------------------------------------------------

@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(14.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/** Neutral metadata pill: location, experience, work mode. */
@Composable
fun MetaPill(text: String, icon: ImageVector? = null) =
    Pill(text, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, icon = icon)

@Composable
fun VerifiedBadge(text: String = "Verified employee") =
    Pill(text, MaterialTheme.extra.successContainer, MaterialTheme.extra.onSuccessContainer, icon = Icons.Filled.CheckCircle)

/** Match score: green from 75, amber from 50, neutral below. */
@Composable
fun MatchBadge(score: Int, modifier: Modifier = Modifier) {
    val (bg, fg) = when {
        score >= 75 -> MaterialTheme.extra.successContainer to MaterialTheme.extra.onSuccessContainer
        score >= 50 -> MaterialTheme.extra.warningContainer to MaterialTheme.extra.onWarningContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill("$score% match", bg, fg, modifier)
}

/** Application status colours, so a list of applications can be scanned at a glance. */
@Composable
fun StatusPill(status: String?, label: String) {
    val x = MaterialTheme.extra
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        "offer" -> x.successContainer to x.onSuccessContainer
        "interview", "referred" -> c.primaryContainer to c.onPrimaryContainer
        "shortlisted" -> x.infoContainer to x.onInfoContainer
        "applied" -> c.secondaryContainer to c.onSecondaryContainer
        "waitlisted" -> x.warningContainer to x.onWarningContainer
        "declined", "not_selected" -> c.errorContainer to c.onErrorContainer
        else -> c.surfaceVariant to c.onSurfaceVariant
    }
    Pill(label, bg, fg)
}

/** Rounded square with the company's initials in a colour picked from its name. */
@Composable
fun CompanyAvatar(name: String?, size: Dp = 44.dp) {
    val initials = name.orEmpty().split(' ', '-', '.').filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    val palette = listOf(
        Color(0xFF0F766E) to Color(0xFFCCF5EF), Color(0xFF1E3A8A) to Color(0xFFDDE5FF),
        Color(0xFFB45309) to Color(0xFFFEF3C7), Color(0xFF7C3AED) to Color(0xFFEDE9FE),
        Color(0xFFBE123C) to Color(0xFFFFE4E6), Color(0xFF0369A1) to Color(0xFFE0F2FE),
    )
    val (fg, bg) = palette[(name.orEmpty().hashCode() and 0x7fffffff) % palette.size]
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.36f).sp)
    }
}

/** Circular avatar with a person's initial (header, profile). */
@Composable
fun PersonAvatar(name: String?, size: Dp = 40.dp, container: Color = MaterialTheme.extra.accent, content: Color = MaterialTheme.extra.onAccent) {
    Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Text(name?.trim()?.firstOrNull()?.uppercase() ?: "🙂", color = content, fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.42f).sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------------------------

enum class Tone { INFO, SUCCESS, WARNING, ERROR }

/** Inline message box. Use instead of loose red/primary text lines. */
@Composable
fun Banner(text: String, tone: Tone = Tone.INFO, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val x = MaterialTheme.extra
    val c = MaterialTheme.colorScheme
    val (bg, fg, icon) = when (tone) {
        Tone.INFO -> Triple(x.infoContainer, x.onInfoContainer, Icons.Filled.Info)
        Tone.SUCCESS -> Triple(x.successContainer, x.onSuccessContainer, Icons.Filled.CheckCircle)
        Tone.WARNING -> Triple(x.warningContainer, x.onWarningContainer, Icons.Filled.Warning)
        Tone.ERROR -> Triple(c.errorContainer, c.onErrorContainer, Icons.Filled.Warning)
    }
    Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
            }
            action?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { it() } }
        }
    }
}

/** Big number with a caption, for stats rows. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val fg = if (onHero) MaterialTheme.extra.onHero else MaterialTheme.colorScheme.onSurface
    val muted = if (onHero) MaterialTheme.extra.onHeroMuted else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = fg)
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

/** Score ring for match cards. */
@Composable
fun ScoreRing(score: Int, size: Dp = 52.dp) {
    val color = when {
        score >= 75 -> MaterialTheme.extra.success
        score >= 50 -> MaterialTheme.extra.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(size),
            color = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 5.dp,
        )
        CircularProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = 5.dp,
            strokeCap = StrokeCap.Round,
        )
        Text("$score", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * While shown, status-bar icons are light (for screens whose top is the dark brand gradient).
 * Restores the theme's default when the screen leaves.
 */
@Composable
fun LightStatusBarIcons() {
    val view = androidx.compose.ui.platform.LocalView.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    if (view.isInEditMode) return
    androidx.compose.runtime.DisposableEffect(view, darkTheme) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !darkTheme }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
