package com.cosmos.app.data.repository

import com.cosmos.app.data.model.EventType
import com.cosmos.app.data.model.MembershipTier
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Centralized seed service for initializing Firestore with sample data.
 * Only runs when collections are empty (first launch / demo mode).
 * Separated from production repository code for clarity.
 */
object FirestoreSeedService {

    suspend fun seedUsers(firestore: FirebaseFirestore) {
        val users = listOf(
            mapOf(
                "id" to "mock_user_sarah",
                "name" to "Sarah Jenkins",
                "email" to "sarah@biosphere.com",
                "headline" to "Founder & CEO at BioSphere",
                "role" to "CEO",
                "company" to "BioSphere",
                "avatarUrl" to "",
                "location" to "Boston, MA",
                "bio" to "Building the future of sustainable food systems.",
                "tags" to listOf("ClimateTech", "Biotech", "Female Founder"),
                "primaryUserType" to "Founder",
                "userRole" to "ADMIN",
                "membershipTier" to MembershipTier.FOUNDER.name,
                "isLinkedInConnected" to true,
                "isProfileComplete" to true,
                "isRestricted" to false,
                "mutualConnectionsCount" to 3,
                "connectionsCount" to 14,
                "eventsAttended" to 5,
                "followUpsCompleted" to 8,
                "goalStatement" to "Find a co-founder for climate tech startup",
                "longTermVision" to "Revolutionize sustainable agriculture through biotech innovation",
                "lookingFor" to listOf("Co-founders", "Investors", "Mentors")
            ),
            mapOf(
                "id" to "mock_user_david",
                "name" to "David Chen",
                "email" to "david@nexus.com",
                "headline" to "General Partner at Nexus Ventures",
                "role" to "GP",
                "company" to "Nexus Ventures",
                "avatarUrl" to "",
                "location" to "San Francisco, CA",
                "bio" to "Investing in early stage AI and enterprise SaaS. Former developer.",
                "tags" to listOf("AI/ML", "B2B SaaS", "VC"),
                "primaryUserType" to "Investor",
                "userRole" to "ORGANIZER",
                "membershipTier" to MembershipTier.INNER_CIRCLE.name,
                "isLinkedInConnected" to true,
                "isProfileComplete" to true,
                "isRestricted" to false,
                "mutualConnectionsCount" to 5,
                "connectionsCount" to 42,
                "eventsAttended" to 12,
                "followUpsCompleted" to 20,
                "goalStatement" to "Discover promising AI-first startups to invest in",
                "longTermVision" to "Build a portfolio of transformative enterprise AI companies",
                "lookingFor" to listOf("Founders", "Strategic introductions")
            ),
            mapOf(
                "id" to "mock_user_elena",
                "name" to "Elena Rostova",
                "email" to "elena@cosmos.design",
                "headline" to "Lead Designer at Cosmos Studio",
                "role" to "Designer",
                "company" to "Cosmos Studio",
                "avatarUrl" to "",
                "location" to "New York, NY",
                "bio" to "Passionate about premium design systems and minimalist interfaces.",
                "tags" to listOf("UI/UX", "Product Design", "Creative"),
                "primaryUserType" to "Creator",
                "userRole" to "USER",
                "membershipTier" to MembershipTier.MEMBER.name,
                "isLinkedInConnected" to false,
                "isProfileComplete" to true,
                "isRestricted" to false,
                "mutualConnectionsCount" to 1,
                "connectionsCount" to 8,
                "eventsAttended" to 3,
                "followUpsCompleted" to 4,
                "goalStatement" to "Connect with founders who need world-class design",
                "longTermVision" to "Build a design agency focused on premium startup branding",
                "lookingFor" to listOf("Collaborators", "Clients", "Industry peers")
            ),
            mapOf(
                "id" to "mock_user_marcus",
                "name" to "Marcus Vance",
                "email" to "marcus@scaleup.com",
                "headline" to "VP of Product at ScaleUp",
                "role" to "Product VP",
                "company" to "ScaleUp",
                "avatarUrl" to "",
                "location" to "Austin, TX",
                "bio" to "Scale specialist. Former 0-to-1 PM at Stripe and Airbnb.",
                "tags" to listOf("Product Management", "Growth", "Scaling"),
                "primaryUserType" to "Startup Operator",
                "userRole" to "USER",
                "membershipTier" to MembershipTier.EXPLORER.name,
                "isLinkedInConnected" to false,
                "isProfileComplete" to true,
                "isRestricted" to false,
                "mutualConnectionsCount" to 2,
                "connectionsCount" to 19,
                "eventsAttended" to 8,
                "followUpsCompleted" to 15,
                "goalStatement" to "Mentor early-stage product teams",
                "longTermVision" to "Transition from operator to advisor across multiple startups",
                "lookingFor" to listOf("Mentoring", "Hiring opportunities", "Business partners")
            )
        )

        for (user in users) {
            val uid = user["id"] as String
            firestore.collection("users").document(uid).set(user).await()
        }
    }

    suspend fun seedEvents(firestore: FirebaseFirestore) {
        // Ensure users exist
        val usersSnapshot = firestore.collection("users").get().await()
        if (usersSnapshot.isEmpty) {
            seedUsers(firestore)
        }

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val date1 = sdf.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 3)
        val date2 = sdf.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 6)
        val date3 = sdf.format(calendar.time)

        val events = listOf(
            "event_1" to mapOf(
                "title" to "Founder Speed Matchmaking",
                "description" to "Intentional 1-on-1 networking matches for founders and operators seeking co-founders.",
                "date" to date1,
                "time" to "6:00 PM UTC",
                "location" to "Cosmos Virtual Room",
                "type" to EventType.FOUNDER_MEETUP.name,
                "participantCount" to 18,
                "maxParticipants" to 30,
                "isPaid" to false,
                "price" to "",
                "coverUrl" to "",
                "tags" to listOf("Speed Dating", "Co-Founder", "Interactive"),
                "createdBy" to "system"
            ),
            "event_2" to mapOf(
                "title" to "AI & B2B SaaS Showcase",
                "description" to "Presenting three breakout startups building AI-first enterprise workflows.",
                "date" to date2,
                "time" to "4:00 PM UTC",
                "location" to "Cosmos Hall",
                "type" to EventType.INDUSTRY_SPECIFIC.name,
                "participantCount" to 45,
                "maxParticipants" to 100,
                "isPaid" to false,
                "price" to "",
                "coverUrl" to "",
                "tags" to listOf("AI", "B2B SaaS", "Showcase"),
                "createdBy" to "system"
            ),
            "event_3" to mapOf(
                "title" to "Cosmos Open Networking Hour",
                "description" to "Meet and chat with other members of the club in a relaxed, open format.",
                "date" to date3,
                "time" to "7:00 PM UTC",
                "location" to "Cosmos Lounge",
                "type" to EventType.OPEN_NETWORKING.name,
                "participantCount" to 22,
                "maxParticipants" to 50,
                "isPaid" to false,
                "price" to "",
                "coverUrl" to "",
                "tags" to listOf("Networking", "Open", "Social"),
                "createdBy" to "system"
            )
        )

        val batch = firestore.batch()
        for ((id, data) in events) {
            batch.set(firestore.collection("events").document(id), data)
        }
        batch.commit().await()

        // Add rounds to first event
        val userIds = firestore.collection("users").get().await().documents.map { it.id }
        val roundsRef = firestore.collection("events").document("event_1").collection("rounds")
        roundsRef.document("r1").set(
            mapOf(
                "title" to "Round 1: Intros",
                "duration" to 15,
                "participantIds" to userIds.take(2)
            )
        ).await()
        roundsRef.document("r2").set(
            mapOf(
                "title" to "Round 2: Collaborative Ideas",
                "duration" to 15,
                "participantIds" to userIds.drop(2).take(2)
            )
        ).await()
    }

    suspend fun seedCircles(firestore: FirebaseFirestore) {
        // Ensure users exist
        val usersSnapshot = firestore.collection("users").get().await()
        if (usersSnapshot.isEmpty) {
            seedUsers(firestore)
        }

        // Only seed if circles collection is empty
        val circlesSnapshot = firestore.collection("circles").get().await()
        if (!circlesSnapshot.isEmpty) return

        val circles = listOf(
            "circle_1" to mapOf(
                "name" to "AI Builders & Founders",
                "description" to "A circle for developers and founders building next-gen generative AI applications.",
                "coverUrl" to "",
                "memberCount" to 4,
                "theme" to "AI & Technology",
                "tags" to listOf("AI/ML", "LLMs", "Tech"),
                "isPrivate" to false,
                "adminName" to "David Chen",
                "createdBy" to "mock_user_david",
                "createdAt" to FieldValue.serverTimestamp()
            ),
            "circle_2" to mapOf(
                "name" to "Cosmos Founders Club",
                "description" to "Official private circle for verified Cosmos startup founders to share tips and resources.",
                "coverUrl" to "",
                "memberCount" to 3,
                "theme" to "Entrepreneurship",
                "tags" to listOf("Founders", "Fundraising", "Cosmos"),
                "isPrivate" to true,
                "adminName" to "Sarah Jenkins",
                "createdBy" to "mock_user_sarah",
                "createdAt" to FieldValue.serverTimestamp()
            ),
            "circle_3" to mapOf(
                "name" to "Premium Design Collective",
                "description" to "Discussing aesthetics, premium typography, and UI/UX design trends.",
                "coverUrl" to "",
                "memberCount" to 4,
                "theme" to "Design",
                "tags" to listOf("Design", "UI/UX", "Aesthetics"),
                "isPrivate" to false,
                "adminName" to "Elena Rostova",
                "createdBy" to "mock_user_elena",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        // Create circle documents
        val batch = firestore.batch()
        for ((id, data) in circles) {
            batch.set(firestore.collection("circles").document(id), data)
        }
        batch.commit().await()

        // Add members to each circle
        val circle1Members = listOf("mock_user_sarah", "mock_user_david", "mock_user_elena", "mock_user_marcus")
        val circle2Members = listOf("mock_user_sarah", "mock_user_david", "mock_user_marcus")
        val circle3Members = listOf("mock_user_sarah", "mock_user_david", "mock_user_elena", "mock_user_marcus")

        val memberData = mapOf(
            "circle_1" to circle1Members,
            "circle_2" to circle2Members,
            "circle_3" to circle3Members
        )

        for ((circleId, members) in memberData) {
            for (uid in members) {
                firestore.collection("circles").document(circleId)
                    .collection("members").document(uid)
                    .set(mapOf(
                        "joinedAt" to FieldValue.serverTimestamp(),
                        "status" to "APPROVED"
                    )).await()
            }
        }

        // Add seed posts
        val posts = mapOf(
            "circle_1" to listOf(
                mapOf(
                    "authorId" to "mock_user_david",
                    "authorName" to "David Chen",
                    "authorAvatar" to "",
                    "content" to "Welcome to the AI Builders circle! Share what you are working on. We've got a lot of exciting LLM projects coming up.",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isPinned" to false,
                    "likesCount" to 15,
                    "repliesCount" to 4,
                    "likes" to listOf("mock_user_sarah", "mock_user_elena")
                ),
                mapOf(
                    "authorId" to "mock_user_sarah",
                    "authorName" to "Sarah Jenkins",
                    "authorAvatar" to "",
                    "content" to "Great to be here! Working on using agentic workflows for bio-tech research.",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isPinned" to false,
                    "likesCount" to 8,
                    "repliesCount" to 2,
                    "likes" to listOf("mock_user_david")
                )
            ),
            "circle_2" to listOf(
                mapOf(
                    "authorId" to "mock_user_sarah",
                    "authorName" to "Sarah Jenkins",
                    "authorAvatar" to "",
                    "content" to "Hello fellow founders! Remember that our goal is 10 high-quality connections per month. Keep it high-trust!",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isPinned" to false,
                    "likesCount" to 24,
                    "repliesCount" to 5,
                    "likes" to listOf("mock_user_david", "mock_user_marcus")
                ),
                mapOf(
                    "authorId" to "mock_user_marcus",
                    "authorName" to "Marcus Vance",
                    "authorAvatar" to "",
                    "content" to "Welcome everyone. Let's use this circle to discuss fundraising, hiring, and scaling challenges.",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isPinned" to false,
                    "likesCount" to 12,
                    "repliesCount" to 3,
                    "likes" to listOf("mock_user_sarah")
                )
            ),
            "circle_3" to listOf(
                mapOf(
                    "authorId" to "mock_user_elena",
                    "authorName" to "Elena Rostova",
                    "authorAvatar" to "",
                    "content" to "Typography is the unsung hero of premium interfaces. What's your favorite sans-serif font lately? I'm loving Outfit and Inter.",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isPinned" to false,
                    "likesCount" to 19,
                    "repliesCount" to 6,
                    "likes" to listOf("mock_user_sarah", "mock_user_david")
                ),
                mapOf(
                    "authorId" to "mock_user_sarah",
                    "authorName" to "Sarah Jenkins",
                    "authorAvatar" to "",
                    "content" to "I really like Outfit, it gives that modern tech-organic vibe.",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isPinned" to false,
                    "likesCount" to 10,
                    "repliesCount" to 1,
                    "likes" to listOf("mock_user_elena")
                )
            )
        )

        for ((circleId, postList) in posts) {
            for (post in postList) {
                firestore.collection("circles").document(circleId)
                    .collection("posts").add(post).await()
            }
        }
    }
}
