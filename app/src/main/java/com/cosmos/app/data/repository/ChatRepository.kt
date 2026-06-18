package com.cosmos.app.data.repository

import com.cosmos.app.data.model.ChatMessage
import com.cosmos.app.data.model.Connection
import com.cosmos.app.data.model.ConnectionStatus
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MessageType
import com.cosmos.app.data.model.MembershipTier
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

interface ChatRepository {
    fun getConnections(userId: String): Flow<List<Connection>>
    fun getConnection(connectionId: String, currentUserId: String): Flow<Connection?>
    fun getMessages(connectionId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(connectionId: String, senderId: String, text: String, type: MessageType = MessageType.TEXT): Result<Unit>
    suspend fun updateCrmLabels(connectionId: String, userId: String, labels: List<String>): Result<Unit>
    suspend fun updatePrivateGoal(connectionId: String, userId: String, goal: String): Result<Unit>
    suspend fun saveAiSummary(connectionId: String, summaryText: String): Result<Unit>
    suspend fun markMessagesAsRead(connectionId: String, userId: String): Result<Unit>
    suspend fun deleteMessage(connectionId: String, messageId: String): Result<Unit>
    suspend fun searchMessages(connectionId: String, query: String): Result<List<ChatMessage>>
    fun getUnreadCountTotal(userId: String): Flow<Int>
}

class FirestoreChatRepository(
    private val firestore: FirebaseFirestore,
    private val profileRepository: ProfileRepository
) : ChatRepository {

    override fun getConnections(userId: String): Flow<List<Connection>> = callbackFlow {
        val registration = firestore.collection("connections")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ChatRepository", "Error fetching connections for user $userId: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val connections = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    val members = (data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val otherMemberId = members.firstOrNull { it != userId } ?: ""
                    
                    val placeholderMember = Member(
                        id = otherMemberId,
                        name = "Member $otherMemberId",
                        headline = "Loading details...",
                        role = "",
                        company = "",
                        avatarUrl = "",
                        membershipTier = MembershipTier.EXPLORER
                    )
                    
                    val lastMessageTime = when (val time = data["lastMessageTime"]) {
                        is Timestamp -> formatTime(time.toDate())
                        else -> "Now"
                    }

                    val unreadCountMap = data["unreadCountMap"] as? Map<String, Any>
                    val unreadCount = (unreadCountMap?.get(userId) as? Number)?.toInt() ?: 0

                    val labelsMap = data["labels"] as? Map<String, Any>
                    val labels = (labelsMap?.get(userId) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                    val privateGoalsMap = data["privateGoals"] as? Map<String, Any>
                    val privateGoal = (privateGoalsMap?.get(userId) as? String) ?: ""

                    val statusStr = data["status"] as? String ?: ConnectionStatus.ACTIVE.name
                    val status = runCatching { ConnectionStatus.valueOf(statusStr) }.getOrDefault(ConnectionStatus.ACTIVE)

                    Connection(
                        id = doc.id,
                        member = placeholderMember,
                        lastMessage = data["lastMessage"] as? String ?: "",
                        lastMessageTime = lastMessageTime,
                        unreadCount = unreadCount,
                        labels = labels,
                        privateGoal = privateGoal,
                        status = status
                    )
                }
                trySend(connections)
            }
        awaitClose {
            registration.remove()
        }
    }

    override fun getConnection(connectionId: String, currentUserId: String): Flow<Connection?> = callbackFlow {
        val registration = firestore.collection("connections").document(connectionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ChatRepository", "Error fetching connection $connectionId: ${error.message}", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val data = snapshot.data ?: emptyMap()
                val members = (data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val otherMemberId = members.firstOrNull { it != currentUserId } ?: ""

                val lastMessageTime = when (val time = data["lastMessageTime"]) {
                    is Timestamp -> formatTime(time.toDate())
                    else -> "Now"
                }

                val unreadCountMap = data["unreadCountMap"] as? Map<String, Any>
                val unreadCount = (unreadCountMap?.get(currentUserId) as? Number)?.toInt() ?: 0

                val labelsMap = data["labels"] as? Map<String, Any>
                val labels = (labelsMap?.get(currentUserId) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                val privateGoalsMap = data["privateGoals"] as? Map<String, Any>
                val privateGoal = (privateGoalsMap?.get(currentUserId) as? String) ?: ""

                val statusStr = data["status"] as? String ?: ConnectionStatus.ACTIVE.name
                val status = runCatching { ConnectionStatus.valueOf(statusStr) }.getOrDefault(ConnectionStatus.ACTIVE)

                val placeholderMember = Member(
                    id = otherMemberId,
                    name = "Loading...",
                    headline = "",
                    role = "",
                    company = "",
                    avatarUrl = "",
                    membershipTier = MembershipTier.EXPLORER
                )

                trySend(
                    Connection(
                        id = snapshot.id,
                        member = placeholderMember,
                        lastMessage = data["lastMessage"] as? String ?: "",
                        lastMessageTime = lastMessageTime,
                        unreadCount = unreadCount,
                        labels = labels,
                        privateGoal = privateGoal,
                        status = status
                    )
                )
            }
        awaitClose {
            registration.remove()
        }
    }

    override fun getMessages(connectionId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = firestore.collection("connections").document(connectionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ChatRepository", "Error fetching messages for connection $connectionId: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    val senderId = data["senderId"] as? String ?: ""
                    val timestamp = when (val time = data["timestamp"]) {
                        is Timestamp -> formatMessageTime(time.toDate())
                        else -> "12:00 PM"
                    }
                    val typeStr = data["type"] as? String ?: MessageType.TEXT.name
                    val type = runCatching { MessageType.valueOf(typeStr) }.getOrDefault(MessageType.TEXT)

                    ChatMessage(
                        id = doc.id,
                        senderId = senderId,
                        text = data["text"] as? String ?: "",
                        timestamp = timestamp,
                        isOwn = false, // determined at ViewModel level using current auth uid
                        type = type,
                        isDeleted = data["isDeleted"] as? Boolean ?: false
                    )
                }
                trySend(messages)
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun sendMessage(
        connectionId: String,
        senderId: String,
        text: String,
        type: MessageType
    ): Result<Unit> = runCatching {
        val messageDoc = mapOf(
            "senderId" to senderId,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to type.name,
            "isDeleted" to false
        )
        // 1. Add message document
        firestore.collection("connections").document(connectionId)
            .collection("messages").add(messageDoc).await()
        
        // 2. Update connection doc with last message summary
        val connectionUpdate = mutableMapOf<String, Any>(
            "lastMessage" to if (type == MessageType.AI_SUMMARY) "AI Summary Ready" else text,
            "lastMessageTime" to FieldValue.serverTimestamp()
        )
        
        // 3. Increment unread count for the OTHER member(s)
        val connDoc = firestore.collection("connections").document(connectionId).get().await()
        val members = (connDoc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        for (memberId in members) {
            if (memberId != senderId) {
                connectionUpdate["unreadCountMap.$memberId"] = FieldValue.increment(1)
            }
        }
        
        firestore.collection("connections").document(connectionId).update(connectionUpdate).await()
        Unit
    }

    override suspend fun updateCrmLabels(
        connectionId: String,
        userId: String,
        labels: List<String>
    ): Result<Unit> = runCatching {
        val path = "labels.$userId"
        firestore.collection("connections").document(connectionId)
            .update(path, labels).await()
    }

    override suspend fun updatePrivateGoal(
        connectionId: String,
        userId: String,
        goal: String
    ): Result<Unit> = runCatching {
        val path = "privateGoals.$userId"
        firestore.collection("connections").document(connectionId)
            .update(path, goal).await()
    }

    override suspend fun saveAiSummary(connectionId: String, summaryText: String): Result<Unit> = runCatching {
        sendMessage(connectionId, "system", summaryText, MessageType.AI_SUMMARY).getOrThrow()
    }

    override suspend fun markMessagesAsRead(connectionId: String, userId: String): Result<Unit> = runCatching {
        firestore.collection("connections").document(connectionId)
            .update("unreadCountMap.$userId", 0).await()
    }

    override suspend fun deleteMessage(connectionId: String, messageId: String): Result<Unit> = runCatching {
        // Soft delete — mark as deleted rather than removing
        firestore.collection("connections").document(connectionId)
            .collection("messages").document(messageId)
            .update("isDeleted", true, "text", "This message was deleted").await()
    }

    override suspend fun searchMessages(connectionId: String, query: String): Result<List<ChatMessage>> = runCatching {
        val snapshot = firestore.collection("connections").document(connectionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get().await()

        val queryLower = query.lowercase().trim()
        snapshot.documents
            .map { doc ->
                val data = doc.data ?: emptyMap()
                val senderId = data["senderId"] as? String ?: ""
                val timestamp = when (val time = data["timestamp"]) {
                    is Timestamp -> formatMessageTime(time.toDate())
                    else -> "12:00 PM"
                }
                val typeStr = data["type"] as? String ?: MessageType.TEXT.name
                val type = runCatching { MessageType.valueOf(typeStr) }.getOrDefault(MessageType.TEXT)
                ChatMessage(
                    id = doc.id,
                    senderId = senderId,
                    text = data["text"] as? String ?: "",
                    timestamp = timestamp,
                    isOwn = false,
                    type = type,
                    isDeleted = data["isDeleted"] as? Boolean ?: false
                )
            }
            .filter { !it.isDeleted && it.text.lowercase().contains(queryLower) }
    }

    override fun getUnreadCountTotal(userId: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection("connections")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                val total = snapshot.documents.sumOf { doc ->
                    val data = doc.data ?: emptyMap()
                    val unreadCountMap = data["unreadCountMap"] as? Map<String, Any>
                    (unreadCountMap?.get(userId) as? Number)?.toInt() ?: 0
                }
                trySend(total)
            }
        awaitClose {
            registration.remove()
        }
    }

    private fun formatTime(date: Date): String {
        val diff = System.currentTimeMillis() - date.time
        return when {
            diff < 60_000 -> "now"
            diff < 3600_000 -> "${diff / 60_000}m"
            diff < 86400_000 -> "${diff / 3600_000}h"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
        }
    }

    private fun formatMessageTime(date: Date): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    }
}
