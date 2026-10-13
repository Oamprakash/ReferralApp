package com.referral.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

@Serializable data object ProfileRoute
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
        RootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        RootState.SignedOut -> SignInScreen()
        is RootState.SignedIn -> SignedInGraph(startAtProfile = s.needsProfile)
        is RootState.Suspended -> SuspendedScreen(reason = s.reason, onSignOut = viewModel::signOut)
    }
}

@Composable
private fun SignedInGraph(startAtProfile: Boolean) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = if (startAtProfile) ProfileRoute else HomeRoute) {
        composable<ProfileRoute> {
            ProfileScreen(
                // Opened from Home's menu = existing user; no back stack = first-time setup
                showPrivacy = nav.previousBackStackEntry != null,
                onSaved = {
                    if (nav.previousBackStackEntry != null) {
                        nav.popBackStack()                     // came from Home via "Edit profile"
                    } else {
                        nav.navigate(HomeRoute) {              // first-time setup
                            popUpTo(ProfileRoute) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenJob = { id -> nav.navigate(JobDetailRoute(id)) },
                onEditProfile = { nav.navigate(ProfileRoute) },
                onPostJob = { nav.navigate(PostJobRoute) },
                onMyPosts = { nav.navigate(MyPostsRoute) { launchSingleTop = true } },
                onMyApplications = { nav.navigate(MyApplicationsRoute) { launchSingleTop = true } },
                onAdmin = { nav.navigate(AdminRoute) { launchSingleTop = true } },
                onMatches = { nav.navigate(MatchesRoute) { launchSingleTop = true } },
            )
        }
        composable<JobDetailRoute> {
            JobDetailScreen(
                onBack = { nav.popBackStack() },
                onApply = { id -> nav.navigate(ApplyRoute(id)) },
                onCompleteProfile = { nav.navigate(ProfileRoute) },   // saving pops back here
            )
        }
        composable<ApplyRoute> {
            ApplyScreen(
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },                       // back to the job, which reloads
            )
        }
        composable<MyApplicationsRoute> {
            MyApplicationsScreen(
                onBack = { nav.popBackStack() },
                onOpenJob = { id -> nav.navigate(JobDetailRoute(id)) },
            )
        }
        composable<PostJobRoute> {
            PostJobScreen(
                onBack = { nav.popBackStack() },
                onNeedsVerification = { nav.navigate(VerifyRoute) },
                onPosted = {
                    val cameFromMyPosts = nav.previousBackStackEntry?.destination?.hasRoute<MyPostsRoute>() == true
                    if (cameFromMyPosts) {
                        nav.popBackStack()
                    } else {
                        nav.navigate(MyPostsRoute) { popUpTo(PostJobRoute) { inclusive = true } }
                    }
                },
            )
        }
        composable<VerifyRoute> {
            VerifyWorkEmailScreen(
                onBack = { nav.popBackStack() },
                onVerified = { nav.popBackStack() },           // back to Post a job, which re-checks
            )
        }
        composable<MyPostsRoute> {
            MyPostsScreen(
                onBack = { nav.popBackStack() },
                onPostJob = { nav.navigate(PostJobRoute) },
                onApplicants = { id -> nav.navigate(ApplicantsRoute(id)) },
                onVerify = { nav.navigate(VerifyRoute) },
            )
        }
        composable<ApplicantsRoute> {
            ApplicantsScreen(onBack = { nav.popBackStack() })
        }
        composable<MatchesRoute> {
            MatchesScreen(
                onBack = { nav.popBackStack() },
                onOpenJob = { id -> nav.navigate(JobDetailRoute(id)) },
                onEditProfile = { nav.navigate(ProfileRoute) },
            )
        }
        composable<AdminRoute> {
            AdminScreen(onBack = { nav.popBackStack() })
        }
    }
}
