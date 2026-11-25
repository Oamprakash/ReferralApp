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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.referral.app.analytics.Analytics
import com.referral.app.ui.common.LoadingBox
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.referral.app.ui.onboarding.OnboardingScreen
import com.referral.app.ui.profile.ProfileScreen
import com.referral.app.ui.referrer.ApplicantsScreen
import com.referral.app.ui.referrer.MyPostsScreen
import com.referral.app.ui.referrer.PostJobScreen
import com.referral.app.ui.referrer.VerifyWorkEmailScreen
import com.referral.app.ui.seeker.ApplyScreen
import com.referral.app.ui.seeker.JobDetailScreen
import com.referral.app.ui.seeker.MyApplicationsScreen
import kotlinx.serialization.Serializable
import com.referral.app.links.PendingJobLink

/** [returnAfterSave]: opened from a job or Best matches to fill gaps; saving goes back there. */
@Serializable data class ProfileRoute(val returnAfterSave: Boolean = false)
@Serializable data object HomeRoute
@Serializable data object VerifyRoute
/** [forReferrerId]: an admin posting on behalf of that verified employee (the post is theirs). */
@Serializable data class PostJobRoute(val forReferrerId: String? = null)
@Serializable data object MyPostsRoute
@Serializable data object MyApplicationsRoute
@Serializable data class JobDetailRoute(val jobId: String)
@Serializable data class ApplyRoute(val jobId: String)
@Serializable data class ApplicantsRoute(val jobId: String)
@Serializable data object AdminRoute
@Serializable data object HowItWorksRoute

@Composable
fun AppRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showTour by viewModel.showTour.collectAsStateWithLifecycle()
    val tourReplay by viewModel.tourReplay.collectAsStateWithLifecycle()

    when (val s = state) {
        RootState.Loading -> LoadingBox()
        RootState.SignedOut -> if (showTour) OnboardingScreen(onDone = viewModel::finishTour, replay = tourReplay)
            else SignInScreen(onHowItWorks = viewModel::replayTour)
        is RootState.SignedIn -> SignedInGraph(onSignOut = viewModel::signOut)
        is RootState.Suspended -> SuspendedScreen(reason = s.reason, onSignOut = viewModel::signOut)
    }
}

/** The bottom tabs ("For you" is now the Best matches view inside Jobs). Each keeps its own scroll/back stack when you switch away and back. */
private enum class Tab(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val isAt: (NavDestination) -> Boolean,      // reified hasRoute: safe with R8 renaming classes
) {
    JOBS("Jobs", Icons.Filled.Home, HomeRoute, { it.hasRoute<HomeRoute>() }),
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
private fun SignedInGraph(
    onSignOut: () -> Unit,
    tabsViewModel: TabsViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    val showPosts by tabsViewModel.showPosts.collectAsStateWithLifecycle()
    val tabs = Tab.entries.filter { it != Tab.POSTS || showPosts }
    // Verification can expire while the app is open: re-check whenever it comes back to the front
    LifecycleResumeEffect(Unit) {
        tabsViewModel.refresh()
        onPauseOrDispose { }
    }
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    // One screen_view per navigation (re-opening a tab counts again). Names are explicit because R8
    // renames the route classes in release builds.
    LaunchedEffect(entry?.id) { dest?.let(::screenName)?.let { Analytics.screen(it) } }
    fun openJob(id: String, from: String) {
        Analytics.jobOpened(from)
        nav.navigate(JobDetailRoute(id))
    }
    // A shared job link: open it once the signed-in screens are up (after sign-in if needed)
    val linkedJob by PendingJobLink.jobId.collectAsStateWithLifecycle()
    LaunchedEffect(linkedJob) {
        linkedJob?.let { id ->
            PendingJobLink.consume()
            openJob(id, "Link")
        }
    }
    val isProfileTab = dest?.hasRoute<ProfileRoute>() == true &&
        entry?.toRoute<ProfileRoute>()?.returnAfterSave == false && nav.previousBackStackEntry != null
    val showBar = isProfileTab || listOf(Tab.JOBS, Tab.APPLIED, Tab.POSTS).any { tab ->
        dest?.hierarchy?.any { d -> tab.isAt(d) } == true
    }
    // Tab just disappeared (verification expired) while it was open: go to Jobs
    LaunchedEffect(showPosts, dest) {
        if (!showPosts && dest?.hasRoute<MyPostsRoute>() == true) nav.switchTab(Tab.JOBS)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),   // each screen handles its own top inset
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val selected = if (tab == Tab.PROFILE) isProfileTab
                            else dest?.hierarchy?.any { tab.isAt(it) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),   // soft brand teal
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
            startDestination = HomeRoute,   // browse first; the resume is asked for on Jobs and when applying
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
                    onHowItWorks = { nav.navigate(HowItWorksRoute) },
                    onPostJob = if (showPosts) null else ({ nav.navigate(PostJobRoute()) }),
                    onSignOut = onSignOut,
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    onOpenJob = { id, from -> openJob(id, from) },
                    onEditProfile = { nav.navigate(ProfileRoute(returnAfterSave = true)) },
                    onProfile = { nav.switchTab(Tab.PROFILE) },
                    showPostPrompt = !showPosts,
                    onPostJob = { nav.navigate(PostJobRoute()) },
                )
            }
            composable<MyApplicationsRoute> {
                MyApplicationsScreen(
                    onOpenJob = { id -> openJob(id, "Applied") },
                    onBrowse = { nav.switchTab(Tab.JOBS) },
                )
            }
            composable<MyPostsRoute> {
                MyPostsScreen(
                    onPostJob = { nav.navigate(PostJobRoute()) },
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
            composable<PostJobRoute> { entry ->
                val forReferrer = entry.toRoute<PostJobRoute>().forReferrerId
                PostJobScreen(
                    onBack = { nav.popBackStack() },
                    onNeedsVerification = { nav.navigate(VerifyRoute) },
                    onPosted = {
                        if (forReferrer != null) { nav.popBackStack(); return@PostJobScreen }   // back to Admin
                        val cameFromMyPosts = nav.previousBackStackEntry?.destination?.hasRoute<MyPostsRoute>() == true
                        if (cameFromMyPosts) nav.popBackStack()
                        else nav.navigate(MyPostsRoute) { popUpTo<PostJobRoute> { inclusive = true } }
                    },
                )
            }
            composable<VerifyRoute> {
                VerifyWorkEmailScreen(
                    onBack = { nav.popBackStack() },
                    onVerified = {
                        tabsViewModel.markReferrer()                    // "My posts" tab appears
                        nav.popBackStack()                              // back to Post a job, which re-checks
                    },
                )
            }
            composable<ApplicantsRoute> {
                ApplicantsScreen(onBack = { nav.popBackStack() })
            }
            composable<HowItWorksRoute> {
                OnboardingScreen(onDone = { nav.popBackStack() }, replay = true)
            }
            composable<AdminRoute> {
                AdminScreen(onBack = { nav.popBackStack() }, onPostFor = { id -> nav.navigate(PostJobRoute(forReferrerId = id)) })
            }
        }
    }
}

/** Readable analytics screen names, matched by type so they survive R8. */
private fun screenName(d: NavDestination): String? = when {
    d.hasRoute<HomeRoute>() -> "Jobs"
    d.hasRoute<MyApplicationsRoute>() -> "Applied"
    d.hasRoute<MyPostsRoute>() -> "MyPosts"
    d.hasRoute<ProfileRoute>() -> "Profile"
    d.hasRoute<JobDetailRoute>() -> "JobDetail"
    d.hasRoute<ApplyRoute>() -> "Apply"
    d.hasRoute<ApplicantsRoute>() -> "Applicants"
    d.hasRoute<PostJobRoute>() -> "PostJob"
    d.hasRoute<VerifyRoute>() -> "Verify"
    d.hasRoute<AdminRoute>() -> "Admin"
    d.hasRoute<HowItWorksRoute>() -> "HowItWorks"
    else -> null
}
