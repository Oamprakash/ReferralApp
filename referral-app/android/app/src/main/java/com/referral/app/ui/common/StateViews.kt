package com.referral.app.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.referral.app.ui.theme.Space

// ---------- Loading: skeleton cards shaped like the real ones ----------

/** A grey block that gently pulses; the building piece of skeletons. */
@Composable
fun SkeletonBlock(width: Dp? = null, height: Dp = 14.dp, modifier: Modifier = Modifier, circle: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val shape = if (circle) CircleShape else RoundedCornerShape(6.dp)
    Box(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .height(height)
            .alpha(pulse)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Placeholder for a job / application / post card while the list loads. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.semantics { contentDescription = "Loading" }) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            SkeletonBlock(width = 40.dp, height = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SkeletonBlock(height = 16.dp, modifier = Modifier.fillMaxWidth(0.7f))
                SkeletonBlock(height = 12.dp, modifier = Modifier.fillMaxWidth(0.45f))
            }
        }
        SkeletonBlock(height = 12.dp, modifier = Modifier.fillMaxWidth(0.6f))
        Row {
            SkeletonBlock(width = 96.dp, height = 14.dp)
            Spacer(Modifier.weight(1f))
            SkeletonBlock(width = 72.dp, height = 12.dp)
        }
    }
}

/** [count] skeleton cards as list items. */
fun LazyListScope.skeletonItems(count: Int = 4, modifier: Modifier = Modifier.padding(horizontal = Space.screen)) {
    items(count) { SkeletonCard(modifier) }
}

// ---------- Error with retry, and offline ----------

/** Whether the phone has a working internet connection right now; updates live. */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(isOnline(cm)) }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = isOnline(cm) }
            override fun onLost(network: Network) { online = isOnline(cm) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { online = isOnline(cm) }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun isOnline(cm: ConnectivityManager): Boolean = runCatching {
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}.getOrDefault(true)

/**
 * Full-area error for when a screen couldn't load: a short message and a Try again button.
 * When the phone is offline it says so instead of the generic [message].
 */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val online = rememberIsOnline()
    EmptyState(
        icon = if (online) Icons.Filled.Warning else Icons.Filled.Refresh,
        title = if (online) message else "You're offline",
        body = if (online) "Check your connection and try again. If it keeps happening, tell us from Profile."
               else "Connect to the internet and try again.",
        modifier = modifier,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                onBack?.let { SmallButton("Back", it, outlined = true) }
                SmallButton("Try again", onRetry, filled = true)
            }
        },
    )
}

/** Thin strip shown above a list when the phone goes offline, so stale data isn't mistaken for live. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    if (!rememberIsOnline()) {
        Banner("You're offline. Showing what was loaded last.", Tone.WARNING, modifier)
    }
}
