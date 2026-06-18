package com.cosmos.app.data.repository

import com.cosmos.app.data.model.EndorsedSkill
import com.cosmos.app.data.model.Member
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

interface ProfileRepository {
    suspend fun getProfile(userId: String, fetchSkills: Boolean = true): Result<Member>
    suspend fun getAllProfiles(): Result<List<Member>>
    suspend fun getDiscoveryDeck(currentUserId: String): Result<List<Member>>
    suspend fun endorseSkill(userId: String, endorserName: String, skillName: String): Result<Unit>
    suspend fun getEndorsedSkills(userId: String): Result<List<EndorsedSkill>>
    suspend fun updateProfile(userId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun searchProfiles(query: String, tags: List<String> = emptyList(), userType: String = ""): Result<List<Member>>
    suspend fun getProfilesPaginated(lastDocId: String?, limit: Int = 20): Result<List<Member>>
}

class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore
) : ProfileRepository {

    override suspend fun getProfile(userId: String, fetchSkills: Boolean): Result<Member> = runCatching {
        if (fetchSkills) {
            kotlinx.coroutines.coroutineScope {
                val docDeferred = async { firestore.collection("users").document(userId).get().await() }
                val skillsDeferred = async { getEndorsedSkills(userId).getOrDefault(emptyList()) }
                
                val snapshot = docDeferred.await()
                if (!snapshot.exists()) {
                    throw IllegalArgumentException("User profile $userId not found")
                }
                val member = FirebaseAuthRepository.mapDocumentToMember(snapshot.id, snapshot.data ?: emptyMap())
                val skills = skillsDeferred.await()
                member.copy(endorsedSkills = skills)
            }
        } else {
            val snapshot = firestore.collection("users").document(userId).get().await()
            if (!snapshot.exists()) {
                throw IllegalArgumentException("User profile $userId not found")
            }
            FirebaseAuthRepository.mapDocumentToMember(snapshot.id, snapshot.data ?: emptyMap())
        }
    }

    override suspend fun getAllProfiles(): Result<List<Member>> = runCatching {
        val snapshot = firestore.collection("users").get().await()
        snapshot.documents
            .filter { doc -> !doc.id.startsWith("mock_user_") }
            .map { doc ->
                FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
            }
    }

    override suspend fun getDiscoveryDeck(currentUserId: String): Result<List<Member>> = runCatching {
        // 1. Fetch current user profile (skills are not needed for discovery scoring)
        val currentUser = getProfile(currentUserId, fetchSkills = false).getOrThrow()
        
        // 2. Fetch all swiped UIDs to exclude them
        val swipedSnapshot = firestore.collection("swipes")
            .whereEqualTo("likerId", currentUserId)
            .get().await()
        val swipedUserIds = swipedSnapshot.documents.mapNotNull { it.getString("likedId") }.toSet()

        // 3. Fetch all other users (excluding restricted users)
        val allUsersSnapshot = firestore.collection("users").get().await()
        val candidates = allUsersSnapshot.documents
            .filter { doc ->
                doc.id != currentUserId &&
                !swipedUserIds.contains(doc.id) &&
                doc.getBoolean("isRestricted") != true &&
                !doc.id.startsWith("mock_user_")
            }
            .map { doc ->
                FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
            }

        // 4. Rank candidates based on matching rules
        candidates.map { candidate ->
            val sharedTags = candidate.tags.intersect(currentUser.tags.toSet()).size
            val hasSharedGoal = if (currentUser.goalStatement.isNotBlank() && candidate.goalStatement.isNotBlank()) {
                candidate.goalStatement.split(" ").intersect(
                    currentUser.goalStatement.split(" ").toSet()
                ).isNotEmpty()
            } else false

            var score = 0
            score += sharedTags * 10
            if (hasSharedGoal) score += 15
            if (candidate.isLinkedInConnected) score += 5
            score += candidate.mutualConnectionsCount * 2
            // Boost for complementary lookingFor
            val sharedLookingFor = candidate.lookingFor.intersect(currentUser.lookingFor.toSet()).size
            score += sharedLookingFor * 8

            candidate to score
        }
        .filter { it.second > 0 || it.first.tags.isEmpty() || currentUser.tags.isEmpty() }
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

        // Create notification for the endorsed user
        val notifData = mapOf(
            "userId" to userId,
            "type" to "ENDORSEMENT_RECEIVED",
            "title" to "New Endorsement",
            "body" to "$endorserName endorsed your $skillName skill.",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to userId
        )
        firestore.collection("notifications").add(notifData).await()
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

    override suspend fun updateProfile(userId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        val updatesWithTimestamp = updates.toMutableMap()
        updatesWithTimestamp["updatedAt"] = FieldValue.serverTimestamp()
        firestore.collection("users").document(userId)
            .set(updatesWithTimestamp, SetOptions.merge()).await()
    }

    override suspend fun searchProfiles(
        query: String,
        tags: List<String>,
        userType: String
    ): Result<List<Member>> = runCatching {
        // Firestore doesn't support full-text search natively
        // So we fetch all and filter client-side (for small datasets)
        // For production scale, use Algolia/Typesense/Cloud Functions
        val allProfiles = getAllProfiles().getOrThrow()
        val queryLower = query.lowercase().trim()

        allProfiles.filter { member ->
            val matchesQuery = queryLower.isEmpty() ||
                member.name.lowercase().contains(queryLower) ||
                member.headline.lowercase().contains(queryLower) ||
                member.company.lowercase().contains(queryLower) ||
                member.bio.lowercase().contains(queryLower) ||
                member.tags.any { it.lowercase().contains(queryLower) }

            val matchesTags = tags.isEmpty() ||
                member.tags.any { memberTag -> tags.any { filterTag -> memberTag.equals(filterTag, ignoreCase = true) } }

            val matchesType = userType.isEmpty() ||
                member.primaryUserType.equals(userType, ignoreCase = true)

            matchesQuery && matchesTags && matchesType && !member.isRestricted
        }
    }

    override suspend fun getProfilesPaginated(lastDocId: String?, limit: Int): Result<List<Member>> = runCatching {
        var query: Query = firestore.collection("users")
            .orderBy("name")
            .limit(limit.toLong())

        if (lastDocId != null) {
            val lastDoc = firestore.collection("users").document(lastDocId).get().await()
            if (lastDoc.exists()) {
                query = query.startAfter(lastDoc)
            }
        }

        val snapshot = query.get().await()
        snapshot.documents
            .filter { doc -> !doc.id.startsWith("mock_user_") }
            .map { doc ->
                FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
            }
    }
}
