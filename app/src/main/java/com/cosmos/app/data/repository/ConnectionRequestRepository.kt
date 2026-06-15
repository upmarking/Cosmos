package com.cosmos.app.data.repository

import com.cosmos.app.data.model.ConnectionProfileStatus
import com.cosmos.app.data.model.ConnectionRequest
import com.cosmos.app.data.model.ConnectionRequestStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface ConnectionRequestRepository {
    suspend fun sendConnectionRequest(
        senderId: String,
        receiverId: String,
        senderName: String,
        senderHeadline: String,
        senderAvatarUrl: String,
        receiverName: String,
        receiverHeadline: String,
        receiverAvatarUrl: String,
        message: String
    ): Result<Unit>

    suspend fun acceptConnectionRequest(requestId: String): Result<Unit>
    suspend fun declineConnectionRequest(requestId: String): Result<Unit>
    suspend fun withdrawConnectionRequest(requestId: String): Result<Unit>

    fun getIncomingRequests(userId: String): Flow<List<ConnectionRequest>>
    fun getOutgoingRequests(userId: String): Flow<List<ConnectionRequest>>

    suspend fun getConnectionStatus(currentUserId: String, otherUserId: String): Result<ConnectionProfileStatus>
    fun getConnectionStatusFlow(currentUserId: String, otherUserId: String): Flow<ConnectionProfileStatus>

    fun getIncomingRequestCount(userId: String): Flow<Int>
}

class FirestoreConnectionRequestRepository(
    private val firestore: FirebaseFirestore
) : ConnectionRequestRepository {

    override suspend fun sendConnectionRequest(
        senderId: String,
        receiverId: String,
        senderName: String,
        senderHeadline: String,
        senderAvatarUrl: String,
        receiverName: String,
        receiverHeadline: String,
        receiverAvatarUrl: String,
        message: String
    ): Result<Unit> = runCatching {
        val requestId = "req_${senderId}_${receiverId}"

        // Check if there's already a pending request in either direction
        val existingForward = firestore.collection("connection_requests")
            .document(requestId).get().await()
        if (existingForward.exists() && existingForward.getString("status") == "PENDING") {
            throw IllegalStateException("Connection request already pending")
        }

        val existingReverse = firestore.collection("connection_requests")
            .document("req_${receiverId}_${senderId}").get().await()
        if (existingReverse.exists() && existingReverse.getString("status") == "PENDING") {
            // The other user already sent us a request — auto-accept it
            acceptConnectionRequest("req_${receiverId}_${senderId}").getOrThrow()
            return@runCatching
        }

        // Check if already connected
        val connectionId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
        val existingConnection = firestore.collection("connections").document(connectionId).get().await()
        if (existingConnection.exists()) {
            throw IllegalStateException("Already connected")
        }

        // Create the request
        val requestData = mapOf(
            "senderId" to senderId,
            "receiverId" to receiverId,
            "senderName" to senderName,
            "senderHeadline" to senderHeadline,
            "senderAvatarUrl" to senderAvatarUrl,
            "receiverName" to receiverName,
            "receiverHeadline" to receiverHeadline,
            "receiverAvatarUrl" to receiverAvatarUrl,
            "message" to message,
            "status" to ConnectionRequestStatus.PENDING.name,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("connection_requests").document(requestId).set(requestData).await()

        // Create notification for receiver
        val notifData = mapOf(
            "userId" to receiverId,
            "type" to "CONNECTION_REQUEST",
            "title" to "New Connection Request",
            "body" to "$senderName wants to connect with you${if (message.isNotBlank()) ": \"$message\"" else ""}",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to senderId
        )
        firestore.collection("notifications").add(notifData).await()
    }

    override suspend fun acceptConnectionRequest(requestId: String): Result<Unit> = runCatching {
        val requestDoc = firestore.collection("connection_requests").document(requestId).get().await()
        if (!requestDoc.exists()) throw IllegalArgumentException("Request not found")

        val data = requestDoc.data ?: throw IllegalArgumentException("Request data is empty")
        val senderId = data["senderId"] as? String ?: throw IllegalArgumentException("Missing senderId")
        val receiverId = data["receiverId"] as? String ?: throw IllegalArgumentException("Missing receiverId")
        val senderName = data["senderName"] as? String ?: ""

        // 1. Update request status
        firestore.collection("connection_requests").document(requestId)
            .update("status", ConnectionRequestStatus.ACCEPTED.name).await()

        // 2. Create connection document (same structure as existing SwipeRepository)
        val connectionId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
        val connectionData = mapOf(
            "id" to connectionId,
            "members" to listOf(senderId, receiverId),
            "lastMessage" to "Connection established! Say hello.",
            "lastMessageTime" to FieldValue.serverTimestamp(),
            "unreadCountMap" to mapOf(senderId to 0, receiverId to 0),
            "labels" to mapOf(senderId to emptyList<String>(), receiverId to emptyList<String>()),
            "privateGoals" to mapOf(senderId to "", receiverId to ""),
            "status" to "ACTIVE",
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("connections").document(connectionId).set(connectionData).await()

        // 3. Notification to sender that their request was accepted
        val notifData = mapOf(
            "userId" to senderId,
            "type" to "CONNECTION_ACCEPTED",
            "title" to "Connection Accepted! 🎉",
            "body" to "Your connection request was accepted. Start a conversation now!",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to receiverId
        )
        firestore.collection("notifications").add(notifData).await()

        // 4. Notification to receiver that connection is established
        val notifData2 = mapOf(
            "userId" to receiverId,
            "type" to "CONNECTION_ACCEPTED",
            "title" to "Connection Established! 🎉",
            "body" to "You are now connected with $senderName. Start a conversation!",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to senderId
        )
        firestore.collection("notifications").add(notifData2).await()
    }

    override suspend fun declineConnectionRequest(requestId: String): Result<Unit> = runCatching {
        firestore.collection("connection_requests").document(requestId)
            .update("status", ConnectionRequestStatus.DECLINED.name).await()
    }

    override suspend fun withdrawConnectionRequest(requestId: String): Result<Unit> = runCatching {
        firestore.collection("connection_requests").document(requestId)
            .update("status", ConnectionRequestStatus.WITHDRAWN.name).await()
    }

    override fun getIncomingRequests(userId: String): Flow<List<ConnectionRequest>> = callbackFlow {
        val registration = firestore.collection("connection_requests")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", ConnectionRequestStatus.PENDING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.map { doc -> mapDocToRequest(doc.id, doc.data ?: emptyMap()) } ?: emptyList()
                trySend(requests)
            }
        awaitClose { registration.remove() }
    }

    override fun getOutgoingRequests(userId: String): Flow<List<ConnectionRequest>> = callbackFlow {
        val registration = firestore.collection("connection_requests")
            .whereEqualTo("senderId", userId)
            .whereEqualTo("status", ConnectionRequestStatus.PENDING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.map { doc -> mapDocToRequest(doc.id, doc.data ?: emptyMap()) } ?: emptyList()
                trySend(requests)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getConnectionStatus(
        currentUserId: String,
        otherUserId: String
    ): Result<ConnectionProfileStatus> = runCatching {
        // 1. Check if connected
        val connectionId = if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        val connDoc = firestore.collection("connections").document(connectionId).get().await()
        if (connDoc.exists()) return@runCatching ConnectionProfileStatus.CONNECTED

        // 2. Check if current user sent a pending request
        val sentId = "req_${currentUserId}_${otherUserId}"
        val sentDoc = firestore.collection("connection_requests").document(sentId).get().await()
        if (sentDoc.exists() && sentDoc.getString("status") == ConnectionRequestStatus.PENDING.name) {
            return@runCatching ConnectionProfileStatus.PENDING_SENT
        }

        // 3. Check if current user received a pending request
        val receivedId = "req_${otherUserId}_${currentUserId}"
        val receivedDoc = firestore.collection("connection_requests").document(receivedId).get().await()
        if (receivedDoc.exists() && receivedDoc.getString("status") == ConnectionRequestStatus.PENDING.name) {
            return@runCatching ConnectionProfileStatus.PENDING_RECEIVED
        }

        ConnectionProfileStatus.NONE
    }

    override fun getConnectionStatusFlow(
        currentUserId: String,
        otherUserId: String
    ): Flow<ConnectionProfileStatus> = callbackFlow {
        // Listen to connection doc
        val connectionId = if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"

        var lastStatus = ConnectionProfileStatus.NONE

        val connRegistration = firestore.collection("connections").document(connectionId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    lastStatus = ConnectionProfileStatus.CONNECTED
                    trySend(lastStatus)
                }
            }

        // Listen to sent request
        val sentId = "req_${currentUserId}_${otherUserId}"
        val sentRegistration = firestore.collection("connection_requests").document(sentId)
            .addSnapshotListener { snapshot, _ ->
                if (lastStatus == ConnectionProfileStatus.CONNECTED) return@addSnapshotListener
                if (snapshot != null && snapshot.exists() && snapshot.getString("status") == ConnectionRequestStatus.PENDING.name) {
                    lastStatus = ConnectionProfileStatus.PENDING_SENT
                    trySend(lastStatus)
                } else if (lastStatus == ConnectionProfileStatus.PENDING_SENT) {
                    // Request was withdrawn or status changed
                    lastStatus = ConnectionProfileStatus.NONE
                    trySend(lastStatus)
                }
            }

        // Listen to received request
        val receivedId = "req_${otherUserId}_${currentUserId}"
        val receivedRegistration = firestore.collection("connection_requests").document(receivedId)
            .addSnapshotListener { snapshot, _ ->
                if (lastStatus == ConnectionProfileStatus.CONNECTED) return@addSnapshotListener
                if (snapshot != null && snapshot.exists() && snapshot.getString("status") == ConnectionRequestStatus.PENDING.name) {
                    lastStatus = ConnectionProfileStatus.PENDING_RECEIVED
                    trySend(lastStatus)
                } else if (lastStatus == ConnectionProfileStatus.PENDING_RECEIVED) {
                    lastStatus = ConnectionProfileStatus.NONE
                    trySend(lastStatus)
                }
            }

        // Initial check
        val status = getConnectionStatus(currentUserId, otherUserId).getOrDefault(ConnectionProfileStatus.NONE)
        lastStatus = status
        trySend(status)

        awaitClose {
            connRegistration.remove()
            sentRegistration.remove()
            receivedRegistration.remove()
        }
    }

    override fun getIncomingRequestCount(userId: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection("connection_requests")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", ConnectionRequestStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }

    private fun mapDocToRequest(id: String, data: Map<String, Any>): ConnectionRequest {
        val statusStr = data["status"] as? String ?: ConnectionRequestStatus.PENDING.name
        val status = runCatching { ConnectionRequestStatus.valueOf(statusStr) }
            .getOrDefault(ConnectionRequestStatus.PENDING)
        val createdAt = when (val ts = data["createdAt"]) {
            is Timestamp -> ts.toDate().time
            else -> 0L
        }
        return ConnectionRequest(
            id = id,
            senderId = data["senderId"] as? String ?: "",
            receiverId = data["receiverId"] as? String ?: "",
            senderName = data["senderName"] as? String ?: "",
            senderHeadline = data["senderHeadline"] as? String ?: "",
            senderAvatarUrl = data["senderAvatarUrl"] as? String ?: "",
            receiverName = data["receiverName"] as? String ?: "",
            receiverHeadline = data["receiverHeadline"] as? String ?: "",
            receiverAvatarUrl = data["receiverAvatarUrl"] as? String ?: "",
            message = data["message"] as? String ?: "",
            status = status,
            createdAt = createdAt
        )
    }
}
