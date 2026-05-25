package com.cosmos.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface SwipeRepository {
    suspend fun recordSwipe(likerId: String, likedId: String, action: String): Result<Boolean>
    suspend fun getMonthlyConnectionsCount(userId: String): Result<Int>
}

class FirestoreSwipeRepository(
    private val firestore: FirebaseFirestore
) : SwipeRepository {

    override suspend fun recordSwipe(likerId: String, likedId: String, action: String): Result<Boolean> = runCatching {
        val swipeId = "${likerId}_${likedId}"
        
        // Write the swipe record
        val swipeData = mapOf(
            "likerId" to likerId,
            "likedId" to likedId,
            "action" to action,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("swipes").document(swipeId).set(swipeData).await()

        if (action == "LIKE") {
            // Check if reverse swipe exists and is also a LIKE
            val reverseSwipeId = "${likedId}_${likerId}"
            val reverseSwipeDoc = firestore.collection("swipes").document(reverseSwipeId).get().await()
            if (reverseSwipeDoc.exists() && reverseSwipeDoc.getString("action") == "LIKE") {
                // Mutual Match! Create connection
                val connectionId = if (likerId < likedId) "${likerId}_${likedId}" else "${likedId}_${likerId}"
                val connectionData = mapOf(
                    "id" to connectionId,
                    "members" to listOf(likerId, likedId),
                    "lastMessage" to "You matched! Say hello.",
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "unreadCountMap" to mapOf(likerId to 0, likedId to 0),
                    "labels" to mapOf(likerId to emptyList<String>(), likedId to emptyList<String>()),
                    "privateGoals" to mapOf(likerId to "", likedId to ""),
                    "status" to "ACTIVE",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("connections").document(connectionId).set(connectionData).await()
                
                // Add a notification for both users
                val notificationData1 = mapOf(
                    "userId" to likerId,
                    "type" to "NEW_MATCH",
                    "title" to "New Match! 🎉",
                    "body" to "You matched with another member. Start a conversation now!",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isRead" to false,
                    "actionId" to likedId
                )
                val notificationData2 = mapOf(
                    "userId" to likedId,
                    "type" to "NEW_MATCH",
                    "title" to "New Match! 🎉",
                    "body" to "You matched with another member. Start a conversation now!",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isRead" to false,
                    "actionId" to likerId
                )
                firestore.collection("notifications").add(notificationData1)
                firestore.collection("notifications").add(notificationData2)
                
                return@runCatching true
            }
        }
        false
    }

    override suspend fun getMonthlyConnectionsCount(userId: String): Result<Int> = runCatching {
        // Find all connections matching the user
        val snapshot = firestore.collection("connections")
            .whereArrayContains("members", userId)
            .get().await()
        
        // Return size of connections matching this month (simplified count)
        snapshot.size()
    }
}
