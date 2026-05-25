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
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                // Map connections. Since mapping requires fetching other member details,
                // we can load member profile details. For immediate UI flow, we spawn a coroutine.
                // To keep it clean and robust, we fetch from local profiles or placeholder profiles
                // if they are not cached, and fetch actual profiles asynchronously.
                val connections = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    val members = (data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val otherMemberId = members.firstOrNull { it != userId } ?: ""
                    
                    // Simple placeholder Member - details loaded in UI/ViewModel
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
                    close(error)
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

                // Retrieve other member details to supply real Member name/headline
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
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val currentUserId = firestore.app.options.databaseUrl ?: "" // mock check
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
                        type = type
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
            "type" to type.name
        )
        // 1. Add message document
        firestore.collection("connections").document(connectionId)
            .collection("messages").add(messageDoc).await()
        
        // 2. Update connection doc with last message summary
        val connectionUpdate = mapOf(
            "lastMessage" to if (type == MessageType.AI_SUMMARY) "AI Summary Ready" else text,
            "lastMessageTime" to FieldValue.serverTimestamp()
        )
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
        // Send AI summary as a system message
        sendMessage(connectionId, "system", summaryText, MessageType.AI_SUMMARY).getOrThrow()
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
