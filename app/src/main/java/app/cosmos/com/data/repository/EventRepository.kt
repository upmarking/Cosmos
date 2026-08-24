package app.cosmos.com.data.repository

import android.util.Log
import app.cosmos.com.data.model.EventPaymentRecord
import app.cosmos.com.data.model.EventPaymentStatus
import app.cosmos.com.data.model.EventRegistrant
import app.cosmos.com.data.model.EventRound
import app.cosmos.com.data.model.EventTicketOrder
import app.cosmos.com.data.model.EventType
import app.cosmos.com.data.model.Member
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.model.NetworkEvent
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

interface EventRepository {
    fun getEvents(currentUserId: String? = null): Flow<List<NetworkEvent>>
    fun getEvent(eventId: String, currentUserId: String): Flow<NetworkEvent?>
    suspend fun registerForEvent(eventId: String, userId: String, name: String, email: String): Result<Unit>
    suspend fun createEventTicketOrder(eventId: String, userId: String, userName: String, userEmail: String, userContact: String = ""): Result<EventTicketOrder>
    suspend fun verifyEventTicketPayment(eventId: String, userId: String, orderId: String, paymentId: String, signature: String, name: String, email: String): Result<EventPaymentRecord>
    suspend fun registerForPaidEvent(eventId: String, userId: String, name: String, email: String, transactionId: String, amount: Double, currency: String, paymentMethod: String): Result<EventPaymentRecord>
    suspend fun unregisterFromEvent(eventId: String, userId: String): Result<Unit>
    fun getEventRounds(eventId: String): Flow<List<EventRound>>
    suspend fun submitRoundFeedback(eventId: String, roundId: String, raterId: String, rateeId: String, rating: Int, feedbackText: String): Result<Unit>
    fun getEventParticipants(eventId: String): Flow<List<Member>>
    fun getEventRegistrants(eventId: String): Flow<List<EventRegistrant>>
    suspend fun createEvent(event: NetworkEvent, creatorId: String): Result<String>
    suspend fun createVirtualEvent(event: NetworkEvent, creatorId: String, creatorEmail: String): Result<Pair<String, String>>
    suspend fun updateEvent(eventId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun deleteEvent(eventId: String): Result<Unit>
    suspend fun searchEvents(query: String, type: EventType? = null, tags: List<String> = emptyList()): Result<List<NetworkEvent>>
}

class FirestoreEventRepository(
    private val firestore: FirebaseFirestore
) : EventRepository {

    private fun isEventExpired(dateStr: String): Boolean {
        if (dateStr.isBlank()) return false
        
        val cleanDate = dateStr
            .replace("Tomorrow, ", "")
            .replace("Today, ", "")
            .replace("Next ", "")
            .replace("Monday, ", "")
            .replace("Tuesday, ", "")
            .replace("Wednesday, ", "")
            .replace("Thursday, ", "")
            .replace("Friday, ", "")
            .replace("Saturday, ", "")
            .replace("Sunday, ", "")
            .trim()
            
        val formats = listOf(
            SimpleDateFormat("MMM d, yyyy", Locale.US),
            SimpleDateFormat("MMMM d, yyyy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        
        var eventDate: java.util.Date? = null
        for (format in formats) {
            try {
                eventDate = format.parse(cleanDate)
                if (eventDate != null) break
            } catch (e: Exception) {
                // ignore
            }
        }
        
        if (eventDate == null) return false
        
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val eventCal = Calendar.getInstance().apply {
            time = eventDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        return eventCal.before(today)
    }

    override fun getEvents(currentUserId: String?): Flow<List<NetworkEvent>> = callbackFlow {
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
                launch {
                    val deferredEvents = snapshot.documents.map { doc ->
                        async {
                            val event = mapDocumentToEvent(doc.id, doc.data ?: emptyMap())
                            var isRegistered = false
                            if (currentUserId != null) {
                                try {
                                    val registrantDoc = firestore.collection("events").document(doc.id)
                                        .collection("registrants").document(currentUserId).get().await()
                                    isRegistered = registrantDoc.exists()
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                            event.copy(isRegistered = isRegistered)
                        }
                    }
                    val events = deferredEvents.awaitAll().filter { !isEventExpired(it.date) }
                    trySend(events)
                }
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

    override suspend fun registerForEvent(eventId: String, userId: String, name: String, email: String): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val eventRef = firestore.collection("events").document(eventId)
            val eventDoc = transaction.get(eventRef)
            val currentCount = eventDoc.getLong("participantCount") ?: 0
            val maxCount = eventDoc.getLong("maxParticipants") ?: 100
            
            if (currentCount >= maxCount) {
                throw IllegalStateException("Event is full")
            }

            val registrantRef = eventRef.collection("registrants").document(userId)
            transaction.set(registrantRef, mapOf(
                "userId" to userId,
                "name" to name,
                "email" to email,
                "registeredAt" to FieldValue.serverTimestamp()
            ))
            transaction.update(eventRef, "participantCount", currentCount + 1)
        }.await()

        // Send notification to the participant
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

        // Send notification to the event host
        val hostId = eventDoc.getString("createdBy") ?: ""
        if (hostId.isNotEmpty() && hostId != userId) {
            val hostNotifData = mapOf(
                "userId" to hostId,
                "type" to "EVENT_REGISTRATION",
                "title" to "New Participant: $name",
                "body" to "$name ($email) registered for $eventTitle.",
                "timestamp" to FieldValue.serverTimestamp(),
                "isRead" to false,
                "actionId" to eventId
            )
            firestore.collection("notifications").add(hostNotifData).await()
        }

        // Sync to Google Calendar if virtual event
        val calendarEventId = eventDoc.getString("calendarEventId") ?: ""
        if (calendarEventId.isNotBlank() && email.isNotBlank()) {
            try {
                syncCalendarAttendee(eventId, email, add = true)
            } catch (e: Exception) {
                Log.w(TAG, "Calendar attendee sync failed (non-fatal): ${e.message}")
            }
        }
        Unit
    }

    override suspend fun unregisterFromEvent(eventId: String, userId: String): Result<Unit> = runCatching {
        // Get user email for Calendar sync before removing
        val registrantDoc = firestore.collection("events").document(eventId)
            .collection("registrants").document(userId).get().await()
        val registrantEmail = registrantDoc.getString("email") ?: ""

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

        // Sync Calendar attendee removal for virtual events
        if (registrantEmail.isNotBlank()) {
            val eventDoc = firestore.collection("events").document(eventId).get().await()
            val calendarEventId = eventDoc.getString("calendarEventId") ?: ""
            if (calendarEventId.isNotBlank()) {
                try {
                    syncCalendarAttendee(eventId, registrantEmail, add = false)
                } catch (e: Exception) {
                    Log.w(TAG, "Calendar attendee removal failed (non-fatal): ${e.message}")
                }
            }
        }
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

    override fun getEventRegistrants(eventId: String): Flow<List<EventRegistrant>> = callbackFlow {
        val registration = firestore.collection("events").document(eventId)
            .collection("registrants")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("EventRepository", "Error fetching event registrants: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val registrants = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    EventRegistrant(
                        userId = doc.id,
                        name = data["name"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        registeredAt = (data["registeredAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L,
                        paymentStatus = data["paymentStatus"] as? String ?: "",
                        transactionId = data["transactionId"] as? String ?: "",
                        amountPaid = (data["amountPaid"] as? Number)?.toDouble() ?: 0.0
                    )
                }
                trySend(registrants)
            }
        awaitClose {
            registration.remove()
        }
    }

    override suspend fun createEvent(event: NetworkEvent, creatorId: String): Result<String> = runCatching {
        val eventMap = mutableMapOf<String, Any>(
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
            "currency" to event.currency,
            "priceAmount" to event.priceAmount,
            "coverUrl" to event.coverUrl,
            "tags" to event.tags,
            "createdBy" to creatorId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        // Add payment collection details for paid events
        if (event.isPaid) {
            eventMap["paymentUpiId"] = event.paymentUpiId
            eventMap["paymentAccountName"] = event.paymentAccountName
            eventMap["paymentInstructions"] = event.paymentInstructions
        }
        val docRef = firestore.collection("events").add(eventMap).await()
        docRef.id
    }

    /**
     * Create a virtual event by calling the Cloud Function which:
     * 1. Creates a Google Calendar event with auto-generated Meet link
     * 2. Creates the Firestore event document
     * Returns Pair(eventId, meetLink)
     */
    override suspend fun createVirtualEvent(
        event: NetworkEvent,
        creatorId: String,
        creatorEmail: String
    ): Result<Pair<String, String>> = runCatching {
        withContext(Dispatchers.IO) {
            val url = URL(CREATE_VIRTUAL_EVENT_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val body = JSONObject().apply {
                put("title", event.title)
                put("description", event.description)
                put("date", event.date)
                put("time", event.time)
                put("maxParticipants", event.maxParticipants)
                put("isPaid", event.isPaid)
                put("price", event.price)
                put("currency", event.currency)
                put("priceAmount", event.priceAmount)
                put("coverUrl", event.coverUrl)
                put("tags", org.json.JSONArray(event.tags))
                put("creatorId", creatorId)
                put("creatorEmail", creatorEmail)
                if (event.isPaid) {
                    put("paymentUpiId", event.paymentUpiId)
                    put("paymentAccountName", event.paymentAccountName)
                    put("paymentInstructions", event.paymentInstructions)
                }
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            connection.disconnect()

            if (responseCode !in 200..299) {
                throw Exception("Failed to create virtual event: $responseBody")
            }

            val json = JSONObject(responseBody)
            val eventId = json.getString("eventId")
            val meetLink = json.optString("meetLink", "")

            Log.d(TAG, "Virtual event created: $eventId, Meet: $meetLink")
            Pair(eventId, meetLink)
        }
    }

    /**
     * Sync a Calendar attendee addition or removal via Cloud Function.
     */
    private suspend fun syncCalendarAttendee(eventId: String, email: String, add: Boolean) {
        withContext(Dispatchers.IO) {
            val urlStr = if (add) ADD_EVENT_PARTICIPANT_URL else REMOVE_EVENT_PARTICIPANT_URL
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val body = JSONObject().apply {
                put("eventId", eventId)
                put("email", email)
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            if (responseCode !in 200..299) {
                Log.w(TAG, "Calendar attendee sync failed with code: $responseCode")
            }
        }
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

                matchesQuery && matchesType && matchesTags && !isEventExpired(event.date)
            }
    }

    override suspend fun registerForPaidEvent(
        eventId: String,
        userId: String,
        name: String,
        email: String,
        transactionId: String,
        amount: Double,
        currency: String,
        paymentMethod: String
    ): Result<EventPaymentRecord> = runCatching {
        val receiptId = "COSMOS-${System.currentTimeMillis().toString(36).uppercase()}-${UUID.randomUUID().toString().take(4).uppercase()}"

        // Get event details for the payment record
        val eventDoc = firestore.collection("events").document(eventId).get().await()
        val eventTitle = eventDoc.getString("title") ?: "Event"
        val organizerUpiId = eventDoc.getString("paymentUpiId") ?: ""
        val organizerName = eventDoc.getString("paymentAccountName") ?: ""

        firestore.runTransaction { transaction ->
            val eventRef = firestore.collection("events").document(eventId)
            val eventSnap = transaction.get(eventRef)
            val currentCount = eventSnap.getLong("participantCount") ?: 0
            val maxCount = eventSnap.getLong("maxParticipants") ?: 100

            if (currentCount >= maxCount) {
                throw IllegalStateException("Event is full")
            }

            // Register the user
            val registrantRef = eventRef.collection("registrants").document(userId)
            transaction.set(registrantRef, mapOf(
                "userId" to userId,
                "name" to name,
                "email" to email,
                "registeredAt" to FieldValue.serverTimestamp(),
                "paymentStatus" to EventPaymentStatus.CONFIRMED.name,
                "transactionId" to transactionId,
                "amountPaid" to amount
            ))

            // Record the payment
            val paymentRef = eventRef.collection("payments").document(userId)
            transaction.set(paymentRef, mapOf(
                "participantId" to userId,
                "participantName" to name,
                "participantEmail" to email,
                "eventId" to eventId,
                "eventTitle" to eventTitle,
                "amount" to amount,
                "currency" to currency,
                "paymentMethod" to paymentMethod,
                "transactionId" to transactionId,
                "paymentStatus" to EventPaymentStatus.CONFIRMED.name,
                "paidAt" to FieldValue.serverTimestamp(),
                "receiptId" to receiptId,
                "organizerUpiId" to organizerUpiId,
                "organizerName" to organizerName
            ))

            transaction.update(eventRef, "participantCount", currentCount + 1)
        }.await()

        // Send notification to participant
        val notifData = mapOf(
            "userId" to userId,
            "type" to "EVENT_REMINDER",
            "title" to "Payment Confirmed — $eventTitle",
            "body" to "Your payment of $currency $amount has been recorded. Receipt: $receiptId",
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "actionId" to eventId
        )
        firestore.collection("notifications").add(notifData).await()

        // Notify organizer
        val hostId = eventDoc.getString("createdBy") ?: ""
        if (hostId.isNotEmpty() && hostId != userId) {
            val hostNotifData = mapOf(
                "userId" to hostId,
                "type" to "EVENT_REGISTRATION",
                "title" to "💰 Paid Registration: $name",
                "body" to "$name paid $currency $amount for $eventTitle. Txn: $transactionId",
                "timestamp" to FieldValue.serverTimestamp(),
                "isRead" to false,
                "actionId" to eventId
            )
            firestore.collection("notifications").add(hostNotifData).await()
        }

        EventPaymentRecord(
            participantId = userId,
            participantName = name,
            participantEmail = email,
            eventId = eventId,
            eventTitle = eventTitle,
            amount = amount,
            currency = currency,
            paymentMethod = paymentMethod,
            transactionId = transactionId,
            paymentStatus = EventPaymentStatus.CONFIRMED.name,
            paidAt = System.currentTimeMillis(),
            receiptId = receiptId,
            organizerUpiId = organizerUpiId,
            organizerName = organizerName
        )
    }

    override suspend fun createEventTicketOrder(
        eventId: String,
        userId: String,
        userName: String,
        userEmail: String,
        userContact: String
    ): Result<EventTicketOrder> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Creating event ticket order: eventId=$eventId, userId=$userId, name=$userName")

            // 1. Try Cloud Function first
            try {
                val requestBody = JSONObject().apply {
                    put("eventId", eventId)
                    put("uid", userId)
                    put("userName", userName)
                    put("userEmail", userEmail)
                    if (userContact.isNotBlank()) {
                        put("userContact", userContact)
                    }
                }

                val response = postJson(CREATE_EVENT_ORDER_URL, requestBody)
                if (response.optBoolean("success", false)) {
                    val orderId = response.getString("orderId")
                    val amount = response.getInt("amount")
                    val amountInPaise = response.optInt("amountInPaise", amount * 100)
                    val currency = response.optString("currency", "INR")
                    val keyId = response.optString("keyId", "")
                    val eventTitle = response.optString("eventTitle", "")

                    return@runCatching EventTicketOrder(
                        orderId = orderId,
                        eventId = eventId,
                        eventTitle = eventTitle,
                        amount = amount,
                        amountInPaise = amountInPaise,
                        currency = currency,
                        keyId = keyId
                    )
                }
            } catch (cfError: Exception) {
                Log.w(TAG, "Cloud Function createEventTicketOrder unavailable, falling back to local order: ${cfError.message}")
            }

            // 2. Direct Firestore fallback (for offline / dev preview)
            val eventDoc = firestore.collection("events").document(eventId).get().await()
            if (!eventDoc.exists()) {
                throw IllegalArgumentException("Event not found")
            }
            val title = eventDoc.getString("title") ?: "Cosmos Event"
            val priceAmount = eventDoc.getDouble("priceAmount") ?: 0.0
            val priceStr = eventDoc.getString("price") ?: ""
            val ticketPrice = if (priceAmount > 0) priceAmount.toInt() else {
                priceStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.toInt() ?: 0
            }
            val orderId = "order_evt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val amountInPaise = ticketPrice * 100

            // Store order record in Firestore (best effort)
            try {
                firestore.collection("event_orders").document(orderId).set(
                    mapOf(
                        "orderId" to orderId,
                        "eventId" to eventId,
                        "eventTitle" to title,
                        "userId" to userId,
                        "userName" to userName,
                        "userEmail" to userEmail,
                        "userContact" to userContact,
                        "amount" to ticketPrice,
                        "amountInPaise" to amountInPaise,
                        "currency" to "INR",
                        "status" to "PENDING",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Event order record save warning: ${e.message}")
            }

            EventTicketOrder(
                orderId = orderId,
                eventId = eventId,
                eventTitle = title,
                amount = ticketPrice,
                amountInPaise = amountInPaise,
                currency = "INR",
                keyId = "rzp_test_placeholder"
            )
        }
    }

    override suspend fun verifyEventTicketPayment(
        eventId: String,
        userId: String,
        orderId: String,
        paymentId: String,
        signature: String,
        name: String,
        email: String
    ): Result<EventPaymentRecord> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Verifying event ticket payment: eventId=$eventId, orderId=$orderId, paymentId=$paymentId")

            // 1. Try Cloud Function first
            try {
                val requestBody = JSONObject().apply {
                    put("uid", userId)
                    put("eventId", eventId)
                    put("orderId", orderId)
                    put("paymentId", paymentId)
                    put("signature", signature)
                    put("userName", name)
                    put("userEmail", email)
                }

                val response = postJson(VERIFY_EVENT_PAYMENT_URL, requestBody)
                if (response.optBoolean("success", false)) {
                    val receiptId = response.optString("receiptId", "COSMOS-TKT-${orderId.takeLast(6).uppercase()}")
                    val amount = response.optDouble("amount", 0.0)
                    val eventTitle = response.optString("eventTitle", "Event")

                    return@runCatching EventPaymentRecord(
                        participantId = userId,
                        participantName = name,
                        participantEmail = email,
                        eventId = eventId,
                        eventTitle = eventTitle,
                        amount = amount,
                        currency = "INR",
                        paymentMethod = "RAZORPAY",
                        transactionId = paymentId,
                        razorpayOrderId = orderId,
                        razorpayPaymentId = paymentId,
                        razorpaySignature = signature,
                        paymentStatus = EventPaymentStatus.CONFIRMED.name,
                        paidAt = System.currentTimeMillis(),
                        receiptId = receiptId,
                        collectedCentrally = true
                    )
                }
            } catch (cfError: Exception) {
                Log.w(TAG, "Cloud Function verifyEventTicketPayment unavailable, applying direct transaction fallback: ${cfError.message}")
            }

            // 2. Direct Firestore fallback
            val eventDoc = firestore.collection("events").document(eventId).get().await()
            val eventTitle = eventDoc.getString("title") ?: "Event"
            val priceAmount = eventDoc.getDouble("priceAmount") ?: 0.0
            val receiptId = "COSMOS-TKT-${System.currentTimeMillis().toString(36).uppercase()}-${UUID.randomUUID().toString().take(4).uppercase()}"

            firestore.runTransaction { tx ->
                val eventRef = firestore.collection("events").document(eventId)
                val eventSnap = tx.get(eventRef)
                val count = eventSnap.getLong("participantCount") ?: 0
                val maxP = eventSnap.getLong("maxParticipants") ?: 100

                if (count >= maxP) throw IllegalStateException("Event is full")

                // Registrant
                val regRef = eventRef.collection("registrants").document(userId)
                tx.set(regRef, mapOf(
                    "userId" to userId,
                    "name" to name,
                    "email" to email,
                    "registeredAt" to FieldValue.serverTimestamp(),
                    "paymentStatus" to EventPaymentStatus.CONFIRMED.name,
                    "transactionId" to paymentId,
                    "orderId" to orderId,
                    "receiptId" to receiptId,
                    "amountPaid" to priceAmount,
                    "paymentMethod" to "RAZORPAY"
                ))

                // Payment record
                val payRef = eventRef.collection("payments").document(userId)
                tx.set(payRef, mapOf(
                    "participantId" to userId,
                    "participantName" to name,
                    "participantEmail" to email,
                    "eventId" to eventId,
                    "eventTitle" to eventTitle,
                    "amount" to priceAmount,
                    "currency" to "INR",
                    "paymentMethod" to "RAZORPAY",
                    "transactionId" to paymentId,
                    "razorpayOrderId" to orderId,
                    "razorpayPaymentId" to paymentId,
                    "razorpaySignature" to signature,
                    "paymentStatus" to EventPaymentStatus.CONFIRMED.name,
                    "paidAt" to FieldValue.serverTimestamp(),
                    "receiptId" to receiptId,
                    "collectedCentrally" to true
                ))

                tx.update(eventRef, "participantCount", count + 1)
            }.await()

            // Update order status
            try {
                firestore.collection("event_orders").document(orderId).update(
                    mapOf(
                        "status" to "COMPLETED",
                        "razorpayPaymentId" to paymentId,
                        "razorpaySignature" to signature,
                        "receiptId" to receiptId,
                        "completedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {}

            // Send notification
            val notif = mapOf(
                "userId" to userId,
                "type" to "EVENT_REMINDER",
                "title" to "Ticket Confirmed — $eventTitle 🎟️",
                "body" to "Your ticket payment has been received. Pass Receipt: $receiptId",
                "timestamp" to FieldValue.serverTimestamp(),
                "isRead" to false,
                "actionId" to eventId
            )
            firestore.collection("notifications").add(notif).await()

            EventPaymentRecord(
                participantId = userId,
                participantName = name,
                participantEmail = email,
                eventId = eventId,
                eventTitle = eventTitle,
                amount = priceAmount,
                currency = "INR",
                paymentMethod = "RAZORPAY",
                transactionId = paymentId,
                razorpayOrderId = orderId,
                razorpayPaymentId = paymentId,
                razorpaySignature = signature,
                paymentStatus = EventPaymentStatus.CONFIRMED.name,
                paidAt = System.currentTimeMillis(),
                receiptId = receiptId,
                collectedCentrally = true
            )
        }
    }

    private fun postJson(urlStr: String, body: JSONObject): JSONObject {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readText()
            }

            if (responseCode !in 200..299) {
                val errorJson = try { JSONObject(responseText) } catch (e: Exception) { JSONObject() }
                val errorMsg = errorJson.optString("error", "Server error (HTTP $responseCode)")
                throw Exception(errorMsg)
            }

            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "FirestoreEventRepository"
        var CREATE_EVENT_ORDER_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/createEventTicketOrder"
        var VERIFY_EVENT_PAYMENT_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/verifyEventTicketPayment"
        var CREATE_VIRTUAL_EVENT_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/createVirtualEvent"
        var ADD_EVENT_PARTICIPANT_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/addEventParticipant"
        var REMOVE_EVENT_PARTICIPANT_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/removeEventParticipant"

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
                currency = data["currency"] as? String ?: "INR",
                priceAmount = (data["priceAmount"] as? Number)?.toDouble() ?: 0.0,
                paymentUpiId = data["paymentUpiId"] as? String ?: "",
                paymentAccountName = data["paymentAccountName"] as? String ?: "",
                paymentInstructions = data["paymentInstructions"] as? String ?: "",
                coverUrl = data["coverUrl"] as? String ?: "",
                tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdBy = data["createdBy"] as? String ?: "",
                createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L,
                isVirtual = data["isVirtual"] as? Boolean ?: false,
                meetLink = data["meetLink"] as? String ?: "",
                calendarEventId = data["calendarEventId"] as? String ?: ""
            )
        }
    }
}
