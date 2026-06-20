package app.cosmos.com.data.repository

import app.cosmos.com.data.model.EventRound
import app.cosmos.com.data.model.EventType
import app.cosmos.com.data.model.Member
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.model.NetworkEvent
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

interface EventRepository {
    fun getEvents(): Flow<List<NetworkEvent>>
    fun getEvent(eventId: String, currentUserId: String): Flow<NetworkEvent?>
    suspend fun registerForEvent(eventId: String, userId: String): Result<Unit>
    suspend fun unregisterFromEvent(eventId: String, userId: String): Result<Unit>
    fun getEventRounds(eventId: String): Flow<List<EventRound>>
    suspend fun submitRoundFeedback(eventId: String, roundId: String, raterId: String, rateeId: String, rating: Int, feedbackText: String): Result<Unit>
    fun getEventParticipants(eventId: String): Flow<List<Member>>
    suspend fun createEvent(event: NetworkEvent, creatorId: String): Result<String>
    suspend fun updateEvent(eventId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteEvent(eventId: String): Result<Unit>
    suspend fun searchEvents(query: String, type: EventType? = null, tags: List<String> = emptyList()): Result<List<NetworkEvent>>
}

class FirestoreEventRepository(
    private val firestore: FirebaseFirestore
) : EventRepository {

    override fun getEvents(): Flow<List<NetworkEvent>> = callbackFlow {
        val registration = firestore.collection("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CosmosEvents", "Error fetching events", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val events = snapshot.documents.map { doc ->
                    mapDocumentToEvent(doc.id, doc.data ?: emptyMap())
                }
                trySend(events)
            }
        awaitClose {
            registration.remove()
        }
    }

    override fun getEvent(eventId: String, currentUserId: String): Flow<NetworkEvent?> = callbackFlow {
        val eventRef = firestore.collection("events").document(eventId)
        val registrantRef = eventRef.collection("registrants").document(currentUserId)
        
        var eventDoc: com.google.firebase.firestore.DocumentSnapshot? = null
        var isRegistered = false
        
        fun sendUpdate() {
            val doc = eventDoc ?: return
            val data = doc.data ?: emptyMap()
            val event = mapDocumentToEvent(doc.id, data)
            trySend(event.copy(isRegistered = isRegistered))
        }
        
        val eventReg = eventRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("EventRepository", "Error fetching event details: ${error.message}", error)
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                eventDoc = snapshot
                sendUpdate()
            } else {
                trySend(null)
            }
        }
        
        val registrantReg = registrantRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("EventRepository", "Error fetching registrant details: ${error.message}", error)
                // Do not close flow; just continue without crashing
                return@addSnapshotListener
            }
            isRegistered = snapshot?.exists() == true
            sendUpdate()
        }
        
        awaitClose {
            eventReg.remove()
            registrantReg.remove()
        }
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

            val registrantRef = eventRef.collection("registrants").document(userId)
            transaction.set(registrantRef, mapOf("registeredAt" to FieldValue.serverTimestamp()))
            transaction.update(eventRef, "participantCount", currentCount + 1)
        }.await()

        // Send notification
        val eventDoc = firestore.collection("events").document(eventId).get().await()
        val eventTitle = eventDoc.getString("title") ?: "Event"
        val notifData = mapOf(
            "userId" to userId,
            "type" to "EVENT_REMINDER",
            "title" to "Registered for $eventTitle",
            "body" to "You're all set! We'll remind you when the event starts.",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to eventId
        )
        firestore.collection("notifications").add(notifData).await()
        Unit
    }

    override suspend fun unregisterFromEvent(eventId: String, userId: String): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val eventRef = firestore.collection("events").document(eventId)
            val eventDoc = transaction.get(eventRef)
            val currentCount = eventDoc.getLong("participantCount") ?: 0

            val registrantRef = eventRef.collection("registrants").document(userId)
            transaction.delete(registrantRef)
            if (currentCount > 0) {
                transaction.update(eventRef, "participantCount", currentCount - 1)
            }
        }.await()
        Unit
    }

    override fun getEventRounds(eventId: String): Flow<List<EventRound>> = callbackFlow {
        val registration = firestore.collection("events").document(eventId)
            .collection("rounds")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("EventRepository", "Error fetching event rounds: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                launch {
                    val deferredRounds = snapshot.documents.map { doc ->
                        async {
                            val data = doc.data ?: emptyMap()
                            val participantIds = (data["participantIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            
                            val deferredParticipants = participantIds.map { uid ->
                                async {
                                    try {
                                        val userDoc = firestore.collection("users").document(uid).get().await()
                                        if (userDoc.exists()) {
                                            FirebaseAuthRepository.mapDocumentToMember(userDoc.id, userDoc.data ?: emptyMap())
                                        } else null
                                    } catch (e: Exception) { null }
                                }
                            }
                            val participants = deferredParticipants.awaitAll().filterNotNull()
                            
                            EventRound(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                duration = doc.getLong("duration")?.toInt() ?: 15,
                                participants = participants
                            )
                        }
                    }
                    val rounds = deferredRounds.awaitAll()
                    trySend(rounds)
                }
            }
        awaitClose {
            registration.remove()
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

    override fun getEventParticipants(eventId: String): Flow<List<Member>> = callbackFlow {
        val registration = firestore.collection("events").document(eventId)
            .collection("registrants")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("EventRepository", "Error fetching event participants: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                launch {
                    val participantIds = snapshot.documents.map { it.id }
                    val deferredMembers = participantIds.map { uid ->
                        async {
                            try {
                                val doc = firestore.collection("users").document(uid).get().await()
                                if (doc.exists()) {
                                    FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
                                } else null
                            } catch (e: Exception) { null }
                        }
                    }
                    val members = deferredMembers.awaitAll().filterNotNull()
                    trySend(members)
                }
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun createEvent(event: NetworkEvent, creatorId: String): Result<String> = runCatching {
        val eventMap = mapOf(
            "title" to event.title,
            "description" to event.description,
            "date" to event.date,
            "time" to event.time,
            "location" to event.location,
            "type" to event.type.name,
            "participantCount" to 0,
            "maxParticipants" to event.maxParticipants,
            "isPaid" to event.isPaid,
            "price" to event.price,
            "coverUrl" to event.coverUrl,
            "tags" to event.tags,
            "createdBy" to creatorId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val docRef = firestore.collection("events").add(eventMap).await()
        docRef.id
    }

    override suspend fun updateEvent(eventId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("events").document(eventId).update(updates).await()
    }

    override suspend fun deleteEvent(eventId: String): Result<Unit> = runCatching {
        // Delete sub-collections first (registrants, rounds)
        val registrants = firestore.collection("events").document(eventId)
            .collection("registrants").get().await()
        registrants.documents.forEach { it.reference.delete().await() }

        val rounds = firestore.collection("events").document(eventId)
            .collection("rounds").get().await()
        for (roundDoc in rounds.documents) {
            val feedback = roundDoc.reference.collection("feedback").get().await()
            feedback.documents.forEach { it.reference.delete().await() }
            roundDoc.reference.delete().await()
        }

        firestore.collection("events").document(eventId).delete().await()
    }

    override suspend fun searchEvents(
        query: String,
        type: EventType?,
        tags: List<String>
    ): Result<List<NetworkEvent>> = runCatching {
        val snapshot = firestore.collection("events").get().await()
        val queryLower = query.lowercase().trim()

        snapshot.documents
            .map { doc -> mapDocumentToEvent(doc.id, doc.data ?: emptyMap()) }
            .filter { event ->
                val matchesQuery = queryLower.isEmpty() ||
                    event.title.lowercase().contains(queryLower) ||
                    event.description.lowercase().contains(queryLower) ||
                    event.tags.any { it.lowercase().contains(queryLower) }

                val matchesType = type == null || event.type == type

                val matchesTags = tags.isEmpty() ||
                    event.tags.any { eventTag -> tags.any { filterTag -> eventTag.equals(filterTag, ignoreCase = true) } }

                matchesQuery && matchesType && matchesTags
            }
    }

    companion object {
        fun mapDocumentToEvent(id: String, data: Map<String, Any>): NetworkEvent {
            val typeStr = data["type"] as? String ?: EventType.OPEN_NETWORKING.name
            val type = runCatching { EventType.valueOf(typeStr) }.getOrDefault(EventType.OPEN_NETWORKING)

            return NetworkEvent(
                id = id,
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
                createdBy = data["createdBy"] as? String ?: "",
                createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L
            )
        }
    }
}
