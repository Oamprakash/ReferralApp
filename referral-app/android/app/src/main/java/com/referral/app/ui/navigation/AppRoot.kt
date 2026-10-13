package com.referral.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.referral.app.ui.common.LoadingBox
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.referral.app.ui.admin.AdminScreen
import com.referral.app.ui.auth.SignInScreen
import com.referral.app.ui.auth.SuspendedScreen
import com.referral.app.ui.home.HomeScreen
import com.referral.app.ui.matches.MatchesScreen
import com.referral.app.ui.profile.ProfileScreen
import com.referral.app.ui.referrer.ApplicantsScreen
import com.referral.app.ui.referrer.MyPostsScreen
import com.referral.app.ui.referrer.PostJobScreen
import com.referral.app.ui.referrer.VerifyWorkEmailScreen
import com.referral.app.ui.seeker.ApplyScreen
import com.referral.app.ui.seeker.JobDetailScreen
import com.referral.app.ui.seeker.MyApplicationsScreen
import kotlinx.serialization.Serializable

/** [returnAfterSave]: opened from a job or Best matches to fill gaps; saving goes back there. */
@Serializable data class ProfileRoute(val returnAfterSave: Boolean = false)
@Serializable data object HomeRoute
@Serializable data object VerifyRoute
@Serializable data object PostJobRoute
@Serializable data object MyPostsRoute
@Serializable data object MyApplicationsRoute
@Serializable data class JobDetailRoute(val jobId: String)
@Serializable data class ApplyRoute(val jobId: String)
@Serializable data class ApplicantsRoute(val jobId: String)
@Serializable data object AdminRoute
@Serializable data object MatchesRoute

@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        RootState.Loading -> LoadingBox()
        RootState.SignedOut -> SignInScreen()
        is RootState.SignedIn -> SignedInGraph(startAtProfile = s.needsProfile, onSignOut = viewModel::signOut)
        is RootState.Suspended -> SuspendedScreen(reason = s.reason, onSignOut = viewModel::signOut)
    }
}

/** The five bottom tabs. Each keeps its own scroll/back stack when you switch away and back. */
private enum class Tab(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val isAt: (NavDestination) -> Boolean,      // reified hasRoute: safe with R8 renaming classes
) {
    JOBS("Jobs", Icons.Filled.Home, HomeRoute, { it.hasRoute<HomeRoute>() }),
    MATCHES("For you", Icons.Filled.Star, MatchesRoute, { it.hasRoute<MatchesRoute>() }),
    APPLIED("Applied", Icons.AutoMirrored.Filled.List, MyApplicationsRoute, { it.hasRoute<MyApplicationsRoute>() }),
    POSTS("My posts", Icons.Filled.Create, MyPostsRoute, { it.hasRoute<MyPostsRoute>() }),
    PROFILE("Profile", Icons.Filled.Person, ProfileRoute(), { it.hasRoute<ProfileRoute>() }),
}

private fun NavHostController.switchTab(tab: Tab) = navigate(tab.route) {
    popUpTo<HomeRoute> { saveState = true }   // Home is always the root once set up
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun SignedInGraph(startAtProfile: Boolean, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val isProfileTab = dest?.hasRoute<ProfileRoute>() == true &&
        entry?.toRoute<ProfileRoute>()?.returnAfterSave == false && nav.previousBackStackEntry != null
    val showBar = isProfileTab || listOf(Tab.JOBS, Tab.MATCHES, Tab.APPLIED, Tab.POSTS).any { tab ->
        dest?.hierarchy?.any { d -> tab.isAt(d) } == true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),   // each screen handles its own top inset
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        val selected = if (tab == Tab.PROFILE) isProfileTab
                            else dest?.hierarchy?.any { tab.isAt(it) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        val barPadding = PaddingValues(bottom = inner.calculateBottomPadding())
        NavHost(
            navController = nav,
            startDestination = if (startAtProfile) ProfileRoute() else HomeRoute,
            modifier = Modifier.padding(barPadding).consumeWindowInsets(barPadding),
        ) {
            composable<ProfileRoute> { e ->
                val route = e.toRoute<ProfileRoute>()
                val firstTime = nav.previousBackStackEntry == null
                ProfileScreen(
                    showPrivacy = !firstTime,
                    isTab = !firstTime && !route.returnAfterSave,
                    onBack = if (route.returnAfterSave) ({ nav.popBackStack(); Unit }) else null,
                    onSaved = {
                        when {
                            firstTime -> nav.navigate(HomeRoute) { popUpTo<ProfileRoute> { inclusive = true } }
                            route.returnAfterSave -> nav.popBackStack()
                            else -> Unit                       // tab: stays, shows "Saved"
                        }
                    },
                    onAdmin = { nav.navigate(AdminRoute) },
                    onSignOut = onSignOut,
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    onOpenJob = { id -> nav.navigate(JobDetailRoute(id)) },
                    onMatches = { nav.switchTab(Tab.MATCHES) },
                    onProfile = { nav.switchTab(Tab.PROFILE) },
                )
            }
            composable<MatchesRoute> {
                MatchesScreen(
                    onOpenJob = { id -> nav.navigate(JobDetailRoute(id)) },
                    onEditProfile = { nav.navigate(ProfileRoute(returnAfterSave = true)) },
                )
            }
            composable<MyApplicationsRoute> {
                MyApplicationsScreen(
                    onOpenJob = { id -> nav.navigate(JobDetailRoute(id)) },
                    onBrowse = { nav.switchTab(Tab.JOBS) },
                )
            }
            composable<MyPostsRoute> {
                MyPostsScreen(
                    onPostJob = { nav.navigate(PostJobRoute) },
                    onApplicants = { id -> nav.navigate(ApplicantsRoute(id)) },
                    onVerify = { nav.navigate(VerifyRoute) },
                )
            }
            composable<JobDetailRoute> {
                JobDetailScreen(
                    onBack = { nav.popBackStack() },
                    onApply = { id -> nav.navigate(ApplyRoute(id)) },
                    onCompleteProfile = { nav.navigate(ProfileRoute(returnAfterSave = true)) },
                )
            }
            composable<ApplyRoute> {
                ApplyScreen(
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },                       // back to the job, which reloads
                )
            }
            composable<PostJobRoute> {
                PostJobScreen(
                    onBack = { nav.popBackStack() },
                    onNeedsVerification = { nav.navigate(VerifyRoute) },
                    onPosted = {
                        val cameFromMyPosts = nav.previousBackStackEntry?.destination?.hasRoute<MyPostsRoute>() == true
                        if (cameFromMyPosts) nav.popBackStack()
                        else nav.navigate(MyPostsRoute) { popUpTo(PostJobRoute) { inclusive = true } }
                    },
                )
            }
            composable<VerifyRoute> {
                VerifyWorkEmailScreen(
                    onBack = { nav.popBackStack() },
                    onVerified = { nav.popBackStack() },           // back to Post a job, which re-checks
                )
            }
            composable<ApplicantsRoute> {
                ApplicantsScreen(onBack = { nav.popBackStack() })
            }
            composable<AdminRoute> {
                AdminScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
