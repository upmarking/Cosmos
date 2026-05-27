package com.cosmos.app.data.repository

import com.cosmos.app.data.model.Circle
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
    fun getCirclePosts(circleId: String): Flow<List<CirclePost>>
    suspend fun createCirclePost(circleId: String, authorId: String, authorName: String, authorAvatar: String, content: String): Result<Unit>
    suspend fun getCircleMembers(circleId: String): Result<List<Member>>
}

data class CirclePost(
    val authorId: String,
    val author: String,
    val avatarUrl: String,
    val content: String,
    val timeString: String
)

class FirestoreCircleRepository(
    private val firestore: FirebaseFirestore
) : CircleRepository {

    private suspend fun seedFirestoreCircles() {
        val c1 = mapOf(
            "name" to "AI Builders & Founders",
            "description" to "A circle for developers and founders building next-gen generative AI applications.",
            "memberCount" to 142,
            "theme" to "AI & Technology",
            "tags" to listOf("AI/ML", "LLMs", "Tech"),
            "isPrivate" to false,
            "adminName" to "David Chen",
            "coverUrl" to ""
        )
        val c2 = mapOf(
            "name" to "Cosmos Founders Club",
            "description" to "Official private circle for verified Cosmos startup founders to share tips and resources.",
            "memberCount" to 88,
            "theme" to "Entrepreneurship",
            "tags" to listOf("Founders", "Fundraising", "Cosmos"),
            "isPrivate" to true,
            "adminName" to "Sarah Jenkins",
            "coverUrl" to ""
        )
        val c3 = mapOf(
            "name" to "Premium Design Collective",
            "description" to "Discussing aesthetics, premium typography, and UI/UX design trends.",
            "memberCount" to 56,
            "theme" to "Design",
            "tags" to listOf("Design", "UI/UX", "Aesthetics"),
            "isPrivate" to false,
            "adminName" to "Elena Rostova",
            "coverUrl" to ""
        )

        val ref1 = firestore.collection("circles").document("circle_1")
        val ref2 = firestore.collection("circles").document("circle_2")
        val ref3 = firestore.collection("circles").document("circle_3")

        firestore.runBatch { batch ->
            batch.set(ref1, c1)
            batch.set(ref2, c2)
            batch.set(ref3, c3)
        }.await()
    }

    override fun getCircles(currentUserId: String): Flow<List<Circle>> = callbackFlow {
        launch {
            try {
                val snapshot = firestore.collection("circles").get().await()
                if (snapshot.isEmpty) {
                    seedFirestoreCircles()
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
                        val isJoinedDoc = firestore.collection("circles").document(doc.id)
                            .collection("members").document(currentUserId).get().await()

                        Circle(
                            id = doc.id,
                            name = data["name"] as? String ?: "",
                            description = data["description"] as? String ?: "",
                            coverUrl = data["coverUrl"] as? String ?: "",
                            memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
                            theme = data["theme"] as? String ?: "",
                            tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            isJoined = isJoinedDoc.exists(),
                            isPrivate = data["isPrivate"] as? Boolean ?: false,
                            adminName = data["adminName"] as? String ?: "Admin"
                        )
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

        Circle(
            id = doc.id,
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            coverUrl = data["coverUrl"] as? String ?: "",
            memberCount = (data["memberCount"] as? Number)?.toInt() ?: 0,
            theme = data["theme"] as? String ?: "",
            tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isJoined = isJoinedDoc.exists(),
            isPrivate = data["isPrivate"] as? Boolean ?: false,
            adminName = data["adminName"] as? String ?: "Admin"
        )
    }

    override suspend fun joinCircle(circleId: String, userId: String): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val circleRef = firestore.collection("circles").document(circleId)
            val circleDoc = transaction.get(circleRef)
            val currentCount = circleDoc.getLong("memberCount") ?: 0

            val memberRef = circleRef.collection("members").document(userId)
            transaction.set(memberRef, mapOf("joinedAt" to FieldValue.serverTimestamp()))
            transaction.update(circleRef, "memberCount", currentCount + 1)
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
                        authorId = data["authorId"] as? String ?: "",
                        author = data["authorName"] as? String ?: "Anonymous",
                        avatarUrl = data["authorAvatar"] as? String ?: "",
                        content = data["content"] as? String ?: "",
                        timeString = time
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
            "likesCount" to 0,
            "repliesCount" to 0
        )
        firestore.collection("circles").document(circleId)
            .collection("posts").add(postDoc).await()
        Unit
    }

    override suspend fun getCircleMembers(circleId: String): Result<List<Member>> = runCatching {
        val snapshot = firestore.collection("circles").document(circleId)
            .collection("members").get().await()
        val memberIds = snapshot.documents.map { it.id }
        
        memberIds.mapNotNull { uid ->
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
            } else {
                null
            }
        }
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
