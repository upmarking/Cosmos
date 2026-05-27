package com.cosmos.app.data.repository

import com.cosmos.app.data.model.EndorsedSkill
import com.cosmos.app.data.model.Member
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface ProfileRepository {
    suspend fun getProfile(userId: String): Result<Member>
    suspend fun getAllProfiles(): Result<List<Member>>
    suspend fun getDiscoveryDeck(currentUserId: String): Result<List<Member>>
    suspend fun endorseSkill(userId: String, endorserName: String, skillName: String): Result<Unit>
    suspend fun getEndorsedSkills(userId: String): Result<List<EndorsedSkill>>
}

class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore
) : ProfileRepository {

    private fun getMockUserMap(userId: String): Map<String, Any>? {
        return when (userId) {
            "mock_user_sarah" -> mapOf(
                "id" to "mock_user_sarah",
                "name" to "Sarah Jenkins",
                "headline" to "Founder & CEO at BioSphere",
                "role" to "CEO",
                "company" to "BioSphere",
                "avatarUrl" to "",
                "location" to "Boston, MA",
                "bio" to "Building the future of sustainable food systems.",
                "tags" to listOf("ClimateTech", "Biotech", "Female Founder"),
                "primaryUserType" to "Founder",
                "membershipTier" to "FOUNDER",
                "isLinkedInConnected" to true,
                "mutualConnectionsCount" to 3,
                "connectionsCount" to 14,
                "eventsAttended" to 5,
                "followUpsCompleted" to 8
            )
            "mock_user_david" -> mapOf(
                "id" to "mock_user_david",
                "name" to "David Chen",
                "headline" to "General Partner at Nexus Ventures",
                "role" to "GP",
                "company" to "Nexus Ventures",
                "avatarUrl" to "",
                "location" to "San Francisco, CA",
                "bio" to "Investing in early stage AI and enterprise SaaS. Former developer.",
                "tags" to listOf("AI/ML", "B2B SaaS", "VC"),
                "primaryUserType" to "Investor",
                "membershipTier" to "INNER_CIRCLE",
                "isLinkedInConnected" to true,
                "mutualConnectionsCount" to 5,
                "connectionsCount" to 42,
                "eventsAttended" to 12,
                "followUpsCompleted" to 20
            )
            "mock_user_elena" -> mapOf(
                "id" to "mock_user_elena",
                "name" to "Elena Rostova",
                "headline" to "Lead Designer at Cosmos Studio",
                "role" to "Designer",
                "company" to "Cosmos Studio",
                "avatarUrl" to "",
                "location" to "New York, NY",
                "bio" to "Passionate about premium design systems and minimalist interfaces.",
                "tags" to listOf("UI/UX", "Product Design", "Creative"),
                "primaryUserType" to "Creator",
                "membershipTier" to "MEMBER",
                "isLinkedInConnected" to false,
                "mutualConnectionsCount" to 1,
                "connectionsCount" to 8,
                "eventsAttended" to 3,
                "followUpsCompleted" to 4
            )
            "mock_user_marcus" -> mapOf(
                "id" to "mock_user_marcus",
                "name" to "Marcus Vance",
                "headline" to "VP of Product at ScaleUp",
                "role" to "Product VP",
                "company" to "ScaleUp",
                "avatarUrl" to "",
                "location" to "Austin, TX",
                "bio" to "Scale specialist. Former 0-to-1 PM at Stripe and Airbnb.",
                "tags" to listOf("Product Management", "Growth", "Scaling"),
                "primaryUserType" to "Startup Operator",
                "membershipTier" to "EXPLORER",
                "isLinkedInConnected" to false,
                "mutualConnectionsCount" to 2,
                "connectionsCount" to 19,
                "eventsAttended" to 8,
                "followUpsCompleted" to 15
            )
            else -> null
        }
    }

    override suspend fun getProfile(userId: String): Result<Member> = runCatching {
        val snapshot = firestore.collection("users").document(userId).get().await()
        val member = if (!snapshot.exists()) {
            if (userId.startsWith("mock_user_")) {
                val mockMap = getMockUserMap(userId) ?: throw IllegalArgumentException("User profile $userId not found")
                firestore.collection("users").document(userId).set(mockMap).await()
                FirebaseAuthRepository.mapDocumentToMember(userId, mockMap)
            } else {
                throw IllegalArgumentException("User profile $userId not found")
            }
        } else {
            FirebaseAuthRepository.mapDocumentToMember(snapshot.id, snapshot.data ?: emptyMap())
        }
        
        // Load endorsed skills
        val skills = getEndorsedSkills(userId).getOrDefault(emptyList())
        member.copy(endorsedSkills = skills)
    }

    override suspend fun getAllProfiles(): Result<List<Member>> = runCatching {
        val snapshot = firestore.collection("users").get().await()
        snapshot.documents.map { doc ->
            val member = FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
            val skills = getEndorsedSkills(doc.id).getOrDefault(emptyList())
            member.copy(endorsedSkills = skills)
        }
    }

    override suspend fun getDiscoveryDeck(currentUserId: String): Result<List<Member>> = runCatching {
        // 1. Fetch current user profile
        val currentUser = getProfile(currentUserId).getOrThrow()
        
        // 2. Fetch all swiped UIDs to exclude them
        val swipedSnapshot = firestore.collection("swipes")
            .whereEqualTo("likerId", currentUserId)
            .get().await()
        val swipedUserIds = swipedSnapshot.documents.mapNotNull { it.getString("likedId") }.toSet()

        // 3. Fetch all other users
        val allUsersSnapshot = firestore.collection("users").get().await()
        val candidates = allUsersSnapshot.documents
            .filter { it.id != currentUserId && !swipedUserIds.contains(it.id) }
            .map { doc ->
                val member = FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
                val skills = getEndorsedSkills(doc.id).getOrDefault(emptyList())
                member.copy(endorsedSkills = skills)
            }

        // 4. Rank candidates based on matching rules
        candidates.map { candidate ->
            val sharedTags = candidate.tags.intersect(currentUser.tags.toSet()).size
            val hasSharedGoal = candidate.goalStatement.split(" ").intersect(
                currentUser.goalStatement.split(" ").toSet()
            ).isNotEmpty()

            var score = 0
            score += sharedTags * 10
            if (hasSharedGoal) score += 15
            if (candidate.isLinkedInConnected) score += 5
            score += candidate.mutualConnectionsCount * 2

            candidate to score
        }
        .filter { it.second > 0 || it.first.tags.isEmpty() || currentUser.tags.isEmpty() } // don't filter out completely if user tags are empty
        .sortedByDescending { it.second }
        .map { it.first }
    }

    override suspend fun endorseSkill(userId: String, endorserName: String, skillName: String): Result<Unit> = runCatching {
        val skillId = skillName.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
        val docRef = firestore.collection("users").document(userId)
            .collection("skills").document(skillId)

        val doc = docRef.get().await()
        if (doc.exists()) {
            val endorsers = (doc.get("endorsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (!endorsers.contains(endorserName)) {
                val newEndorsers = endorsers + endorserName
                docRef.update(
                    mapOf(
                        "count" to newEndorsers.size,
                        "endorsers" to newEndorsers
                    )
                ).await()
            }
        } else {
            docRef.set(
                mapOf(
                    "name" to skillName,
                    "count" to 1,
                    "endorsers" to listOf(endorserName)
                )
            ).await()
        }
        Unit
    }

    override suspend fun getEndorsedSkills(userId: String): Result<List<EndorsedSkill>> = runCatching {
        val snapshot = firestore.collection("users").document(userId)
            .collection("skills").get().await()
        
        snapshot.documents.map { doc ->
            EndorsedSkill(
                name = doc.getString("name") ?: "",
                count = doc.getLong("count")?.toInt() ?: 0,
                endorsers = (doc.get("endorsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}
