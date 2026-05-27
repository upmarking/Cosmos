package com.cosmos.app.data.model

/**
 * Core data models for the Cosmos app.
 * These are placeholder models — replace with real API models in production.
 */

data class Member(
    val id: String,
    val name: String,
    val headline: String,
    val role: String,
    val company: String,
    val avatarUrl: String,
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
    val primaryUserType: String = ""
)

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

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: String,
    val isOwn: Boolean,
    val type: MessageType = MessageType.TEXT
)

enum class MessageType {
    TEXT, AI_SUMMARY, MEETING_NOTE, INTRO_REQUEST
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
    val rounds: List<EventRound> = emptyList()
)

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
    val adminName: String = ""
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
    PROFILE_VIEW
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
