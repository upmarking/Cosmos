package com.cosmos.app.data.repository

import com.cosmos.app.data.model.EventRound
import com.cosmos.app.data.model.EventType
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.NetworkEvent
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface EventRepository {
    suspend fun getEvents(): Result<List<NetworkEvent>>
    suspend fun getEvent(eventId: String, currentUserId: String): Result<NetworkEvent>
    suspend fun registerForEvent(eventId: String, userId: String): Result<Unit>
    suspend fun getEventRounds(eventId: String): Result<List<EventRound>>
    suspend fun submitRoundFeedback(eventId: String, roundId: String, raterId: String, rateeId: String, rating: Int, feedbackText: String): Result<Unit>
}

class FirestoreEventRepository(
    private val firestore: FirebaseFirestore
) : EventRepository {

    override suspend fun getEvents(): Result<List<NetworkEvent>> = runCatching {
        val snapshot = firestore.collection("events").get().await()
        snapshot.documents.map { doc ->
            val data = doc.data ?: emptyMap()
            val typeStr = data["type"] as? String ?: EventType.OPEN_NETWORKING.name
            val type = runCatching { EventType.valueOf(typeStr) }.getOrDefault(EventType.OPEN_NETWORKING)
            
            NetworkEvent(
                id = doc.id,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                date = data["date"] as? String ?: "",
                time = data["time"] as? String ?: "",
                location = data["location"] as? String ?: "",
                type = type,
                participantCount = (data["participantCount"] as? Number)?.toInt() ?: 0,
                maxParticipants = (data["maxParticipants"] as? Number)?.toInt() ?: 0,
                isPaid = data["isPaid"] as? Boolean ?: false,
                price = data["price"] as? String ?: "",
                coverUrl = data["coverUrl"] as? String ?: "",
                tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }

    override suspend fun getEvent(eventId: String, currentUserId: String): Result<NetworkEvent> = runCatching {
        val doc = firestore.collection("events").document(eventId).get().await()
        if (!doc.exists()) throw IllegalArgumentException("Event $eventId not found")
        val data = doc.data ?: emptyMap()
        val typeStr = data["type"] as? String ?: EventType.OPEN_NETWORKING.name
        val type = runCatching { EventType.valueOf(typeStr) }.getOrDefault(EventType.OPEN_NETWORKING)

        // Check if user is registered
        val isRegisteredDoc = firestore.collection("events").document(eventId)
            .collection("registrants").document(currentUserId).get().await()

        NetworkEvent(
            id = doc.id,
            title = data["title"] as? String ?: "",
            description = data["description"] as? String ?: "",
            date = data["date"] as? String ?: "",
            time = data["time"] as? String ?: "",
            location = data["location"] as? String ?: "",
            type = type,
            participantCount = (data["participantCount"] as? Number)?.toInt() ?: 0,
            maxParticipants = (data["maxParticipants"] as? Number)?.toInt() ?: 0,
            isPaid = data["isPaid"] as? Boolean ?: false,
            price = data["price"] as? String ?: "",
            coverUrl = data["coverUrl"] as? String ?: "",
            tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isRegistered = isRegisteredDoc.exists()
        )
    }

    override suspend fun registerForEvent(eventId: String, userId: String): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val eventRef = firestore.collection("events").document(eventId)
            val eventDoc = transaction.get(eventRef)
            val currentCount = eventDoc.getLong("participantCount") ?: 0
            val maxCount = eventDoc.getLong("maxParticipants") ?: 100
            
            if (currentCount >= maxCount) {
                throw IllegalStateException("Event is full")
            }

            // Register user in subcollection
            val registrantRef = eventRef.collection("registrants").document(userId)
            transaction.set(registrantRef, mapOf("registeredAt" to FieldValue.serverTimestamp()))
            
            // Increment participant count
            transaction.update(eventRef, "participantCount", currentCount + 1)
        }.await()
        Unit
    }

    override suspend fun getEventRounds(eventId: String): Result<List<EventRound>> = runCatching {
        val snapshot = firestore.collection("events").document(eventId)
            .collection("rounds").get().await()
        
        snapshot.documents.map { doc ->
            EventRound(
                id = doc.id,
                title = doc.getString("title") ?: "",
                duration = doc.getLong("duration")?.toInt() ?: 15
            )
        }
    }

    override suspend fun submitRoundFeedback(
        eventId: String,
        roundId: String,
        raterId: String,
        rateeId: String,
        rating: Int,
        feedbackText: String
    ): Result<Unit> = runCatching {
        val feedbackMap = mapOf(
            "raterId" to raterId,
            "rateeId" to rateeId,
            "rating" to rating,
            "feedbackText" to feedbackText,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("events").document(eventId)
            .collection("rounds").document(roundId)
            .collection("feedback").add(feedbackMap).await()
        Unit
    }
}
