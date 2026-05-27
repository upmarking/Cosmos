package com.cosmos.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cosmos.app.screens.communities.CircleMembersScreen
import com.cosmos.app.screens.communities.CommunityHubScreen
import com.cosmos.app.screens.communities.ExploreCirclesScreen
import com.cosmos.app.screens.communities.PrivateCircleFeedScreen
import com.cosmos.app.screens.connect.DiscoveryDeckScreen
import com.cosmos.app.screens.connect.EndorseExpertiseScreen
import com.cosmos.app.screens.connect.FoundersCircleFeedScreen
import com.cosmos.app.screens.connect.MemberProfileScreen
import com.cosmos.app.screens.conversations.ConversationsListScreen
import com.cosmos.app.screens.conversations.RelationshipCrmChatScreen
import com.cosmos.app.screens.events.EventLobbyScreen
import com.cosmos.app.screens.events.EventsListScreen
import com.cosmos.app.screens.introductions.RequestWarmIntroScreen
import com.cosmos.app.screens.introductions.ReviewIntroRequestScreen
import com.cosmos.app.screens.introductions.ThreeWayIntroductionScreen
import com.cosmos.app.screens.onboarding.AiMatchingRefinementScreen
import com.cosmos.app.screens.onboarding.CompleteIdentityScreen
import com.cosmos.app.screens.onboarding.DefineIntentScreen
import com.cosmos.app.screens.onboarding.WelcomeScreen
import com.cosmos.app.screens.onboarding.YourVisionScreen
import com.cosmos.app.screens.profile.MembershipTiersScreen
import com.cosmos.app.screens.profile.NetworkingDashboardScreen
import com.cosmos.app.screens.profile.NotificationsCenterScreen
import com.cosmos.app.screens.profile.SettingsPrivacyScreen
import com.cosmos.app.screens.profile.EditProfileScreen

@Composable
fun CosmosNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Welcome.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(300))
        }
    ) {
        // ── Onboarding ──────────────────────────────────────────────────────
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Screen.CompleteIdentity.route) },
                onSignIn = { navController.navigate(Screen.Connect.route) { popUpTo(0) } }
            )
        }
        composable(Screen.WelcomeSignIn.route) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Screen.CompleteIdentity.route) },
                onSignIn = { navController.navigate(Screen.Connect.route) { popUpTo(0) } },
                initialShowSignIn = true
            )
        }
        composable(Screen.CompleteIdentity.route) {
            CompleteIdentityScreen(
                onNext = { navController.navigate(Screen.DefineIntent.route) },
                onBack = { navController.popBackStack() },
                onSignInInstead = {
                    navController.navigate(Screen.WelcomeSignIn.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = false }
                    }
                }
            )
        }
        composable(Screen.DefineIntent.route) {
            DefineIntentScreen(
                onNext = { navController.navigate(Screen.YourVision.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.YourVision.route) {
            YourVisionScreen(
                onNext = { navController.navigate(Screen.AiMatchingRefinement.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AiMatchingRefinement.route) {
            AiMatchingRefinementScreen(
                onFinish = {
                    navController.navigate(Screen.Connect.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Connect ──────────────────────────────────────────────────────────
        composable(Screen.Connect.route) {
            DiscoveryDeckScreen(
                onProfileTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                },
                onNavigateToFeed = { navController.navigate(Screen.FoundersCircleFeed.route) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.FoundersCircleFeed.route) {
            FoundersCircleFeedScreen(
                onProfileTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                },
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.MemberProfile.route,
            arguments = listOf(navArgument("memberId") { type = NavType.StringType })
        ) { backStack ->
            val memberId = backStack.arguments?.getString("memberId") ?: ""
            MemberProfileScreen(
                memberId = memberId,
                onBack = { navController.popBackStack() },
                onEndorse = { navController.navigate(Screen.EndorseExpertise.createRoute(memberId)) },
                onWarmIntro = { navController.navigate(Screen.RequestWarmIntro.createRoute(memberId)) },
                onStartChat = { navController.navigate(Screen.CrmChat.createRoute(memberId)) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.EndorseExpertise.route,
            arguments = listOf(navArgument("memberId") { type = NavType.StringType })
        ) { backStack ->
            val memberId = backStack.arguments?.getString("memberId") ?: ""
            EndorseExpertiseScreen(
                memberId = memberId,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
            )
        }

        // ── Introductions ─────────────────────────────────────────────────────
        composable(
            route = Screen.RequestWarmIntro.route,
            arguments = listOf(navArgument("memberId") { type = NavType.StringType })
        ) { backStack ->
            val memberId = backStack.arguments?.getString("memberId") ?: ""
            RequestWarmIntroScreen(
                memberId = memberId,
                onBack = { navController.popBackStack() },
                onSent = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.ReviewIntroRequest.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType })
        ) { backStack ->
            val requestId = backStack.arguments?.getString("requestId") ?: ""
            ReviewIntroRequestScreen(
                requestId = requestId,
                onBack = { navController.popBackStack() },
                onAccept = { introId ->
                    navController.navigate(Screen.ThreeWayIntroduction.createRoute(introId))
                },
                onDecline = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.ThreeWayIntroduction.route,
            arguments = listOf(navArgument("introId") { type = NavType.StringType })
        ) { backStack ->
            val introId = backStack.arguments?.getString("introId") ?: ""
            ThreeWayIntroductionScreen(
                introId = introId,
                onBack = { navController.popBackStack() },
                onDone = { navController.navigate(Screen.Connect.route) { popUpTo(Screen.Connect.route) } }
            )
        }

        // ── Events ────────────────────────────────────────────────────────────
        composable(Screen.Events.route) {
            EventsListScreen(
                onEventTap = { eventId ->
                    navController.navigate(Screen.EventLobby.createRoute(eventId))
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.EventLobby.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStack ->
            val eventId = backStack.arguments?.getString("eventId") ?: ""
            EventLobbyScreen(
                eventId = eventId,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        // ── Communities ───────────────────────────────────────────────────────
        composable(Screen.Communities.route) {
            CommunityHubScreen(
                onExplore = { navController.navigate(Screen.ExploreCircles.route) },
                onCircleTap = { circleId ->
                    navController.navigate(Screen.PrivateCircleFeed.createRoute(circleId))
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.ExploreCircles.route) {
            ExploreCirclesScreen(
                onBack = { navController.popBackStack() },
                onCircleTap = { circleId ->
                    navController.navigate(Screen.CircleMembers.createRoute(circleId))
                }
            )
        }
        composable(
            route = Screen.CircleMembers.route,
            arguments = listOf(navArgument("circleId") { type = NavType.StringType })
        ) { backStack ->
            val circleId = backStack.arguments?.getString("circleId") ?: ""
            CircleMembersScreen(
                circleId = circleId,
                onBack = { navController.popBackStack() },
                onMemberTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                },
                onFeedTap = { navController.navigate(Screen.PrivateCircleFeed.createRoute(circleId)) }
            )
        }
        composable(
            route = Screen.PrivateCircleFeed.route,
            arguments = listOf(navArgument("circleId") { type = NavType.StringType })
        ) { backStack ->
            val circleId = backStack.arguments?.getString("circleId") ?: ""
            PrivateCircleFeedScreen(
                circleId = circleId,
                onBack = { navController.popBackStack() },
                onMembersTap = { navController.navigate(Screen.CircleMembers.createRoute(circleId)) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        // ── Conversations ──────────────────────────────────────────────────────
        composable(Screen.Conversations.route) {
            ConversationsListScreen(
                onChatTap = { connectionId ->
                    navController.navigate(Screen.CrmChat.createRoute(connectionId))
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.CrmChat.route,
            arguments = listOf(navArgument("connectionId") { type = NavType.StringType })
        ) { backStack ->
            val connectionId = backStack.arguments?.getString("connectionId") ?: ""
            RelationshipCrmChatScreen(
                connectionId = connectionId,
                onBack = { navController.popBackStack() },
                onProfileTap = { memberId -> navController.navigate(Screen.MemberProfile.createRoute(memberId)) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        // ── Profile ────────────────────────────────────────────────────────────
        composable(Screen.Profile.route) {
            NetworkingDashboardScreen(
                onMembershipTap = { navController.navigate(Screen.MembershipTiers.route) },
                onSettingsTap = { navController.navigate(Screen.Settings.route) },
                onNotificationsTap = { navController.navigate(Screen.Notifications.route) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.MembershipTiers.route) {
            MembershipTiersScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Notifications.route) {
            NotificationsCenterScreen(
                onBack = { navController.popBackStack() },
                onIntroRequest = { requestId ->
                    navController.navigate(Screen.ReviewIntroRequest.createRoute(requestId))
                },
                onChatTap = { connectionId ->
                    navController.navigate(Screen.CrmChat.createRoute(connectionId))
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsPrivacyScreen(
                onBack = { navController.popBackStack() },
                onSignOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onEditProfileTap = {
                    navController.navigate(Screen.EditProfile.route)
                }
            )
        }
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
