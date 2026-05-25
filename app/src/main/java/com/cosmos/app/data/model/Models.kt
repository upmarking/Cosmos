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
    val followUpsCompleted: Int = 0
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
 * Sample/mock data for UI previews and placeholder screens.
 */
object SampleData {
    val sampleMember = Member(
        id = "1",
        name = "Alexandra Chen",
        headline = "Founder & CEO at NexusAI",
        role = "CEO",
        company = "NexusAI",
        avatarUrl = "",
        location = "San Francisco, CA",
        bio = "Building the future of enterprise intelligence. Ex-Google, Stanford MBA. Passionate about AI, product strategy, and building world-class teams.",
        tags = listOf("AI", "SaaS", "Founder", "Product"),
        goalStatement = "Looking for co-founders, strategic investors, and enterprise advisors in the AI space.",
        mutualConnectionsCount = 12,
        isLinkedInConnected = true,
        membershipTier = MembershipTier.INNER_CIRCLE,
        connectionsCount = 8,
        eventsAttended = 5,
        followUpsCompleted = 14,
        endorsedSkills = listOf(
            EndorsedSkill("Product Strategy", 24),
            EndorsedSkill("Fundraising", 18),
            EndorsedSkill("Leadership", 31),
            EndorsedSkill("AI/ML", 15),
            EndorsedSkill("Public Speaking", 9)
        )
    )

    val sampleMembers = listOf(
        sampleMember,
        Member(
            id = "2",
            name = "Marcus Williams",
            headline = "Partner at Sequoia Capital",
            role = "Partner",
            company = "Sequoia Capital",
            avatarUrl = "",
            location = "Menlo Park, CA",
            bio = "Early-stage investor focused on B2B SaaS, fintech, and deep tech. Previously founded two companies.",
            tags = listOf("Investor", "Fintech", "B2B SaaS", "Deep Tech"),
            goalStatement = "Seeking exceptional founders building in fintech and AI infrastructure.",
            mutualConnectionsCount = 7,
            isLinkedInConnected = true,
            membershipTier = MembershipTier.FOUNDER,
            endorsedSkills = listOf(
                EndorsedSkill("Venture Capital", 41),
                EndorsedSkill("Strategic Partnerships", 29),
                EndorsedSkill("Board Advisory", 22)
            )
        ),
        Member(
            id = "3",
            name = "Priya Sharma",
            headline = "Head of Product at Stripe",
            role = "Head of Product",
            company = "Stripe",
            avatarUrl = "",
            location = "New York, NY",
            bio = "Scaling payment infrastructure for millions of businesses globally. Obsessed with developer experience and financial inclusion.",
            tags = listOf("Product", "Fintech", "Developer Tools", "Growth"),
            goalStatement = "Looking to connect with fintech founders and potential collaborators on open banking.",
            mutualConnectionsCount = 3,
            isLinkedInConnected = true,
            membershipTier = MembershipTier.MEMBER,
            endorsedSkills = listOf(
                EndorsedSkill("Product Thinking", 38),
                EndorsedSkill("Fintech", 27),
                EndorsedSkill("Growth", 19)
            )
        )
    )

    val sampleCircles = listOf(
        Circle(
            id = "c1",
            name = "Founders Circle",
            description = "A private community for verified startup founders to share insights, challenges, and opportunities.",
            memberCount = 234,
            theme = "Entrepreneurship",
            tags = listOf("Founders", "Startups", "Fundraising"),
            isJoined = true,
            isPrivate = true,
            adminName = "Cosmos Team"
        ),
        Circle(
            id = "c2",
            name = "AI & Deep Tech",
            description = "For builders and investors at the frontier of artificial intelligence and deep technology.",
            memberCount = 412,
            theme = "Technology",
            tags = listOf("AI", "ML", "Deep Tech", "Research"),
            isJoined = false,
            isPrivate = false,
            adminName = "Dr. James Park"
        ),
        Circle(
            id = "c3",
            name = "Fintech Operators",
            description = "Senior operators and founders in the financial technology space.",
            memberCount = 189,
            theme = "Finance",
            tags = listOf("Fintech", "Payments", "Banking", "Crypto"),
            isJoined = false,
            isPrivate = true,
            adminName = "Sarah Goldman"
        )
    )

    val sampleEvents = listOf(
        NetworkEvent(
            id = "e1",
            title = "Founders Summit: AI Edition",
            description = "An intimate gathering of 50 top AI founders for structured networking, roundtables, and 1:1 meetings.",
            date = "June 15, 2026",
            time = "6:00 PM - 9:00 PM PST",
            location = "San Francisco, CA",
            type = EventType.FOUNDER_MEETUP,
            participantCount = 38,
            maxParticipants = 50,
            isPaid = true,
            price = "$75",
            tags = listOf("AI", "Founders", "Networking"),
            coverUrl = ""
        ),
        NetworkEvent(
            id = "e2",
            title = "Open Networking: NYC",
            description = "Monthly open networking session for professionals across all industries in New York City.",
            date = "June 20, 2026",
            time = "7:00 PM - 9:00 PM EST",
            location = "New York, NY",
            type = EventType.OPEN_NETWORKING,
            participantCount = 67,
            maxParticipants = 100,
            tags = listOf("Networking", "NYC", "Open")
        )
    )

    val sampleNotifications = listOf(
        Notification(
            id = "n1",
            type = NotificationType.NEW_MATCH,
            title = "New Match! 🎉",
            body = "You and Marcus Williams are both interested in connecting.",
            timestamp = "2 min ago",
            isRead = false,
            actionId = "2"
        ),
        Notification(
            id = "n2",
            type = NotificationType.WARM_INTRO_REQUEST,
            title = "Warm Introduction Request",
            body = "Priya Sharma wants to be introduced to your connection David Kim.",
            timestamp = "1 hour ago",
            isRead = false,
            actionId = "req_001"
        ),
        Notification(
            id = "n3",
            type = NotificationType.ENDORSEMENT_RECEIVED,
            title = "New Endorsement",
            body = "Alexandra Chen endorsed your Product Strategy skill.",
            timestamp = "3 hours ago",
            isRead = true,
            actionId = "1"
        ),
        Notification(
            id = "n4",
            type = NotificationType.AI_SUMMARY_READY,
            title = "Meeting Summary Ready",
            body = "Your meeting with Marcus Williams has been summarized by AI.",
            timestamp = "Yesterday",
            isRead = true,
            actionId = "2"
        ),
        Notification(
            id = "n5",
            type = NotificationType.EVENT_INVITATION,
            title = "Event Invitation",
            body = "You've been invited to Founders Summit: AI Edition on June 15.",
            timestamp = "2 days ago",
            isRead = true,
            actionId = "e1"
        )
    )
}
