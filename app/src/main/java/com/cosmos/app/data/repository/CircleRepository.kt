package com.cosmos.app.data.repository

import com.cosmos.app.data.model.Circle
import com.cosmos.app.data.model.CirclePost
import com.cosmos.app.data.model.Member
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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
}

class FirestoreCircleRepository(
    private val firestore: FirebaseFirestore
) : CircleRepository {

    override fun getCircles(currentUserId: String): Flow<List<Circle>> = callbackFlow {

        // Seed circles if collection is empty (first launch)
        launch {
            try {
                val snapshot = firestore.collection("circles").get().await()
                if (snapshot.isEmpty) {
                    FirestoreSeedService.seedCircles(firestore)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val registration = firestore.collection("circles")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                launch {
                    val circles = snapshot.documents.map { doc ->
                        val data = doc.data ?: emptyMap()
                        val isJoinedDoc = try {
                            firestore.collection("circles").document(doc.id)
                                .collection("members").document(currentUserId).get().await()
                        } catch (e: Exception) { null }

                        val status = isJoinedDoc?.getString("status") ?: if (isJoinedDoc?.exists() == true) "APPROVED" else "NONE"
                        val isJoined = status == "APPROVED"
                        val isPending = status == "PENDING"

                        mapDocumentToCircle(doc.id, data, isJoined = isJoined, isPending = isPending)
                    }
                    trySend(circles)
                }
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun getCircle(circleId: String, currentUserId: String): Result<Circle> = runCatching {
        val doc = firestore.collection("circles").document(circleId).get().await()
        if (!doc.exists()) throw IllegalArgumentException("Circle $circleId not found")
        val data = doc.data ?: emptyMap()
        
        val isJoinedDoc = firestore.collection("circles").document(circleId)
            .collection("members").document(currentUserId).get().await()

        val status = isJoinedDoc?.getString("status") ?: if (isJoinedDoc?.exists() == true) "APPROVED" else "NONE"
        val isJoined = status == "APPROVED"
        val isPending = status == "PENDING"

        mapDocumentToCircle(doc.id, data, isJoined = isJoined, isPending = isPending)
    }

    override suspend fun joinCircle(circleId: String, userId: String): Result<Unit> = runCatching {
        var isPrivateCircle = false
        var circleName = ""
        firestore.runTransaction { transaction ->
            val circleRef = firestore.collection("circles").document(circleId)
            val circleDoc = transaction.get(circleRef)
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
        }.await()

        if (isPrivateCircle) {
            // Simulate approval after 5 seconds
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                kotlinx.coroutines.delay(5000)
                try {
                    firestore.runTransaction { transaction ->
                        val circleRef = firestore.collection("circles").document(circleId)
                        val circleDoc = transaction.get(circleRef)
                        val currentCount = circleDoc.getLong("memberCount") ?: 0

                        val memberRef = circleRef.collection("members").document(userId)
                        transaction.update(memberRef, "status", "APPROVED")
                        transaction.update(circleRef, "memberCount", currentCount + 1)
                    }.await()

                    // Add a notification for the user
                    val notifDoc = mapOf(
                        "type" to com.cosmos.app.data.model.NotificationType.COMMUNITY_ANNOUNCEMENT.name,
                        "title" to "Access Approved! 🎉",
                        "body" to "Your request to join $circleName has been approved by the moderator.",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false,
                        "actionId" to circleId
                    )
                    firestore.collection("users").document(userId)
                        .collection("notifications").add(notifDoc).await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
            val status = memberDoc.getString("status") ?: ""
            
            transaction.delete(memberRef)
            if (currentCount > 0 && status == "APPROVED") {
                transaction.update(circleRef, "memberCount", currentCount - 1)
            }
        }.await()
        Unit
    }

    override fun getCirclePosts(circleId: String): Flow<List<CirclePost>> = callbackFlow {
        val registration = firestore.collection("circles").document(circleId)
            .collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
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
        val snapshot = firestore.collection("circles").document(circleId)
            .collection("members").get().await()
        val memberIds = snapshot.documents.map { it.id }
        
        memberIds.mapNotNull { uid ->
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
                } else null
            } catch (e: Exception) { null }
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
            .collection("posts").get().await()
        snapshot.documents.filter { doc ->
            val likes = (doc.get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            likes.contains(userId)
        }.map { it.id }.toSet()
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
