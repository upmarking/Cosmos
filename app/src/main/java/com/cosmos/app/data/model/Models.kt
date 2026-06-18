package com.cosmos.app.data.model

/**
 * Core data models for the Cosmos app.
 * All models map directly to Firestore document structures.
 */

// ── User Role (administrative permission level) ─────────────────────────────
enum class UserRole {
    USER,       // Regular user
    ORGANIZER,  // Can create events and circles
    ADMIN       // Full platform access
}

data class Member(
    val id: String,
    val name: String,
    val headline: String,
    val role: String,
    val company: String,
    val avatarUrl: String,
    val email: String = "",
    val location: String = "",
    val bio: String = "",
    val tags: List<String> = emptyList(),
    val goalStatement: String = "",
    val longTermVision: String = "",
    val endorsedSkills: List<EndorsedSkill> = emptyList(),
    val mutualConnectionsCount: Int = 0,
    val isLinkedInConnected: Boolean = false,
    val memberSince: String = "",
    val membershipTier: MembershipTier = MembershipTier.EXPLORER,
    val connectionsCount: Int = 0,
    val eventsAttended: Int = 0,
    val followUpsCompleted: Int = 0,
    val introsMade: Int = 0,
    val goalsHit: Int = 0,
    val primaryUserType: String = "",
    val userRole: UserRole = UserRole.USER,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isProfileComplete: Boolean = false,
    val isRestricted: Boolean = false,
    val lookingFor: List<String> = emptyList(),
    val availabilityPreferences: String = "",
    val notificationNewMatches: Boolean = true,
    val notificationMessages: Boolean = true,
    val notificationEventInvitations: Boolean = true,
    val notificationEventReminders: Boolean = true,
    val notificationAiSummaries: Boolean = true,
    val notificationFollowUpReminders: Boolean = true,
    val notificationWarmIntroRequests: Boolean = true,
    val notificationCommunityAnnouncements: Boolean = true,
    val notificationEndorsements: Boolean = true,
    val privacyProfileVisibility: Boolean = true,
    val privacyShowLinkedIn: Boolean = true,
    val privacyAllowWarmIntros: Boolean = true,
    val privacyShowMutualConnections: Boolean = true,
    val privacyDataAnalytics: Boolean = true,
    val monthlyConnectionLimit: Int = 10,
    val matchingPreferences: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList(),
    val isFromCache: Boolean = false
) {
    /** Computed: whether core profile fields have been filled */
    val profileCompletionPercent: Int
        get() {
            var filled = 0
            var total = 8
            if (name.isNotBlank()) filled++
            if (headline.isNotBlank()) filled++
            if (role.isNotBlank()) filled++
            if (company.isNotBlank()) filled++
            if (bio.isNotBlank()) filled++
            if (tags.isNotEmpty()) filled++
            if (goalStatement.isNotBlank()) filled++
            if (avatarUrl.isNotBlank()) filled++
            return (filled * 100) / total
        }

    val isAdmin: Boolean get() = userRole == UserRole.ADMIN
    val isOrganizer: Boolean get() = userRole == UserRole.ORGANIZER || userRole == UserRole.ADMIN
}

data class EndorsedSkill(
    val name: String,
    val count: Int,
    val endorsers: List<String> = emptyList()
)

enum class MembershipTier(val label: String, val color: Long) {
    EXPLORER("Explorer", 0xFF908FA0),
    MEMBER("Member", 0xFF0566D9),
    INNER_CIRCLE("Inner Circle", 0xFF494BD6),
    FOUNDER("Founder", 0xFFC0C1FF)
}

data class Connection(
    val id: String,
    val member: Member,
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val labels: List<String> = emptyList(),
    val privateGoal: String = "",
    val status: ConnectionStatus = ConnectionStatus.ACTIVE
)

enum class ConnectionStatus {
    PENDING, ACTIVE, FOLLOW_UP_NEEDED, INTRO_REQUESTED
}

/** Status of a connection request between two users */
enum class ConnectionRequestStatus {
    PENDING, ACCEPTED, DECLINED, WITHDRAWN
}

/** Aggregated relationship status between current user and a viewed profile */
enum class ConnectionProfileStatus {
    NONE,              // No relationship
    PENDING_SENT,      // Current user sent a request
    PENDING_RECEIVED,  // Current user received a request
    CONNECTED          // Active connection exists
}

data class ConnectionRequest(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val senderName: String = "",
    val senderHeadline: String = "",
    val senderAvatarUrl: String = "",
    val receiverName: String = "",
    val receiverHeadline: String = "",
    val receiverAvatarUrl: String = "",
    val message: String = "",
    val status: ConnectionRequestStatus = ConnectionRequestStatus.PENDING,
    val createdAt: Long = 0L
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: String,
    val isOwn: Boolean,
    val type: MessageType = MessageType.TEXT,
    val isDeleted: Boolean = false
)

enum class MessageType {
    TEXT, AI_SUMMARY, MEETING_NOTE, INTRO_REQUEST, SYSTEM
}

data class NetworkEvent(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val location: String,
    val type: EventType,
    val participantCount: Int,
    val maxParticipants: Int,
    val isPaid: Boolean = false,
    val price: String = "",
    val coverUrl: String = "",
    val tags: List<String> = emptyList(),
    val isRegistered: Boolean = false,
    val rounds: List<EventRound> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = 0L
) {
    val isFull: Boolean get() = participantCount >= maxParticipants
    val spotsRemaining: Int get() = (maxParticipants - participantCount).coerceAtLeast(0)
}

enum class EventType(val label: String) {
    OPEN_NETWORKING("Open Networking"),
    FOUNDER_MEETUP("Founder Meetup"),
    INDUSTRY_SPECIFIC("Industry Specific"),
    INVITE_ONLY("Invite Only"),
    THEMED("Themed Event")
}

data class EventRound(
    val id: String,
    val title: String,
    val duration: Int, // minutes
    val participants: List<Member> = emptyList()
)

data class EventFeedback(
    val id: String = "",
    val raterId: String,
    val rateeId: String,
    val rating: Int,
    val feedbackText: String,
    val timestamp: Long = 0L
)

data class Circle(
    val id: String,
    val name: String,
    val description: String,
    val coverUrl: String = "",
    val memberCount: Int,
    val theme: String,
    val tags: List<String> = emptyList(),
    val isJoined: Boolean = false,
    val isPrivate: Boolean = false,
    val adminName: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val isPending: Boolean = false
)

data class CirclePost(
    val id: String = "",
    val authorId: String,
    val author: String,
    val avatarUrl: String,
    val content: String,
    val timeString: String,
    val isPinned: Boolean = false,
    val likesCount: Int = 0,
    val repliesCount: Int = 0
)

data class SocialPost(
    val id: String = "",
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val authorHeadline: String,
    val content: String,
    val timeString: String,
    val imageUrl: String? = null,
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val likes: List<String> = emptyList()
)

data class SocialPostReply(
    val id: String = "",
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val authorHeadline: String,
    val content: String,
    val timeString: String
)


data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: String,
    val isRead: Boolean = false,
    val actionId: String = "" // e.g. memberId, eventId, requestId
)

enum class NotificationType {
    NEW_MATCH,
    MESSAGE,
    EVENT_INVITATION,
    EVENT_REMINDER,
    MEETING_SCHEDULED,
    AI_SUMMARY_READY,
    FOLLOW_UP_REMINDER,
    WARM_INTRO_REQUEST,
    WARM_INTRO_ACCEPTED,
    WARM_INTRO_DECLINED,
    COMMUNITY_ANNOUNCEMENT,
    ENDORSEMENT_RECEIVED,
    PROFILE_VIEW,
    CONNECTION_REQUEST,
    CONNECTION_ACCEPTED
}

data class IntroRequest(
    val id: String,
    val requester: Member,
    val target: Member,
    val connector: Member,
    val message: String,
    val status: IntroStatus = IntroStatus.PENDING
)

enum class IntroStatus {
    PENDING, ACCEPTED, DECLINED, COMPLETED
}

/**
 * Minimal placeholder for fallback usage only.
 * Real data comes from Firestore.
 */
object SampleData {
    val sampleMember = Member(
        id = "",
        name = "Unknown",
        headline = "",
        role = "",
        company = "",
        avatarUrl = "",
        membershipTier = MembershipTier.EXPLORER
    )
}
