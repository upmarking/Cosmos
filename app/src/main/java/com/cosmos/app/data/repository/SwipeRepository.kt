package com.cosmos.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

interface SwipeRepository {
    suspend fun recordSwipe(likerId: String, likedId: String, action: String): Result<Boolean>
    suspend fun getMonthlyConnectionsCount(userId: String): Result<Int>
    suspend fun undoLastSwipe(userId: String): Result<Unit>
}

class FirestoreSwipeRepository(
    private val firestore: FirebaseFirestore
) : SwipeRepository {

    override suspend fun recordSwipe(likerId: String, likedId: String, action: String): Result<Boolean> = runCatching {
        val swipeId = "${likerId}_${likedId}"
        
        // Write the swipe record (used for discovery deck filtering only)
        val swipeData = mapOf(
            "likerId" to likerId,
            "likedId" to likedId,
            "action" to action,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("swipes").document(swipeId).set(swipeData).await()

        // Connection creation is now handled by ConnectionRequestRepository
        false
    }

    override suspend fun getMonthlyConnectionsCount(userId: String): Result<Int> = runCatching {
        // Get start of current month
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = Timestamp(calendar.time)

        // Find connections created this month
        val snapshot = firestore.collection("connections")
            .whereArrayContains("members", userId)
            .get().await()
        
        // Filter by creation date this month
        snapshot.documents.count { doc ->
            val createdAt = doc.getTimestamp("createdAt")
            createdAt != null && createdAt >= startOfMonth
        }
    }

    override suspend fun undoLastSwipe(userId: String): Result<Unit> = runCatching {
        // Find the most recent swipe by this user
        val snapshot = firestore.collection("swipes")
            .whereEqualTo("likerId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get().await()

        if (snapshot.documents.isNotEmpty()) {
            val lastSwipe = snapshot.documents.first()
            val likedId = lastSwipe.getString("likedId") ?: return@runCatching

            // Delete the swipe
            lastSwipe.reference.delete().await()

            // If it created a connection, remove that too
            val connectionId = if (userId < likedId) "${userId}_${likedId}" else "${likedId}_${userId}"
            val connDoc = firestore.collection("connections").document(connectionId).get().await()
            if (connDoc.exists()) {
                // Delete messages sub-collection
                val messages = connDoc.reference.collection("messages").get().await()
                messages.documents.forEach { it.reference.delete().await() }
                connDoc.reference.delete().await()
            }
        }
    }
}
