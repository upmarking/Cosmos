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

    override suspend fun getProfile(userId: String): Result<Member> = runCatching {
        val snapshot = firestore.collection("users").document(userId).get().await()
        if (!snapshot.exists()) throw IllegalArgumentException("User profile $userId not found")
        val member = FirebaseAuthRepository.mapDocumentToMember(snapshot.id, snapshot.data ?: emptyMap())
        
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
