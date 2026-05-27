package com.cosmos.app.data.repository

import com.cosmos.app.data.model.EventRound
import com.cosmos.app.data.model.EventType
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MembershipTier
import com.cosmos.app.data.model.NetworkEvent
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

interface EventRepository {
    fun getEvents(): Flow<List<NetworkEvent>>
    fun getEvent(eventId: String, currentUserId: String): Flow<NetworkEvent?>
    suspend fun registerForEvent(eventId: String, userId: String): Result<Unit>
    fun getEventRounds(eventId: String): Flow<List<EventRound>>
    suspend fun submitRoundFeedback(eventId: String, roundId: String, raterId: String, rateeId: String, rating: Int, feedbackText: String): Result<Unit>
    fun getEventParticipants(eventId: String): Flow<List<Member>>
}

class FirestoreEventRepository(
    private val firestore: FirebaseFirestore
) : EventRepository {

    private suspend fun seedFirestoreUsers() {
        val u1 = mapOf(
            "id" to "mock_user_sarah",
            "name" to "Sarah Jenkins",
            "headline" to "Founder & CEO at BioSphere",
            "role" to "CEO",
            "company" to "BioSphere",
            "avatarUrl" to "",
            "location" to "Boston, MA",
            "bio" to "Building the future of sustainable food systems.",
            "tags" to listOf("ClimateTech", "Biotech", "Female Founder"),
            "primaryUserType" to "Founder",
            "membershipTier" to MembershipTier.FOUNDER.name,
            "isLinkedInConnected" to true,
            "mutualConnectionsCount" to 3,
            "connectionsCount" to 14,
            "eventsAttended" to 5,
            "followUpsCompleted" to 8
        )
        val u2 = mapOf(
            "id" to "mock_user_david",
            "name" to "David Chen",
            "headline" to "General Partner at Nexus Ventures",
            "role" to "GP",
            "company" to "Nexus Ventures",
            "avatarUrl" to "",
            "location" to "San Francisco, CA",
            "bio" to "Investing in early stage AI and enterprise SaaS. Former developer.",
            "tags" to listOf("AI/ML", "B2B SaaS", "VC"),
            "primaryUserType" to "Investor",
            "membershipTier" to MembershipTier.INNER_CIRCLE.name,
            "isLinkedInConnected" to true,
            "mutualConnectionsCount" to 5,
            "connectionsCount" to 42,
            "eventsAttended" to 12,
            "followUpsCompleted" to 20
        )
        val u3 = mapOf(
            "id" to "mock_user_elena",
            "name" to "Elena Rostova",
            "headline" to "Lead Designer at Cosmos Studio",
            "role" to "Designer",
            "company" to "Cosmos Studio",
            "avatarUrl" to "",
            "location" to "New York, NY",
            "bio" to "Passionate about premium design systems and minimalist interfaces.",
            "tags" to listOf("UI/UX", "Product Design", "Creative"),
            "primaryUserType" to "Creator",
            "membershipTier" to MembershipTier.MEMBER.name,
            "isLinkedInConnected" to false,
            "mutualConnectionsCount" to 1,
            "connectionsCount" to 8,
            "eventsAttended" to 3,
            "followUpsCompleted" to 4
        )
        val u4 = mapOf(
            "id" to "mock_user_marcus",
            "name" to "Marcus Vance",
            "headline" to "VP of Product at ScaleUp",
            "role" to "Product VP",
            "company" to "ScaleUp",
            "avatarUrl" to "",
            "location" to "Austin, TX",
            "bio" to "Scale specialist. Former 0-to-1 PM at Stripe and Airbnb.",
            "tags" to listOf("Product Management", "Growth", "Scaling"),
            "primaryUserType" to "Startup Operator",
            "membershipTier" to MembershipTier.EXPLORER.name,
            "isLinkedInConnected" to false,
            "mutualConnectionsCount" to 2,
            "connectionsCount" to 19,
            "eventsAttended" to 8,
            "followUpsCompleted" to 15
        )
        
        firestore.collection("users").document("mock_user_sarah").set(u1).await()
        firestore.collection("users").document("mock_user_david").set(u2).await()
        firestore.collection("users").document("mock_user_elena").set(u3).await()
        firestore.collection("users").document("mock_user_marcus").set(u4).await()
    }

    private suspend fun seedFirestoreEvents() {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val date1 = sdf.format(calendar.time)
        
        calendar.add(Calendar.DAY_OF_YEAR, 3)
        val date2 = sdf.format(calendar.time)
        
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        val date3 = sdf.format(calendar.time)

        val e1 = mapOf(
            "title" to "Founder Speed Matchmaking",
            "description" to "Intentional 1-on-1 networking matches for founders and operators seeking co-founders.",
            "date" to date1,
            "time" to "6:00 PM UTC",
            "location" to "Cosmos Virtual Room",
            "type" to EventType.FOUNDER_MEETUP.name,
            "participantCount" to 18,
            "maxParticipants" to 30,
            "isPaid" to false,
            "price" to "",
            "coverUrl" to "",
            "tags" to listOf("Speed Dating", "Co-Founder", "Interactive")
        )
        val e2 = mapOf(
            "title" to "AI & B2B SaaS Showcase",
            "description" to "Presenting three breakout startups building AI-first enterprise workflows.",
            "date" to date2,
            "time" to "4:00 PM UTC",
            "location" to "Cosmos Hall",
            "type" to EventType.INDUSTRY_SPECIFIC.name,
            "participantCount" to 45,
            "maxParticipants" to 100,
            "isPaid" to false,
            "price" to "",
            "coverUrl" to "",
            "tags" to listOf("AI", "B2B SaaS", "Showcase")
        )
        val e3 = mapOf(
            "title" to "Cosmos Open Networking Hour",
            "description" to "Meet and chat with other members of the club in a relaxed, open format.",
            "date" to date3,
            "time" to "7:00 PM UTC",
            "location" to "Cosmos Lounge",
            "type" to EventType.OPEN_NETWORKING.name,
            "participantCount" to 22,
            "maxParticipants" to 50,
            "isPaid" to false,
            "price" to "",
            "coverUrl" to "",
            "tags" to listOf("Networking", "Open", "Social")
        )
        
        val ref1 = firestore.collection("events").document("event_1")
        val ref2 = firestore.collection("events").document("event_2")
        val ref3 = firestore.collection("events").document("event_3")
        
        firestore.runBatch { batch ->
            batch.set(ref1, e1)
            batch.set(ref2, e2)
            batch.set(ref3, e3)
        }.await()

        val usersSnapshot = firestore.collection("users").get().await()
        val userIds = usersSnapshot.documents.map { it.id }.toMutableList()
        
        if (userIds.size < 4) {
            seedFirestoreUsers()
            val freshUsersSnapshot = firestore.collection("users").get().await()
            userIds.clear()
            userIds.addAll(freshUsersSnapshot.documents.map { it.id })
        }

        val r1 = mapOf(
            "title" to "Round 1: Intros",
            "duration" to 15,
            "participantIds" to userIds.take(2)
        )
        val r2 = mapOf(
            "title" to "Round 2: Collaborative Ideas",
            "duration" to 15,
            "participantIds" to userIds.drop(2).take(2)
        )
        
        val roundRef1 = ref1.collection("rounds").document("r1")
        val roundRef2 = ref1.collection("rounds").document("r2")
        
        firestore.runBatch { batch ->
            batch.set(roundRef1, r1)
            batch.set(roundRef2, r2)
        }.await()
    }

    override fun getEvents(): Flow<List<NetworkEvent>> = callbackFlow {
        launch {
            try {
                val snapshot = firestore.collection("events").get().await()
                if (snapshot.isEmpty) {
                    seedFirestoreEvents()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val registration = firestore.collection("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val events = snapshot.documents.map { doc ->
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
            val typeStr = data["type"] as? String ?: EventType.OPEN_NETWORKING.name
            val type = runCatching { EventType.valueOf(typeStr) }.getOrDefault(EventType.OPEN_NETWORKING)
            
            trySend(
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
                    isRegistered = isRegistered
                )
            )
        }
        
        val eventReg = eventRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
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
                close(error)
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
        Unit
    }

    override fun getEventRounds(eventId: String): Flow<List<EventRound>> = callbackFlow {
        val registration = firestore.collection("events").document(eventId)
            .collection("rounds")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                launch {
                    val rounds = snapshot.documents.map { doc ->
                        val data = doc.data ?: emptyMap()
                        val participantIds = (data["participantIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        
                        val participants = participantIds.mapNotNull { uid ->
                            val userDoc = firestore.collection("users").document(uid).get().await()
                            if (userDoc.exists()) {
                                FirebaseAuthRepository.mapDocumentToMember(userDoc.id, userDoc.data ?: emptyMap())
                            } else {
                                null
                            }
                        }
                        
                        EventRound(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            duration = doc.getLong("duration")?.toInt() ?: 15,
                            participants = participants
                        )
                    }
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
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                launch {
                    val participantIds = snapshot.documents.map { it.id }
                    val members = participantIds.mapNotNull { uid ->
                        val doc = firestore.collection("users").document(uid).get().await()
                        if (doc.exists()) {
                            FirebaseAuthRepository.mapDocumentToMember(doc.id, doc.data ?: emptyMap())
                        } else {
                            null
                        }
                    }
                    trySend(members)
                }
            }
        awaitClose {
            registration.remove()
        }
    }
}
