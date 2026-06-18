package com.cosmos.app.data.repository

import com.cosmos.app.data.model.Notification
import com.cosmos.app.data.model.NotificationType
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

interface NotificationRepository {
    fun getNotifications(userId: String): Flow<List<Notification>>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun markAllAsRead(userId: String): Result<Unit>
    suspend fun createNotification(userId: String, type: NotificationType, title: String, body: String, actionId: String): Result<Unit>
    suspend fun deleteNotification(notificationId: String): Result<Unit>
    fun getUnreadCount(userId: String): Flow<Int>
}

class FirestoreNotificationRepository(
    private val firestore: FirebaseFirestore
) : NotificationRepository {

    override fun getNotifications(userId: String): Flow<List<Notification>> = callbackFlow {
        val registration = firestore.collection("notifications")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CosmosNotifications", "Error fetching notifications for $userId", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val sortedDocs = snapshot.documents.sortedByDescending { doc ->
                    doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp(0, 0)
                }
                val notifications = sortedDocs.map { doc ->
                    val data = doc.data ?: emptyMap()
                    val typeStr = data["type"] as? String ?: NotificationType.NEW_MATCH.name
                    val type = runCatching { NotificationType.valueOf(typeStr) }.getOrDefault(NotificationType.NEW_MATCH)
                    val time = when (val ts = data["timestamp"]) {
                        is Timestamp -> formatTime(ts.toDate())
                        else -> "Now"
                    }
                    Notification(
                        id = doc.id,
                        type = type,
                        title = data["title"] as? String ?: "",
                        body = data["body"] as? String ?: "",
                        timestamp = time,
                        isRead = data["isRead"] as? Boolean ?: false,
                        actionId = data["actionId"] as? String ?: ""
                    )
                }
                trySend(notifications)
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        firestore.collection("notifications").document(notificationId)
            .update("isRead", true).await()
    }

    override suspend fun markAllAsRead(userId: String): Result<Unit> = runCatching {
        val snapshot = firestore.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get().await()
        
        val batch = firestore.batch()
        snapshot.documents.forEach { doc ->
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    override suspend fun createNotification(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        actionId: String
    ): Result<Unit> = runCatching {
        val data = mapOf(
            "userId" to userId,
            "type" to type.name,
            "title" to title,
            "body" to body,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to actionId
        )
        firestore.collection("notifications").add(data).await()
        Unit
    }

    override suspend fun deleteNotification(notificationId: String): Result<Unit> = runCatching {
        firestore.collection("notifications").document(notificationId).delete().await()
    }

    override fun getUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose {
            registration.remove()
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
