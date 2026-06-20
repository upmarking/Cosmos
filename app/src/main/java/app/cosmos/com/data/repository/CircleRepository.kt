package app.cosmos.com.data.repository

import app.cosmos.com.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface CircleRepository {
    fun getCircles(currentUserId: String): Flow<List<Circle>>
    suspend fun getCircle(circleId: String, currentUserId: String): Result<Circle>
    suspend fun joinCircle(circleId: String, userId: String): Result<Unit>
    suspend fun leaveCircle(circleId: String, userId: String): Result<Unit>
    fun getCirclePosts(circleId: String): Flow<List<CirclePost>>
    suspend fun createCirclePost(circleId: String, authorId: String, authorName: String, authorAvatar: String, content: String): Result<Unit>
    suspend fun deleteCirclePost(circleId: String, postId: String): Result<Unit>
    suspend fun getCircleMembers(circleId: String): Result<List<Member>>
    suspend fun createCircle(circle: Circle, creatorId: String): Result<String>
    suspend fun updateCircle(circleId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteCircle(circleId: String): Result<Unit>
    suspend fun searchCircles(query: String): Result<List<Circle>>
    suspend fun likePost(circleId: String, postId: String, userId: String): Result<Unit>
    suspend fun unlikePost(circleId: String, postId: String, userId: String): Result<Unit>
    suspend fun getLikedPostIds(circleId: String, userId: String): Result<Set<String>>
    suspend fun getPendingCircleMembers(circleId: String): Result<List<Member>>
    suspend fun approveCircleJoinRequest(circleId: String, userId: String): Result<Unit>
    suspend fun declineCircleJoinRequest(circleId: String, userId: String): Result<Unit>
    fun getCirclePost(circleId: String, postId: String): Flow<CirclePost?>
    fun getCirclePostReplies(circleId: String, postId: String): Flow<List<CirclePostReply>>
    suspend fun addCirclePostReply(circleId: String, postId: String, reply: CirclePostReply): Result<Unit>
}

class FirestoreCircleRepository(
    private val firestore: FirebaseFirestore
) : CircleRepository {

    override fun getCircles(currentUserId: String): Flow<List<Circle>> = callbackFlow {
        var userJoinedCircles = emptyList<String>()
        var userPendingCircles = emptyList<String>()
        var circlesDocs = emptyList<com.google.firebase.firestore.DocumentSnapshot>()

        fun emitUpdatedList() {
            val circlesList = circlesDocs
                .filter { doc ->
                    val createdBy = doc.getString("createdBy") ?: ""
                    !createdBy.startsWith("mock_user_")
                }
                .map { doc ->
                    val data = doc.data ?: emptyMap()
                    val createdBy = doc.getString("createdBy") ?: ""
                    val isJoined = userJoinedCircles.contains(doc.id) || createdBy == currentUserId
                    val isPending = userPendingCircles.contains(doc.id)
                    mapDocumentToCircle(doc.id, data, isJoined = isJoined, isPending = isPending)
                }
            trySend(circlesList)
        }

        // Listen to current user document for joined/pending circle lists
        val userRegistration = firestore.collection("users").document(currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    userJoinedCircles = (snapshot.get("joinedCircles") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    userPendingCircles = (snapshot.get("pendingCircles") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    emitUpdatedList()
                }
            }

        // Listen to circles collection
        val circlesRegistration = firestore.collection("circles")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CircleRepository", "Error fetching circles: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                circlesDocs = snapshot.documents
                emitUpdatedList()
            }

        awaitClose {
            userRegistration.remove()
            circlesRegistration.remove()
        }
    }

    override suspend fun getCircle(circleId: String, currentUserId: String): Result<Circle> = runCatching {
        val doc = firestore.collection("circles").document(circleId).get().await()
        if (!doc.exists()) throw IllegalArgumentException("Circle $circleId not found")
        val data = doc.data ?: emptyMap()
        
        val isJoinedDoc = firestore.collection("circles").document(circleId)
            .collection("members").document(currentUserId).get().await()

        val status = isJoinedDoc?.getString("status") ?: if (isJoinedDoc?.exists() == true) "APPROVED" else "NONE"
        val createdBy = data["createdBy"] as? String ?: ""
        val isJoined = status == "APPROVED" || createdBy == currentUserId
        val isPending = status == "PENDING"

        mapDocumentToCircle(doc.id, data, isJoined = isJoined, isPending = isPending)
    }

    override suspend fun joinCircle(circleId: String, userId: String): Result<Unit> = runCatching {
        var isPrivateCircle = false
        var circleName = ""
        var createdBy = ""
        firestore.runTransaction { transaction ->
            val circleRef = firestore.collection("circles").document(circleId)
            val circleDoc = transaction.get(circleRef)
            createdBy = circleDoc.getString("createdBy") ?: ""
            if (createdBy == userId) {
                throw IllegalStateException("Cannot join your own orbit")
            }
            isPrivateCircle = circleDoc.getBoolean("isPrivate") ?: false
            circleName = circleDoc.getString("name") ?: "Private Circle"
            val currentCount = circleDoc.getLong("memberCount") ?: 0

            val memberRef = circleRef.collection("members").document(userId)
            val status = if (isPrivateCircle) "PENDING" else "APPROVED"
            transaction.set(memberRef, mapOf(
                "joinedAt" to FieldValue.serverTimestamp(),
                "status" to status
            ))
            if (!isPrivateCircle) {
                transaction.update(circleRef, "memberCount", currentCount + 1)
            }

            // Record circle membership on user document transactionally
            val userRef = firestore.collection("users").document(userId)
            val fieldName = if (isPrivateCircle) "pendingCircles" else "joinedCircles"
            transaction.update(userRef, fieldName, FieldValue.arrayUnion(circleId))
        }.await()

        if (isPrivateCircle && createdBy.isNotEmpty()) {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val senderName = userDoc.getString("name") ?: "A user"
            val notifData = mapOf(
                "userId" to createdBy,
                "type" to "COMMUNITY_ANNOUNCEMENT",
                "title" to "New Orbit Join Request! 🚀",
                "body" to "$senderName wants to join your orbit \"$circleName\".",
                "timestamp" to FieldValue.serverTimestamp(),
                "isRead" to false,
                "actionId" to circleId
            )
            firestore.collection("notifications").add(notifData).await()
        }
        Unit
    }

    override suspend fun leaveCircle(circleId: String, userId: String): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val circleRef = firestore.collection("circles").document(circleId)
            val circleDoc = transaction.get(circleRef)
            val currentCount = circleDoc.getLong("memberCount") ?: 0

            val memberRef = circleRef.collection("members").document(userId)
            val memberDoc = transaction.get(memberRef)
            val status = if (memberDoc.exists()) memberDoc.getString("status") else null
            
            if (memberDoc.exists()) {
                transaction.delete(memberRef)
            }
            
            // Decrement count if the user was an approved member, or if they didn't have a status but were in the subcollection.
            val wasApproved = memberDoc.exists() && (status == "APPROVED" || status == null)
            if (currentCount > 0 && wasApproved) {
                transaction.update(circleRef, "memberCount", currentCount - 1)
            }

            // Remove circle membership from user document transactionally
            val userRef = firestore.collection("users").document(userId)
            transaction.set(userRef, mapOf(
                "joinedCircles" to FieldValue.arrayRemove(circleId),
                "pendingCircles" to FieldValue.arrayRemove(circleId)
            ), com.google.firebase.firestore.SetOptions.merge())
        }.await()
        Unit
    }

    override fun getCirclePosts(circleId: String): Flow<List<CirclePost>> = callbackFlow {
        val registration = firestore.collection("circles").document(circleId)
            .collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CircleRepository", "Error fetching posts for circle $circleId: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val posts = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    val time = when (val ts = data["timestamp"]) {
                        is Timestamp -> formatTime(ts.toDate())
                        else -> "Now"
                    }
                    CirclePost(
                        id = doc.id,
                        authorId = data["authorId"] as? String ?: "",
                        author = data["authorName"] as? String ?: "Anonymous",
                        avatarUrl = data["authorAvatar"] as? String ?: "",
                        content = data["content"] as? String ?: "",
                        timeString = time,
                        isPinned = data["isPinned"] as? Boolean ?: false,
                        likesCount = (data["likesCount"] as? Number)?.toInt() ?: 0,
                        repliesCount = (data["repliesCount"] as? Number)?.toInt() ?: 0
                    )
                }
                trySend(posts)
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun createCirclePost(
        circleId: String,
        authorId: String,
        authorName: String,
        authorAvatar: String,
        content: String
    ): Result<Unit> = runCatching {
        val postDoc = mapOf(
            "authorId" to authorId,
            "authorName" to authorName,
            "authorAvatar" to authorAvatar,
            "content" to content,
            "timestamp" to FieldValue.serverTimestamp(),
            "isPinned" to false,
            "likesCount" to 0,
            "repliesCount" to 0
        )
        firestore.collection("circles").document(circleId)
            .collection("posts").add(postDoc).await()
        Unit
    }

    override suspend fun deleteCirclePost(circleId: String, postId: String): Result<Unit> = runCatching {
        firestore.collection("circles").document(circleId)
            .collection("posts").document(postId).delete().await()
    }

    override suspend fun getCircleMembers(circleId: String): Result<List<Member>> = runCatching {
        val circleDoc = firestore.collection("circles").document(circleId).get().await()
        val createdBy = circleDoc.getString("createdBy") ?: ""
        val adminName = circleDoc.getString("adminName") ?: "Organizer"

        val snapshot = firestore.collection("circles").document(circleId)
            .collection("members").get().await()
        val memberIds = snapshot.documents
            .filter { doc ->
                val status = doc.getString("status") ?: "APPROVED"
                status == "APPROVED"
            }
            .map { it.id }
            .toMutableSet()
            
        // Always include the orbit creator in the members list
        if (createdBy.isNotEmpty()) {
            memberIds.add(createdBy)
        }
        
        android.util.Log.d("CircleRepository", "getCircleMembers - circleId: $circleId, memberIds: $memberIds")
        
        kotlinx.coroutines.coroutineScope {
            val deferredMembers = memberIds.map { uid ->
                async {
                    try {
                        val doc = firestore.collection("users").document(uid).get().await()
                        android.util.Log.d("CircleRepository", "getCircleMembers - user doc for $uid exists: ${doc.exists()}")
                        if (doc.exists()) {
                            FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
                        } else {
                            Member(
                                id = uid,
                                name = if (uid == createdBy) adminName else "Orbit Member",
                                headline = if (uid == createdBy) "Orbit Organizer" else "Member",
                                role = if (uid == createdBy) "Organizer" else "Member",
                                company = "",
                                avatarUrl = "",
                                membershipTier = MembershipTier.EXPLORER
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CircleRepository", "getCircleMembers - error fetching user $uid", e)
                        Member(
                            id = uid,
                            name = if (uid == createdBy) adminName else "Orbit Member",
                            headline = if (uid == createdBy) "Orbit Organizer" else "Member",
                            role = if (uid == createdBy) "Organizer" else "Member",
                            company = "",
                            avatarUrl = "",
                            membershipTier = MembershipTier.EXPLORER
                        )
                    }
                }
            }
            deferredMembers.awaitAll().filterNotNull()
        }
    }

    override suspend fun createCircle(circle: Circle, creatorId: String): Result<String> = runCatching {
        val circleMap = mapOf(
            "name" to circle.name,
            "description" to circle.description,
            "coverUrl" to circle.coverUrl,
            "memberCount" to 1, // creator is first member
            "theme" to circle.theme,
            "tags" to circle.tags,
            "isPrivate" to circle.isPrivate,
            "adminName" to circle.adminName,
            "createdBy" to creatorId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val docRef = firestore.collection("circles").add(circleMap).await()
        // Auto-join creator with APPROVED status
        docRef.collection("members").document(creatorId)
            .set(mapOf(
                "joinedAt" to FieldValue.serverTimestamp(),
                "status" to "APPROVED"
            )).await()
        // Update user document joinedCircles
        firestore.collection("users").document(creatorId)
            .update("joinedCircles", FieldValue.arrayUnion(docRef.id)).await()
        docRef.id
    }

    override suspend fun updateCircle(circleId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("circles").document(circleId).update(updates).await()
    }

    override suspend fun deleteCircle(circleId: String): Result<Unit> = runCatching {
        // Delete sub-collections
        val members = firestore.collection("circles").document(circleId)
            .collection("members").get().await()
        members.documents.forEach { it.reference.delete().await() }

        val posts = firestore.collection("circles").document(circleId)
            .collection("posts").get().await()
        posts.documents.forEach { it.reference.delete().await() }

        firestore.collection("circles").document(circleId).delete().await()
    }

    override suspend fun searchCircles(query: String): Result<List<Circle>> = runCatching {
        val snapshot = firestore.collection("circles").get().await()
        val queryLower = query.lowercase().trim()

        snapshot.documents
            .filter { doc ->
                val createdBy = doc.getString("createdBy") ?: ""
                !createdBy.startsWith("mock_user_")
            }
            .map { doc -> mapDocumentToCircle(doc.id, doc.data ?: emptyMap(), isJoined = false, isPending = false) }
            .filter { circle ->
                queryLower.isEmpty() ||
                circle.name.lowercase().contains(queryLower) ||
                circle.description.lowercase().contains(queryLower) ||
                circle.theme.lowercase().contains(queryLower) ||
                circle.tags.any { it.lowercase().contains(queryLower) }
            }
    }

    override suspend fun likePost(circleId: String, postId: String, userId: String): Result<Unit> = runCatching {
        val postRef = firestore.collection("circles").document(circleId)
            .collection("posts").document(postId)
        firestore.runTransaction { transaction ->
            val doc = transaction.get(postRef)
            val currentLikes = (doc.get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (!currentLikes.contains(userId)) {
                transaction.update(postRef, mapOf(
                    "likes" to FieldValue.arrayUnion(userId),
                    "likesCount" to (doc.getLong("likesCount") ?: 0) + 1
                ))
            }
        }.await()
    }

    override suspend fun unlikePost(circleId: String, postId: String, userId: String): Result<Unit> = runCatching {
        val postRef = firestore.collection("circles").document(circleId)
            .collection("posts").document(postId)
        firestore.runTransaction { transaction ->
            val doc = transaction.get(postRef)
            val currentLikes = (doc.get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (currentLikes.contains(userId)) {
                transaction.update(postRef, mapOf(
                    "likes" to FieldValue.arrayRemove(userId),
                    "likesCount" to ((doc.getLong("likesCount") ?: 1) - 1).coerceAtLeast(0)
                ))
            }
        }.await()
    }

    override suspend fun getLikedPostIds(circleId: String, userId: String): Result<Set<String>> = runCatching {
        val snapshot = firestore.collection("circles").document(circleId)
            .collection("posts")
            .whereArrayContains("likes", userId)
            .get()
            .await()
        snapshot.documents.map { it.id }.toSet()
    }

    override suspend fun getPendingCircleMembers(circleId: String): Result<List<Member>> = runCatching {
        val circleDoc = firestore.collection("circles").document(circleId).get().await()
        val creatorId = circleDoc.getString("createdBy") ?: ""

        val snapshot = firestore.collection("circles").document(circleId)
            .collection("members")
            .whereEqualTo("status", "PENDING")
            .get().await()
        val memberIds = snapshot.documents.map { it.id }
            .filter { it != creatorId }
        
        kotlinx.coroutines.coroutineScope {
            val deferredMembers = memberIds.map { uid ->
                async {
                    try {
                        val doc = firestore.collection("users").document(uid).get().await()
                        if (doc.exists()) {
                            FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
                        } else null
                    } catch (e: Exception) { null }
                }
            }
            deferredMembers.awaitAll().filterNotNull()
        }
    }

    override suspend fun approveCircleJoinRequest(circleId: String, userId: String): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val circleRef = firestore.collection("circles").document(circleId)
            val circleDoc = transaction.get(circleRef)
            val currentCount = circleDoc.getLong("memberCount") ?: 0

            val memberRef = circleRef.collection("members").document(userId)
            transaction.update(memberRef, "status", "APPROVED")
            transaction.update(circleRef, "memberCount", currentCount + 1)

            val userRef = firestore.collection("users").document(userId)
            transaction.update(userRef, "pendingCircles", FieldValue.arrayRemove(circleId))
            transaction.update(userRef, "joinedCircles", FieldValue.arrayUnion(circleId))
        }.await()

        val notifData = mapOf(
            "userId" to userId,
            "type" to "COMMUNITY_ANNOUNCEMENT",
            "title" to "Orbit Request Approved! 🚀",
            "body" to "You have been approved to join the orbit.",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to circleId
        )
        firestore.collection("notifications").add(notifData).await()
    }

    override suspend fun declineCircleJoinRequest(circleId: String, userId: String): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val circleRef = firestore.collection("circles").document(circleId)
            val memberRef = circleRef.collection("members").document(userId)
            transaction.delete(memberRef)

            val userRef = firestore.collection("users").document(userId)
            transaction.update(userRef, "pendingCircles", FieldValue.arrayRemove(circleId))
        }.await()
    }

    override fun getCirclePost(circleId: String, postId: String): Flow<CirclePost?> = callbackFlow {
        val registration = firestore.collection("circles").document(circleId)
            .collection("posts").document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CircleRepository", "Error fetching post $postId: ${error.message}", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val data = snapshot.data ?: emptyMap()
                val time = when (val ts = data["timestamp"]) {
                    is Timestamp -> formatTime(ts.toDate())
                    else -> "Now"
                }
                val post = CirclePost(
                    id = snapshot.id,
                    authorId = data["authorId"] as? String ?: "",
                    author = data["authorName"] as? String ?: "Anonymous",
                    avatarUrl = data["authorAvatar"] as? String ?: "",
                    content = data["content"] as? String ?: "",
                    timeString = time,
                    isPinned = data["isPinned"] as? Boolean ?: false,
                    likesCount = (data["likesCount"] as? Number)?.toInt() ?: 0,
                    repliesCount = (data["repliesCount"] as? Number)?.toInt() ?: 0
                )
                trySend(post)
            }
        awaitClose {
            registration.remove()
        }
    }

    override fun getCirclePostReplies(circleId: String, postId: String): Flow<List<CirclePostReply>> = callbackFlow {
        val registration = firestore.collection("circles").document(circleId)
            .collection("posts").document(postId)
            .collection("replies")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CircleRepository", "Error fetching replies for post $postId: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val replies = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    val time = when (val ts = data["timestamp"]) {
                        is Timestamp -> formatTime(ts.toDate())
                        else -> "Now"
                    }
                    CirclePostReply(
                        id = doc.id,
                        authorId = data["authorId"] as? String ?: "",
                        authorName = data["authorName"] as? String ?: "Anonymous",
                        authorAvatarUrl = data["authorAvatarUrl"] as? String ?: "",
                        authorHeadline = data["authorHeadline"] as? String ?: "",
                        content = data["content"] as? String ?: "",
                        timeString = time
                    )
                }
                trySend(replies)
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun addCirclePostReply(circleId: String, postId: String, reply: CirclePostReply): Result<Unit> = runCatching {
        val replyDoc = mapOf(
            "authorId" to reply.authorId,
            "authorName" to reply.authorName,
            "authorAvatarUrl" to reply.authorAvatarUrl,
            "authorHeadline" to reply.authorHeadline,
            "content" to reply.content,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.runTransaction { transaction ->
            val postRef = firestore.collection("circles").document(circleId)
                .collection("posts").document(postId)
            val postSnapshot = transaction.get(postRef)
            val currentRepliesCount = postSnapshot.getLong("repliesCount") ?: 0

            val replyRef = postRef.collection("replies").document()
            transaction.set(replyRef, replyDoc)

            transaction.update(postRef, "repliesCount", currentRepliesCount + 1)
        }.await()
        Unit
    }

    private fun mapDocumentToCircle(id: String, data: Map<String, Any>, isJoined: Boolean, isPending: Boolean): Circle {
        return Circle(
            id = id,
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            coverUrl = data["coverUrl"] as? String ?: "",
            memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
            theme = data["theme"] as? String ?: "",
            tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isJoined = isJoined,
            isPrivate = data["isPrivate"] as? Boolean ?: false,
            adminName = data["adminName"] as? String ?: "Admin",
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = (data["createdAt"] as? Timestamp)?.seconds ?: 0L,
            isPending = isPending
        )
    }

    private fun formatTime(date: Date): String {
        val diff = System.currentTimeMillis() - date.time
        return when {
            diff < 60_000 -> "now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
        }
    }
}
