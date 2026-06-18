package com.cosmos.app.navigation

/**
 * All navigation destinations in the Cosmos app.
 */
sealed class Screen(val route: String) {

    // ── Onboarding ──────────────────────────────────────────────────────────
    object Welcome : Screen("welcome")
    object WelcomeSignIn : Screen("welcome_signin") // Welcome screen with sign-in panel open
    object CompleteIdentity : Screen("complete_identity")
    object DefineIntent : Screen("define_intent")
    object YourVision : Screen("your_vision")
    object AiMatchingRefinement : Screen("ai_matching_refinement")

    // ── Main App Tabs ────────────────────────────────────────────────────────
    object Connect : Screen("connect")
    object Events : Screen("events")
    object Social : Screen("social")
    object Communities : Screen("communities")
    object Conversations : Screen("conversations")
    object Profile : Screen("profile")

    // ── Connect sub-screens ──────────────────────────────────────────────────
    object DiscoveryDeck : Screen("discovery_deck")
    object SearchProfiles : Screen("search_profiles")
    object FoundersOrbitFeed : Screen("founders_orbit_feed")
    object MemberProfile : Screen("member_profile/{memberId}") {
        fun createRoute(memberId: String) = "member_profile/$memberId"
    }
    object EndorseExpertise : Screen("endorse_expertise/{memberId}") {
        fun createRoute(memberId: String) = "endorse_expertise/$memberId"
    }
    object ConnectionRequests : Screen("connection_requests")
    object RequestWarmIntro : Screen("request_warm_intro/{memberId}") {
        fun createRoute(memberId: String) = "request_warm_intro/$memberId"
    }
    object ReviewIntroRequest : Screen("review_intro_request/{requestId}") {
        fun createRoute(requestId: String) = "review_intro_request/$requestId"
    }
    object ThreeWayIntroduction : Screen("three_way_introduction/{introId}") {
        fun createRoute(introId: String) = "three_way_introduction/$introId"
    }

    // ── Events sub-screens ───────────────────────────────────────────────────
    object EventLobby : Screen("event_lobby/{eventId}") {
        fun createRoute(eventId: String) = "event_lobby/$eventId"
    }
    object PostEvent : Screen("post_event")

    // ── Communities sub-screens ──────────────────────────────────────────────
    object CommunityHub : Screen("community_hub")
    object ExploreOrbits : Screen("explore_orbits")
    object OrbitMembers : Screen("orbit_members/{circleId}") {
        fun createRoute(circleId: String) = "orbit_members/$circleId"
    }
    object PrivateOrbitFeed : Screen("private_orbit_feed/{circleId}") {
        fun createRoute(circleId: String) = "private_orbit_feed/$circleId"
    }

    // ── Conversations sub-screens ────────────────────────────────────────────
    object CrmChat : Screen("crm_chat/{connectionId}") {
        fun createRoute(connectionId: String) = "crm_chat/$connectionId"
    }

    // ── Profile sub-screens ──────────────────────────────────────────────────
    object NetworkingDashboard : Screen("networking_dashboard")
    object MembershipTiers : Screen("membership_tiers")
    object Notifications : Screen("notifications")
    object Settings : Screen("settings")
    object EditProfile : Screen("edit_profile")
    object HelpSupport : Screen("help_support")

    // ── Social sub-screens ───────────────────────────────────────────────────
    object SocialPostDetail : Screen("social_post_detail/{postId}") {
        fun createRoute(postId: String) = "social_post_detail/$postId"
    }
}
