package app.cosmos.com.navigation

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
import androidx.navigation.navDeepLink
import app.cosmos.com.screens.communities.OrbitMembersScreen
import app.cosmos.com.screens.communities.CommunityHubScreen
import app.cosmos.com.screens.communities.ExploreOrbitsScreen
import app.cosmos.com.screens.communities.PrivateOrbitFeedScreen
import app.cosmos.com.screens.communities.OrbitPostDetailScreen
import app.cosmos.com.screens.connect.DiscoveryDeckScreen
import app.cosmos.com.screens.connect.EndorseExpertiseScreen
import app.cosmos.com.screens.connect.ConnectionRequestsScreen
import app.cosmos.com.screens.connect.FoundersOrbitFeedScreen
import app.cosmos.com.screens.connect.MemberProfileScreen
import app.cosmos.com.screens.connect.SearchScreen
import app.cosmos.com.screens.conversations.ConversationsListScreen
import app.cosmos.com.screens.conversations.RelationshipCrmChatScreen
import app.cosmos.com.screens.events.EventLobbyScreen
import app.cosmos.com.screens.events.EventsListScreen
import app.cosmos.com.screens.introductions.RequestWarmIntroScreen
import app.cosmos.com.screens.introductions.ReviewIntroRequestScreen
import app.cosmos.com.screens.introductions.ThreeWayIntroductionScreen
import app.cosmos.com.screens.onboarding.AiMatchingRefinementScreen
import app.cosmos.com.screens.onboarding.CompleteIdentityScreen
import app.cosmos.com.screens.onboarding.DefineIntentScreen
import app.cosmos.com.screens.onboarding.ResetPasswordScreen
import app.cosmos.com.screens.onboarding.SplashScreen
import app.cosmos.com.screens.onboarding.VerifyEmailScreen
import app.cosmos.com.screens.onboarding.WelcomeScreen
import app.cosmos.com.screens.onboarding.YourVisionScreen
import app.cosmos.com.screens.profile.MembershipTiersScreen
import app.cosmos.com.screens.profile.NetworkingDashboardScreen
import app.cosmos.com.screens.profile.NotificationsCenterScreen
import app.cosmos.com.screens.profile.SettingsPrivacyScreen
import app.cosmos.com.screens.profile.EditProfileScreen
import app.cosmos.com.screens.profile.HelpSupportScreen
import app.cosmos.com.screens.profile.NetworkRelationsScreen
import app.cosmos.com.screens.social.SocialFeedScreen
import app.cosmos.com.screens.social.PostDetailScreen

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun CosmosNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route,
    modifier: Modifier = Modifier
) {
    val authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val currentUserState by authViewModel.currentUser.collectAsState()

    androidx.compose.runtime.LaunchedEffect(currentUserState) {
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null && !firebaseUser.isEmailVerified) {
            val currentRoute = navController.currentDestination?.route
            val bypassRoutes = listOf(Screen.VerifyEmail.route, Screen.Welcome.route, Screen.WelcomeSignIn.route)
            if (currentRoute != null && currentRoute !in bypassRoutes) {
                navController.navigate(Screen.VerifyEmail.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            return@LaunchedEffect
        }

        val user = currentUserState
        if (user != null && !user.isProfileComplete && !user.isFromCache) {
            val currentRoute = navController.currentDestination?.route
            val onboardingRoutes = listOf(
                Screen.Splash.route,
                Screen.Welcome.route,
                Screen.WelcomeSignIn.route,
                Screen.CompleteIdentity.route,
                Screen.VerifyEmail.route,
                Screen.DefineIntent.route,
                Screen.YourVision.route,
                Screen.AiMatchingRefinement.route
            )
            if (currentRoute != null && currentRoute !in onboardingRoutes) {
                val targetRoute = when {
                    user.name.isBlank() || user.primaryUserType.isBlank() -> Screen.CompleteIdentity.route
                    user.tags.isEmpty() -> Screen.DefineIntent.route
                    user.goalStatement.isBlank() -> Screen.YourVision.route
                    else -> Screen.AiMatchingRefinement.route
                }
                navController.navigate(targetRoute) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

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
        composable(Screen.Splash.route) {
            SplashScreen(
                onAnimationFinished = {
                    val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val dest = if (firebaseUser != null && firebaseUser.isEmailVerified) Screen.Connect.route else Screen.Welcome.route
                    navController.navigate(dest) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
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
        composable(
            route = Screen.ResetPassword.route,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "https://cosmos-app-42ed2.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode={oobCode}"
                },
                navDeepLink {
                    uriPattern = "https://cosmos-app-42ed2.web.app/__/auth/action?mode=resetPassword&oobCode={oobCode}"
                },
                navDeepLink {
                    uriPattern = "cosmos://reset-password?oobCode={oobCode}"
                }
            ),
            arguments = listOf(
                navArgument("oobCode") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val oobCode = backStackEntry.arguments?.getString("oobCode")
            ResetPasswordScreen(
                oobCode = oobCode,
                onResetSuccess = {
                    navController.navigate(Screen.WelcomeSignIn.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.navigate(Screen.WelcomeSignIn.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }
        composable(Screen.CompleteIdentity.route) {
            CompleteIdentityScreen(
                onNext = { navController.navigate(Screen.VerifyEmail.route) },
                onBack = { navController.popBackStack() },
                onSignInInstead = {
                    navController.navigate(Screen.WelcomeSignIn.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = false }
                    }
                }
            )
        }
        composable(Screen.VerifyEmail.route) {
            VerifyEmailScreen(
                onVerified = {
                    navController.navigate(Screen.DefineIntent.route) {
                        popUpTo(Screen.VerifyEmail.route) { inclusive = true }
                    }
                },
                onSignOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
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
                onNavigateToFeed = { navController.navigate(Screen.FoundersOrbitFeed.route) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.SearchProfiles.route) {
            SearchScreen(
                onProfileTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.FoundersOrbitFeed.route) {
            FoundersOrbitFeedScreen(
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
        composable(Screen.ConnectionRequests.route) {
            ConnectionRequestsScreen(
                onBack = { navController.popBackStack() },
                onProfileTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                },
                onChatTap = { connectionId ->
                    navController.navigate(Screen.CrmChat.createRoute(connectionId))
                },
                onNavigate = { route -> navController.navigate(route) }
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
                onPostEventTap = {
                    navController.navigate(Screen.PostEvent.route)
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.PostEvent.route) {
            app.cosmos.com.screens.events.PostEventScreen(
                onBack = { navController.popBackStack() },
                onEventPosted = { navController.popBackStack() }
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

        // ── Social ────────────────────────────────────────────────────────────
        composable(Screen.Social.route) {
            SocialFeedScreen(
                onPostTap = { postId ->
                    navController.navigate(Screen.SocialPostDetail.createRoute(postId))
                },
                onProfileTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.SocialPostDetail.route,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStack ->
            val postId = backStack.arguments?.getString("postId") ?: ""
            PostDetailScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
                onProfileTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                }
            )
        }

        // ── Communities ───────────────────────────────────────────────────────
        composable(Screen.Communities.route) {
            CommunityHubScreen(
                onExplore = { navController.navigate(Screen.ExploreOrbits.route) },
                onCircleTap = { circleId ->
                    navController.navigate(Screen.PrivateOrbitFeed.createRoute(circleId))
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.ExploreOrbits.route) {
            ExploreOrbitsScreen(
                onBack = { navController.popBackStack() },
                onCircleTap = { circleId ->
                    navController.navigate(Screen.OrbitMembers.createRoute(circleId))
                }
            )
        }
        composable(
            route = Screen.OrbitMembers.route,
            arguments = listOf(navArgument("circleId") { type = NavType.StringType })
        ) { backStack ->
            val circleId = backStack.arguments?.getString("circleId") ?: ""
            OrbitMembersScreen(
                circleId = circleId,
                onBack = { navController.popBackStack() },
                onMemberTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                },
                onFeedTap = { navController.navigate(Screen.PrivateOrbitFeed.createRoute(circleId)) }
            )
        }
        composable(
            route = Screen.PrivateOrbitFeed.route,
            arguments = listOf(navArgument("circleId") { type = NavType.StringType })
        ) { backStack ->
            val circleId = backStack.arguments?.getString("circleId") ?: ""
            PrivateOrbitFeedScreen(
                circleId = circleId,
                onBack = { navController.popBackStack() },
                onMembersTap = { navController.navigate(Screen.OrbitMembers.createRoute(circleId)) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = Screen.OrbitPostDetail.route,
            arguments = listOf(
                navArgument("circleId") { type = NavType.StringType },
                navArgument("postId") { type = NavType.StringType }
            )
        ) { backStack ->
            val circleId = backStack.arguments?.getString("circleId") ?: ""
            val postId = backStack.arguments?.getString("postId") ?: ""
            OrbitPostDetailScreen(
                circleId = circleId,
                postId = postId,
                onBack = { navController.popBackStack() },
                onProfileTap = { memberId ->
                    navController.navigate(Screen.MemberProfile.createRoute(memberId))
                }
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
                onNavigate = { route -> navController.navigate(route) },
                authViewModel = authViewModel
            )
        }
        composable(Screen.MembershipTiers.route) {
            MembershipTiersScreen(
                onBack = { navController.popBackStack() },
                authViewModel = authViewModel
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
                },
                onNavigate = { route -> navController.navigate(route) },
                authViewModel = authViewModel
            )
        }
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onBack = { navController.popBackStack() },
                authViewModel = authViewModel
            )
        }
        composable(Screen.HelpSupport.route) {
            HelpSupportScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.NetworkRelations.route,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "followers"
                }
            )
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab") ?: "followers"
            NetworkRelationsScreen(
                initialTab = initialTab,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }
    }
}
