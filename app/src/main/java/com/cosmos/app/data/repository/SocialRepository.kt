package com.cosmos.app.data.repository

import com.cosmos.app.data.model.SocialPost
import com.cosmos.app.data.model.SocialPostReply
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface SocialRepository {
    fun getSocialPosts(): Flow<List<SocialPost>>
    suspend fun createSocialPost(
        authorId: String,
        authorName: String,
        authorAvatarUrl: String,
        authorHeadline: String,
        content: String,
        imageUrl: String? = null
    ): Result<Unit>
    suspend fun deleteSocialPost(postId: String): Result<Unit>
    suspend fun likePost(postId: String, userId: String): Result<Unit>
    suspend fun unlikePost(postId: String, userId: String): Result<Unit>
    suspend fun getLikedPostIds(userId: String): Result<Set<String>>
    
    fun getPostReplies(postId: String): Flow<List<SocialPostReply>>
    suspend fun addPostReply(postId: String, reply: SocialPostReply): Result<Unit>
}

class FirestoreSocialRepository(
    private val firestore: FirebaseFirestore
) : SocialRepository {

    override fun getSocialPosts(): Flow<List<SocialPost>> = callbackFlow {
        val registration = firestore.collection("social_posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("SocialRepository", "Error fetching social posts: ${error.message}", error)
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
                    SocialPost(
                        id = doc.id,
                        authorId = data["authorId"] as? String ?: "",
                        authorName = data["authorName"] as? String ?: "Anonymous",
                        authorAvatarUrl = data["authorAvatarUrl"] as? String ?: "",
                        authorHeadline = data["authorHeadline"] as? String ?: "",
                        content = data["content"] as? String ?: "",
                        timeString = time,
                        imageUrl = data["imageUrl"] as? String,
                        likesCount = (data["likesCount"] as? Number)?.toInt() ?: 0,
                        repliesCount = (data["repliesCount"] as? Number)?.toInt() ?: 0,
                        likes = (data["likes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )
                }
                trySend(posts)
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun createSocialPost(
        authorId: String,
        authorName: String,
        authorAvatarUrl: String,
        authorHeadline: String,
        content: String,
        imageUrl: String?
    ): Result<Unit> = runCatching {
        val postDoc = mutableMapOf(
            "authorId" to authorId,
            "authorName" to authorName,
            "authorAvatarUrl" to authorAvatarUrl,
            "authorHeadline" to authorHeadline,
            "content" to content,
            "timestamp" to FieldValue.serverTimestamp(),
            "likesCount" to 0,
            "repliesCount" to 0,
            "likes" to emptyList<String>()
        )
        if (imageUrl != null) {
            postDoc["imageUrl"] = imageUrl
        }
        firestore.collection("social_posts").add(postDoc).await()
        Unit
    }

    override suspend fun deleteSocialPost(postId: String): Result<Unit> = runCatching {
        // Delete subcollection replies first
        val replies = firestore.collection("social_posts").document(postId)
            .collection("replies").get().await()
        replies.documents.forEach { it.reference.delete().await() }
        
        firestore.collection("social_posts").document(postId).delete().await()
        Unit
    }

    override suspend fun likePost(postId: String, userId: String): Result<Unit> = runCatching {
        val postRef = firestore.collection("social_posts").document(postId)
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
        Unit
    }

    override suspend fun unlikePost(postId: String, userId: String): Result<Unit> = runCatching {
        val postRef = firestore.collection("social_posts").document(postId)
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
        Unit
    }

    override suspend fun getLikedPostIds(userId: String): Result<Set<String>> = runCatching {
        val snapshot = firestore.collection("social_posts")
            .whereArrayContains("likes", userId)
            .get()
            .await()
        snapshot.documents.map { it.id }.toSet()
    }

    override fun getPostReplies(postId: String): Flow<List<SocialPostReply>> = callbackFlow {
        val registration = firestore.collection("social_posts").document(postId)
            .collection("replies")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("SocialRepository", "Error fetching replies for post $postId: ${error.message}", error)
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
                    SocialPostReply(
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

    override suspend fun addPostReply(postId: String, reply: SocialPostReply): Result<Unit> = runCatching {
        val replyDoc = mapOf(
            "authorId" to reply.authorId,
            "authorName" to reply.authorName,
            "authorAvatarUrl" to reply.authorAvatarUrl,
            "authorHeadline" to reply.authorHeadline,
            "content" to reply.content,
            "timestamp" to FieldValue.serverTimestamp()
        )
        
        firestore.runTransaction { transaction ->
            val postRef = firestore.collection("social_posts").document(postId)
            val postSnapshot = transaction.get(postRef)
            val currentRepliesCount = postSnapshot.getLong("repliesCount") ?: 0
            
            val replyRef = postRef.collection("replies").document()
            transaction.set(replyRef, replyDoc)
            
            transaction.update(postRef, "repliesCount", currentRepliesCount + 1)
        }.await()
        Unit
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
