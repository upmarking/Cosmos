package com.cosmos.app.data.repository

import com.cosmos.app.data.model.IntroRequest
import com.cosmos.app.data.model.IntroStatus
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MembershipTier
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface IntroRepository {
    suspend fun requestWarmIntro(requesterId: String, targetId: String, connectorId: String, message: String): Result<Unit>
    suspend fun getIntroRequest(requestId: String): Result<IntroRequest>
    suspend fun respondToIntroRequest(requestId: String, status: IntroStatus): Result<Unit>
    fun getIntroRequestsForUser(userId: String): Flow<List<IntroRequest>>
}

class FirestoreIntroRepository(
    private val firestore: FirebaseFirestore,
    private val profileRepository: ProfileRepository
) : IntroRepository {

    override suspend fun requestWarmIntro(
        requesterId: String,
        targetId: String,
        connectorId: String,
        message: String
    ): Result<Unit> = runCatching {
        val data = mapOf(
            "requesterId" to requesterId,
            "targetId" to targetId,
            "connectorId" to connectorId,
            "message" to message,
            "status" to IntroStatus.PENDING.name,
            "timestamp" to FieldValue.serverTimestamp()
        )
        val docRef = firestore.collection("intro_requests").add(data).await()
        
        // Notify the connector
        val notificationData = mapOf(
            "userId" to connectorId,
            "type" to "WARM_INTRO_REQUEST",
            "title" to "Introduction Request",
            "body" to "A member is requesting an introduction through you.",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to docRef.id
        )
        firestore.collection("notifications").add(notificationData).await()
        Unit
    }

    override suspend fun getIntroRequest(requestId: String): Result<IntroRequest> = runCatching {
        val doc = firestore.collection("intro_requests").document(requestId).get().await()
        if (!doc.exists()) throw IllegalArgumentException("Intro request $requestId not found")
        val data = doc.data ?: emptyMap()
        
        val requesterId = data["requesterId"] as? String ?: ""
        val targetId = data["targetId"] as? String ?: ""
        val connectorId = data["connectorId"] as? String ?: ""
        val message = data["message"] as? String ?: ""
        val statusStr = data["status"] as? String ?: IntroStatus.PENDING.name
        val status = runCatching { IntroStatus.valueOf(statusStr) }.getOrDefault(IntroStatus.PENDING)

        val requester = profileRepository.getProfile(requesterId).getOrThrow()
        val target = profileRepository.getProfile(targetId).getOrThrow()
        val connector = profileRepository.getProfile(connectorId).getOrThrow()

        IntroRequest(
            id = doc.id,
            requester = requester,
            target = target,
            connector = connector,
            message = message,
            status = status
        )
    }

    override suspend fun respondToIntroRequest(requestId: String, status: IntroStatus): Result<Unit> = runCatching {
        firestore.collection("intro_requests").document(requestId)
            .update("status", status.name).await()
        
        // Load details to notify requester
        val doc = firestore.collection("intro_requests").document(requestId).get().await()
        val requesterId = doc.getString("requesterId") ?: ""
        val targetId = doc.getString("targetId") ?: ""
        val connectorId = doc.getString("connectorId") ?: ""

        val typeStr = if (status == IntroStatus.ACCEPTED) "WARM_INTRO_ACCEPTED" else "WARM_INTRO_DECLINED"
        val title = if (status == IntroStatus.ACCEPTED) "Intro Request Accepted!" else "Intro Request Declined"
        val body = if (status == IntroStatus.ACCEPTED) {
            "Your introduction request has been accepted. Say hello!"
        } else {
            "Your introduction request was declined by the connector."
        }

        // Send notification to requester
        val notificationData = mapOf(
            "userId" to requesterId,
            "type" to typeStr,
            "title" to title,
            "body" to body,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to targetId
        )
        firestore.collection("notifications").add(notificationData).await()

        if (status == IntroStatus.ACCEPTED) {
            // Also automatically instantiate a 3-way connection or messaging thread
            val connectionId = "intro_${requestId}"
            val connectionData = mapOf(
                "id" to connectionId,
                "members" to listOf(requesterId, targetId, connectorId),
                "lastMessage" to "3-Way Intro started! Say hello.",
                "lastMessageTime" to FieldValue.serverTimestamp(),
                "unreadCountMap" to mapOf(requesterId to 0, targetId to 0, connectorId to 0),
                "labels" to mapOf(requesterId to emptyList<String>(), targetId to emptyList<String>(), connectorId to emptyList<String>()),
                "privateGoals" to mapOf(requesterId to "", targetId to "", connectorId to ""),
                "status" to "INTRO_REQUESTED",
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("connections").document(connectionId).set(connectionData).await()

            // Increment connectionsCount for requester, target, and connector
            firestore.collection("users").document(requesterId).update("connectionsCount", FieldValue.increment(1)).await()
            firestore.collection("users").document(targetId).update("connectionsCount", FieldValue.increment(1)).await()
            firestore.collection("users").document(connectorId).update("connectionsCount", FieldValue.increment(1)).await()
        }
        Unit
    }

    override fun getIntroRequestsForUser(userId: String): Flow<List<IntroRequest>> = callbackFlow {
        val registration = firestore.collection("intro_requests")
            .whereEqualTo("connectorId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                // Fast map for Flow stream (details resolved in ViewModel)
                val list = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap()
                    val statusStr = data["status"] as? String ?: IntroStatus.PENDING.name
                    val status = runCatching { IntroStatus.valueOf(statusStr) }.getOrDefault(IntroStatus.PENDING)
                    
                    val requesterPlaceholder = Member(
                        id = data["requesterId"] as? String ?: "",
                        name = "Member",
                        headline = "",
                        role = "",
                        company = "",
                        avatarUrl = "",
                        membershipTier = MembershipTier.EXPLORER
                    )
                    
                    IntroRequest(
                        id = doc.id,
                        requester = requesterPlaceholder,
                        target = requesterPlaceholder,
                        connector = requesterPlaceholder,
                        message = data["message"] as? String ?: "",
                        status = status
                    )
                }
                trySend(list)
            }
        awaitClose {
            registration.remove()
        }
    }
}
