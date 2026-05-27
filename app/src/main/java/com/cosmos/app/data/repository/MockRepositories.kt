package com.cosmos.app.data.repository

import com.cosmos.app.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

object LocalStore {
    private var prefs: android.content.SharedPreferences? = null

    var currentUserId: String? = null
    val currentUserFlow = MutableStateFlow<Member?>(null)
    
    val users = mutableMapOf<String, Member>()
    val swipes = mutableListOf<Map<String, Any>>()
    
    val connectionsFlow = MutableStateFlow<List<Connection>>(emptyList())
    val messagesMap = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
    
    val eventsFlow = MutableStateFlow<List<NetworkEvent>>(emptyList())
    val circlesFlow = MutableStateFlow<List<Circle>>(emptyList())
    val notificationsFlow = MutableStateFlow<List<Notification>>(emptyList())
    val introRequestsFlow = MutableStateFlow<List<IntroRequest>>(emptyList())
    val eventRounds = mutableMapOf<String, List<EventRound>>()

    // Map of normalized email -> Password
    val userPasswords = mutableMapOf<String, String>()
    // Map of normalized email -> User ID
    val userEmailsToIds = mutableMapOf<String, String>()

    fun initialize(context: android.content.Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("cosmos_local_store", android.content.Context.MODE_PRIVATE)
        loadFromPrefs()
        if (userEmailsToIds.isEmpty()) {
            seed()
            saveToPrefs()
        }
    }

    fun save() {
        saveToPrefs()
    }

    private fun saveToPrefs() {
        val editor = prefs?.edit() ?: return
        editor.putString("current_user_id", currentUserId)
        
        val passwordsJson = JSONObject()
        userPasswords.forEach { (email, pwd) -> passwordsJson.put(email, pwd) }
        editor.putString("user_passwords", passwordsJson.toString())
        
        val emailsJson = JSONObject()
        userEmailsToIds.forEach { (email, uid) -> emailsJson.put(email, uid) }
        editor.putString("user_emails_to_ids", emailsJson.toString())
        
        val usersJson = JSONObject()
        users.forEach { (uid, member) ->
            usersJson.put(uid, memberToJson(member))
        }
        editor.putString("users", usersJson.toString())

        val swipesArray = JSONArray()
        swipes.forEach { swipe ->
            val swipeJson = JSONObject()
            swipe.forEach { (key, value) -> swipeJson.put(key, value) }
            swipesArray.put(swipeJson)
        }
        editor.putString("swipes", swipesArray.toString())

        val connectionsArray = JSONArray()
        connectionsFlow.value.forEach { connectionsArray.put(connectionToJson(it)) }
        editor.putString("connections", connectionsArray.toString())

        val messagesJson = JSONObject()
        messagesMap.forEach { (connId, flow) ->
            val array = JSONArray()
            flow.value.forEach { msg -> array.put(chatMessageToJson(msg)) }
            messagesJson.put(connId, array)
        }
        editor.putString("messages_map", messagesJson.toString())

        val circlesArray = JSONArray()
        circlesFlow.value.forEach { circlesArray.put(circleToJson(it)) }
        editor.putString("circles", circlesArray.toString())

        val notificationsArray = JSONArray()
        notificationsFlow.value.forEach { notificationsArray.put(notificationToJson(it)) }
        editor.putString("notifications", notificationsArray.toString())

        val introRequestsArray = JSONArray()
        introRequestsFlow.value.forEach { introRequestsArray.put(introRequestToJson(it)) }
        editor.putString("intro_requests", introRequestsArray.toString())

        editor.apply()
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        currentUserId = p.getString("current_user_id", null)
        
        userPasswords.clear()
        val passwordsStr = p.getString("user_passwords", null)
        if (passwordsStr != null) {
            val json = JSONObject(passwordsStr)
            json.keys().forEach { email ->
                userPasswords[email] = json.getString(email)
            }
        }
        
        userEmailsToIds.clear()
        val emailsStr = p.getString("user_emails_to_ids", null)
        if (emailsStr != null) {
            val json = JSONObject(emailsStr)
            json.keys().forEach { email ->
                userEmailsToIds[email] = json.getString(email)
            }
        }
        
        users.clear()
        val usersStr = p.getString("users", null)
        if (usersStr != null) {
            val json = JSONObject(usersStr)
            json.keys().forEach { uid ->
                users[uid] = jsonToMember(json.getJSONObject(uid))
            }
        }

        swipes.clear()
        val swipesStr = p.getString("swipes", null)
        if (swipesStr != null) {
            val array = JSONArray(swipesStr)
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val map = mutableMapOf<String, Any>()
                json.keys().forEach { key ->
                    map[key] = json.get(key)
                }
                swipes.add(map)
            }
        }

        val connectionsStr = p.getString("connections", null)
        val connectionsList = mutableListOf<Connection>()
        if (connectionsStr != null) {
            val array = JSONArray(connectionsStr)
            for (i in 0 until array.length()) {
                connectionsList.add(jsonToConnection(array.getJSONObject(i)))
            }
        }
        connectionsFlow.value = connectionsList

        messagesMap.clear()
        val messagesStr = p.getString("messages_map", null)
        if (messagesStr != null) {
            val json = JSONObject(messagesStr)
            json.keys().forEach { connId ->
                val array = json.getJSONArray(connId)
                val list = mutableListOf<ChatMessage>()
                for (i in 0 until array.length()) {
                    list.add(jsonToChatMessage(array.getJSONObject(i)))
                }
                messagesMap[connId] = MutableStateFlow(list)
            }
        }

        val circlesStr = p.getString("circles", null)
        val circlesList = mutableListOf<Circle>()
        if (circlesStr != null) {
            val array = JSONArray(circlesStr)
            for (i in 0 until array.length()) {
                circlesList.add(jsonToCircle(array.getJSONObject(i)))
            }
        }
        circlesFlow.value = circlesList

        val notificationsStr = p.getString("notifications", null)
        val notificationsList = mutableListOf<Notification>()
        if (notificationsStr != null) {
            val array = JSONArray(notificationsStr)
            for (i in 0 until array.length()) {
                notificationsList.add(jsonToNotification(array.getJSONObject(i)))
            }
        }
        notificationsFlow.value = notificationsList

        val introRequestsStr = p.getString("intro_requests", null)
        val introRequestsList = mutableListOf<IntroRequest>()
        if (introRequestsStr != null) {
            val array = JSONArray(introRequestsStr)
            for (i in 0 until array.length()) {
                introRequestsList.add(jsonToIntroRequest(array.getJSONObject(i)))
            }
        }
        introRequestsFlow.value = introRequestsList

        // Update currentUserFlow
        currentUserFlow.value = currentUserId?.let { users[it] }
    }

    private fun seed() {
        val u1Id = "mock_user_sarah"
        val u1 = Member(
            id = u1Id,
            name = "Sarah Jenkins",
            headline = "Founder & CEO at BioSphere",
            role = "CEO",
            company = "BioSphere",
            avatarUrl = "",
            location = "Boston, MA",
            bio = "Building the future of sustainable food systems.",
            tags = listOf("ClimateTech", "Biotech", "Female Founder"),
            primaryUserType = "Founder",
            membershipTier = MembershipTier.FOUNDER
        )
        users[u1Id] = u1
        userPasswords["sarah@biosphere.com"] = "password"
        userEmailsToIds["sarah@biosphere.com"] = u1Id

        val u2Id = "mock_user_david"
        val u2 = Member(
            id = u2Id,
            name = "David Chen",
            headline = "General Partner at Nexus Ventures",
            role = "GP",
            company = "Nexus Ventures",
            avatarUrl = "",
            location = "San Francisco, CA",
            bio = "Investing in early stage AI and enterprise SaaS. Former developer.",
            tags = listOf("AI/ML", "B2B SaaS", "VC"),
            primaryUserType = "Investor",
            membershipTier = MembershipTier.INNER_CIRCLE
        )
        users[u2Id] = u2
        userPasswords["david@nexus.com"] = "password"
        userEmailsToIds["david@nexus.com"] = u2Id

        val u3Id = "mock_user_elena"
        val u3 = Member(
            id = u3Id,
            name = "Elena Rostova",
            headline = "Lead Designer at Cosmos Studio",
            role = "Designer",
            company = "Cosmos Studio",
            avatarUrl = "",
            location = "New York, NY",
            bio = "Passionate about premium design systems and minimalist interfaces.",
            tags = listOf("UI/UX", "Product Design", "Creative"),
            primaryUserType = "Creator",
            membershipTier = MembershipTier.MEMBER
        )
        users[u3Id] = u3
        userPasswords["elena@cosmos.design"] = "password"
        userEmailsToIds["elena@cosmos.design"] = u3Id

        val u4Id = "mock_user_marcus"
        val u4 = Member(
            id = u4Id,
            name = "Marcus Vance",
            headline = "VP of Product at ScaleUp",
            role = "Product VP",
            company = "ScaleUp",
            avatarUrl = "",
            location = "Austin, TX",
            bio = "Scale specialist. Former 0-to-1 PM at Stripe and Airbnb.",
            tags = listOf("Product Management", "Growth", "Scaling"),
            primaryUserType = "Startup Operator",
            membershipTier = MembershipTier.EXPLORER
        )
        users[u4Id] = u4
        userPasswords["marcus@scaleup.com"] = "password"
        userEmailsToIds["marcus@scaleup.com"] = u4Id

        circlesFlow.value = listOf(
            Circle(
                id = "circle_1",
                name = "AI Builders & Founders",
                description = "A circle for developers and founders building next-gen generative AI applications.",
                memberCount = 142,
                theme = "AI & Technology",
                tags = listOf("AI/ML", "LLMs", "Tech"),
                adminName = "David Chen"
            ),
            Circle(
                id = "circle_2",
                name = "Cosmos Founders Club",
                description = "Official private circle for verified Cosmos startup founders to share tips and resources.",
                memberCount = 88,
                theme = "Entrepreneurship",
                tags = listOf("Founders", "Fundraising", "Cosmos"),
                adminName = "Sarah Jenkins"
            ),
            Circle(
                id = "circle_3",
                name = "Premium Design Collective",
                description = "Discussing aesthetics, premium typography, and UI/UX design trends.",
                memberCount = 56,
                theme = "Design",
                tags = listOf("Design", "UI/UX", "Aesthetics"),
                adminName = "Elena Rostova"
            )
        )

        val calendar = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
        
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val date1 = sdf.format(calendar.time)
        
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 2)
        val date2 = sdf.format(calendar.time)
        
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 3)
        val date3 = sdf.format(calendar.time)

        eventsFlow.value = listOf(
            NetworkEvent(
                id = "event_1",
                title = "Founder Speed Matchmaking",
                description = "Intentional 1-on-1 networking matches for founders and operators seeking co-founders.",
                date = date1,
                time = "6:00 PM UTC",
                location = "Cosmos Virtual Room",
                type = EventType.FOUNDER_MEETUP,
                participantCount = 18,
                maxParticipants = 30,
                tags = listOf("Speed Dating", "Co-Founder", "Interactive")
            ),
            NetworkEvent(
                id = "event_2",
                title = "AI & B2B SaaS Showcase",
                description = "Presenting three breakout startups building AI-first enterprise workflows.",
                date = date2,
                time = "4:00 PM UTC",
                location = "Cosmos Hall",
                type = EventType.INDUSTRY_SPECIFIC,
                participantCount = 45,
                maxParticipants = 100,
                tags = listOf("AI", "B2B SaaS", "Showcase")
            ),
            NetworkEvent(
                id = "event_3",
                title = "Cosmos Open Networking Hour",
                description = "Meet and chat with other members of the club in a relaxed, open format.",
                date = date3,
                time = "7:00 PM UTC",
                location = "Cosmos Lounge",
                type = EventType.OPEN_NETWORKING,
                participantCount = 22,
                maxParticipants = 50,
                tags = listOf("Networking", "Open", "Social")
            )
        )
    }

    private fun memberToJson(member: Member): JSONObject {
        val json = JSONObject()
        json.put("id", member.id)
        json.put("name", member.name)
        json.put("headline", member.headline)
        json.put("role", member.role)
        json.put("company", member.company)
        json.put("avatarUrl", member.avatarUrl)
        json.put("location", member.location)
        json.put("bio", member.bio)
        
        val tagsArray = JSONArray()
        member.tags.forEach { tagsArray.put(it) }
        json.put("tags", tagsArray)
        
        json.put("goalStatement", member.goalStatement)
        json.put("longTermVision", member.longTermVision)
        
        val skillsArray = JSONArray()
        member.endorsedSkills.forEach { skill ->
            val skillJson = JSONObject()
            skillJson.put("name", skill.name)
            skillJson.put("count", skill.count)
            val endorsersArray = JSONArray()
            skill.endorsers.forEach { endorsersArray.put(it) }
            skillJson.put("endorsers", endorsersArray)
            skillsArray.put(skillJson)
        }
        json.put("endorsedSkills", skillsArray)
        
        json.put("mutualConnectionsCount", member.mutualConnectionsCount)
        json.put("isLinkedInConnected", member.isLinkedInConnected)
        json.put("memberSince", member.memberSince)
        json.put("membershipTier", member.membershipTier.name)
        json.put("connectionsCount", member.connectionsCount)
        json.put("eventsAttended", member.eventsAttended)
        json.put("followUpsCompleted", member.followUpsCompleted)
        json.put("primaryUserType", member.primaryUserType)
        return json
    }

    private fun jsonToMember(json: JSONObject): Member {
        val tagsList = mutableListOf<String>()
        val tagsArray = json.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tagsList.add(tagsArray.getString(i))
            }
        }
        
        val skillsList = mutableListOf<EndorsedSkill>()
        val skillsArray = json.optJSONArray("endorsedSkills")
        if (skillsArray != null) {
            for (i in 0 until skillsArray.length()) {
                val skillJson = skillsArray.getJSONObject(i)
                val endorsersList = mutableListOf<String>()
                val endorsersArray = skillJson.optJSONArray("endorsers")
                if (endorsersArray != null) {
                    for (j in 0 until endorsersArray.length()) {
                        endorsersList.add(endorsersArray.getString(j))
                    }
                }
                skillsList.add(
                    EndorsedSkill(
                        name = skillJson.getString("name"),
                        count = skillJson.getInt("count"),
                        endorsers = endorsersList
                    )
                )
            }
        }
        
        val tierStr = json.optString("membershipTier", MembershipTier.EXPLORER.name)
        val tier = try { MembershipTier.valueOf(tierStr) } catch(e: Exception) { MembershipTier.EXPLORER }
        
        return Member(
            id = json.getString("id"),
            name = json.getString("name"),
            headline = json.optString("headline", ""),
            role = json.optString("role", ""),
            company = json.optString("company", ""),
            avatarUrl = json.optString("avatarUrl", ""),
            location = json.optString("location", ""),
            bio = json.optString("bio", ""),
            tags = tagsList,
            goalStatement = json.optString("goalStatement", ""),
            longTermVision = json.optString("longTermVision", ""),
            endorsedSkills = skillsList,
            mutualConnectionsCount = json.optInt("mutualConnectionsCount", 0),
            isLinkedInConnected = json.optBoolean("isLinkedInConnected", false),
            memberSince = json.optString("memberSince", ""),
            membershipTier = tier,
            connectionsCount = json.optInt("connectionsCount", 0),
            eventsAttended = json.optInt("eventsAttended", 0),
            followUpsCompleted = json.optInt("followUpsCompleted", 0),
            primaryUserType = json.optString("primaryUserType", "")
        )
    }

    private fun connectionToJson(connection: Connection): JSONObject {
        val json = JSONObject()
        json.put("id", connection.id)
        json.put("member", memberToJson(connection.member))
        json.put("lastMessage", connection.lastMessage)
        json.put("lastMessageTime", connection.lastMessageTime)
        json.put("unreadCount", connection.unreadCount)
        
        val labelsArray = JSONArray()
        connection.labels.forEach { labelsArray.put(it) }
        json.put("labels", labelsArray)
        
        json.put("privateGoal", connection.privateGoal)
        json.put("status", connection.status.name)
        return json
    }

    private fun jsonToConnection(json: JSONObject): Connection {
        val labelsList = mutableListOf<String>()
        val labelsArray = json.optJSONArray("labels")
        if (labelsArray != null) {
            for (i in 0 until labelsArray.length()) {
                labelsList.add(labelsArray.getString(i))
            }
        }
        
        val statusStr = json.optString("status", ConnectionStatus.ACTIVE.name)
        val status = try { ConnectionStatus.valueOf(statusStr) } catch(e: Exception) { ConnectionStatus.ACTIVE }
        
        return Connection(
            id = json.getString("id"),
            member = jsonToMember(json.getJSONObject("member")),
            lastMessage = json.optString("lastMessage", ""),
            lastMessageTime = json.optString("lastMessageTime", ""),
            unreadCount = json.optInt("unreadCount", 0),
            labels = labelsList,
            privateGoal = json.optString("privateGoal", ""),
            status = status
        )
    }

    private fun chatMessageToJson(msg: ChatMessage): JSONObject {
        val json = JSONObject()
        json.put("id", msg.id)
        json.put("senderId", msg.senderId)
        json.put("text", msg.text)
        json.put("timestamp", msg.timestamp)
        json.put("isOwn", msg.isOwn)
        json.put("type", msg.type.name)
        return json
    }

    private fun jsonToChatMessage(json: JSONObject): ChatMessage {
        val typeStr = json.optString("type", MessageType.TEXT.name)
        val type = try { MessageType.valueOf(typeStr) } catch(e: Exception) { MessageType.TEXT }
        
        return ChatMessage(
            id = json.getString("id"),
            senderId = json.getString("senderId"),
            text = json.getString("text"),
            timestamp = json.getString("timestamp"),
            isOwn = json.getBoolean("isOwn"),
            type = type
        )
    }

    private fun circleToJson(circle: Circle): JSONObject {
        val json = JSONObject()
        json.put("id", circle.id)
        json.put("name", circle.name)
        json.put("description", circle.description)
        json.put("coverUrl", circle.coverUrl)
        json.put("memberCount", circle.memberCount)
        json.put("theme", circle.theme)
        
        val tagsArray = JSONArray()
        circle.tags.forEach { tagsArray.put(it) }
        json.put("tags", tagsArray)
        
        json.put("isJoined", circle.isJoined)
        json.put("isPrivate", circle.isPrivate)
        json.put("adminName", circle.adminName)
        return json
    }

    private fun jsonToCircle(json: JSONObject): Circle {
        val tagsList = mutableListOf<String>()
        val tagsArray = json.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tagsList.add(tagsArray.getString(i))
            }
        }
        
        return Circle(
            id = json.getString("id"),
            name = json.getString("name"),
            description = json.getString("description"),
            coverUrl = json.optString("coverUrl", ""),
            memberCount = json.getInt("memberCount"),
            theme = json.getString("theme"),
            tags = tagsList,
            isJoined = json.optBoolean("isJoined", false),
            isPrivate = json.optBoolean("isPrivate", false),
            adminName = json.optString("adminName", "")
        )
    }

    private fun notificationToJson(notif: Notification): JSONObject {
        val json = JSONObject()
        json.put("id", notif.id)
        json.put("type", notif.type.name)
        json.put("title", notif.title)
        json.put("body", notif.body)
        json.put("timestamp", notif.timestamp)
        json.put("isRead", notif.isRead)
        json.put("actionId", notif.actionId)
        return json
    }

    private fun jsonToNotification(json: JSONObject): Notification {
        val typeStr = json.optString("type", NotificationType.MESSAGE.name)
        val type = try { NotificationType.valueOf(typeStr) } catch(e: Exception) { NotificationType.MESSAGE }
        
        return Notification(
            id = json.getString("id"),
            type = type,
            title = json.getString("title"),
            body = json.getString("body"),
            timestamp = json.getString("timestamp"),
            isRead = json.optBoolean("isRead", false),
            actionId = json.optString("actionId", "")
        )
    }

    private fun introRequestToJson(req: IntroRequest): JSONObject {
        val json = JSONObject()
        json.put("id", req.id)
        json.put("requester", memberToJson(req.requester))
        json.put("target", memberToJson(req.target))
        json.put("connector", memberToJson(req.connector))
        json.put("message", req.message)
        json.put("status", req.status.name)
        return json
    }

    private fun jsonToIntroRequest(json: JSONObject): IntroRequest {
        val statusStr = json.optString("status", IntroStatus.PENDING.name)
        val status = try { IntroStatus.valueOf(statusStr) } catch(e: Exception) { IntroStatus.PENDING }
        
        return IntroRequest(
            id = json.getString("id"),
            requester = jsonToMember(json.getJSONObject("requester")),
            target = jsonToMember(json.getJSONObject("target")),
            connector = jsonToMember(json.getJSONObject("connector")),
            message = json.getString("message"),
            status = status
        )
    }
}

// ── MockAuthRepository ────────────────────────────────────────────────────────
class MockAuthRepository : AuthRepository {
    override val currentUserId: String?
        get() = LocalStore.currentUserId

    override val currentUser: Flow<Member?>
        get() = LocalStore.currentUserFlow

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        val uid = LocalStore.userEmailsToIds[normalizedEmail]
        val storedPassword = LocalStore.userPasswords[normalizedEmail]
        
        if (uid != null && storedPassword == password) {
            val user = LocalStore.users[uid] ?: throw Exception("User profile not found")
            LocalStore.currentUserId = uid
            LocalStore.currentUserFlow.value = user
        } else if (uid != null) {
            throw Exception("Incorrect password. Please try again.")
        } else {
            throw Exception("No account found with this email. Please sign up first.")
        }
    }

    override suspend fun signUp(email: String, password: String, name: String, primaryType: String): Result<Unit> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        if (LocalStore.userEmailsToIds.containsKey(normalizedEmail)) {
            throw com.google.firebase.auth.FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "An account already exists with this email. Please sign in instead.")
        }
        
        val uid = "mock_user_${UUID.randomUUID()}"
        val newUser = Member(
            id = uid,
            name = name,
            headline = "$primaryType at Cosmos",
            role = primaryType,
            company = "Cosmos",
            avatarUrl = "",
            location = "San Francisco, CA",
            membershipTier = MembershipTier.EXPLORER,
            primaryUserType = primaryType
        )
        LocalStore.users[uid] = newUser
        LocalStore.userPasswords[normalizedEmail] = password
        LocalStore.userEmailsToIds[normalizedEmail] = uid
        LocalStore.currentUserId = uid
        LocalStore.currentUserFlow.value = newUser
        LocalStore.save()
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        LocalStore.currentUserId = null
        LocalStore.currentUserFlow.value = null
        LocalStore.save()
    }

    override suspend fun saveOnboardingData(member: Member): Result<Unit> = runCatching {
        val uid = LocalStore.currentUserId ?: throw IllegalStateException("Not logged in")
        val updatedMember = member.copy(id = uid)
        LocalStore.users[uid] = updatedMember
        LocalStore.currentUserFlow.value = updatedMember
        LocalStore.save()
    }

    override suspend fun uploadProfileImage(uid: String, bytes: ByteArray): Result<String> = runCatching {
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    }
}

// ── MockProfileRepository ─────────────────────────────────────────────────────
class MockProfileRepository : ProfileRepository {
    override suspend fun getProfile(userId: String): Result<Member> = runCatching {
        LocalStore.users[userId] ?: throw IllegalArgumentException("User not found: $userId")
    }

    override suspend fun getAllProfiles(): Result<List<Member>> = runCatching {
        LocalStore.users.values.toList()
    }

    override suspend fun getDiscoveryDeck(currentUserId: String): Result<List<Member>> = runCatching {
        val currentUser = LocalStore.users[currentUserId] ?: throw IllegalArgumentException("User not found")
        val swipedIds = LocalStore.swipes.filter { it["likerId"] == currentUserId }.map { it["likedId"] as String }.toSet()
        
        LocalStore.users.values
            .filter { it.id != currentUserId && !swipedIds.contains(it.id) }
            .sortedByDescending { candidate ->
                val sharedTags = candidate.tags.intersect(currentUser.tags.toSet()).size
                sharedTags * 10 + (if (candidate.isLinkedInConnected) 5 else 0)
            }
    }

    override suspend fun endorseSkill(userId: String, endorserName: String, skillName: String): Result<Unit> = runCatching {
        val user = LocalStore.users[userId] ?: throw IllegalArgumentException("User not found")
        val currentSkills = user.endorsedSkills.toMutableList()
        val index = currentSkills.indexOfFirst { it.name.equals(skillName, ignoreCase = true) }
        if (index >= 0) {
            val skill = currentSkills[index]
            if (!skill.endorsers.contains(endorserName)) {
                currentSkills[index] = skill.copy(
                    count = skill.count + 1,
                    endorsers = skill.endorsers + endorserName
                )
            }
        } else {
            currentSkills.add(EndorsedSkill(skillName, 1, listOf(endorserName)))
        }
        LocalStore.users[userId] = user.copy(endorsedSkills = currentSkills)
        
        val notification = Notification(
            id = UUID.randomUUID().toString(),
            type = NotificationType.ENDORSEMENT_RECEIVED,
            title = "New Endorsement",
            body = "$endorserName endorsed your $skillName skill.",
            timestamp = "Just now",
            isRead = false,
            actionId = userId
        )
        LocalStore.notificationsFlow.value = listOf(notification) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }

    override suspend fun getEndorsedSkills(userId: String): Result<List<EndorsedSkill>> = runCatching {
        LocalStore.users[userId]?.endorsedSkills ?: emptyList()
    }
}

// ── MockSwipeRepository ───────────────────────────────────────────────────────
class MockSwipeRepository : SwipeRepository {
    override suspend fun recordSwipe(likerId: String, likedId: String, action: String): Result<Boolean> = runCatching {
        LocalStore.swipes.add(mapOf("likerId" to likerId, "likedId" to likedId, "action" to action))
        
        if (action == "LIKE") {
            // Auto-swipe back in Mock Mode to enable testing match flows and chat!
            val reverseSwipe = mapOf("likerId" to likedId, "likedId" to likerId, "action" to "LIKE")
            if (LocalStore.swipes.none { it["likerId"] == likedId && it["likedId"] == likerId }) {
                LocalStore.swipes.add(reverseSwipe)
            }
            
            val connectionId = if (likerId < likedId) "${likerId}_${likedId}" else "${likedId}_${likerId}"
            val member1 = LocalStore.users[likerId] ?: SampleData.sampleMember
            val member2 = LocalStore.users[likedId] ?: SampleData.sampleMember
            
            val newConn = Connection(
                id = connectionId,
                member = member2,
                lastMessage = "You matched! Say hello.",
                lastMessageTime = "Now",
                unreadCount = 0,
                labels = emptyList(),
                privateGoal = "",
                status = ConnectionStatus.ACTIVE
            )
            
            if (LocalStore.connectionsFlow.value.none { it.id == connectionId }) {
                LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value + newConn
            }
            
            val notif1 = Notification(
                id = UUID.randomUUID().toString(),
                type = NotificationType.NEW_MATCH,
                title = "New Match! 🎉",
                body = "You matched with ${member2.name}. Start a conversation now!",
                timestamp = "Now",
                isRead = false,
                actionId = likedId
            )
            val notif2 = Notification(
                id = UUID.randomUUID().toString(),
                type = NotificationType.NEW_MATCH,
                title = "New Match! 🎉",
                body = "You matched with ${member1.name}. Start a conversation now!",
                timestamp = "Now",
                isRead = false,
                actionId = likerId
            )
            LocalStore.notificationsFlow.value = listOf(notif1, notif2) + LocalStore.notificationsFlow.value
            LocalStore.save()
            return@runCatching true
        }
        LocalStore.save()
        false
    }

    override suspend fun getMonthlyConnectionsCount(userId: String): Result<Int> = runCatching {
        LocalStore.connectionsFlow.value.size
    }
}

// ── MockChatRepository ────────────────────────────────────────────────────────
class MockChatRepository : ChatRepository {
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun getConnections(userId: String): Flow<List<Connection>> {
        return LocalStore.connectionsFlow.map { list ->
            list.filter { conn ->
                if (conn.id.startsWith("intro_")) {
                    val reqId = conn.id.removePrefix("intro_")
                    val req = LocalStore.introRequestsFlow.value.find { it.id == reqId }
                    req != null && (req.requester.id == userId || req.target.id == userId)
                } else {
                    conn.id.startsWith("${userId}_") || conn.id.endsWith("_${userId}")
                }
            }.map { conn ->
                val otherId = if (conn.id.startsWith("intro_")) {
                    val reqId = conn.id.removePrefix("intro_")
                    val req = LocalStore.introRequestsFlow.value.find { it.id == reqId }!!
                    if (req.requester.id == userId) req.target.id else req.requester.id
                } else {
                    if (conn.id.startsWith("${userId}_")) {
                        conn.id.removePrefix("${userId}_")
                    } else {
                        conn.id.removeSuffix("_${userId}")
                    }
                }
                val otherProfile = LocalStore.users[otherId] ?: conn.member
                conn.copy(member = otherProfile)
            }
        }
    }

    override fun getConnection(connectionId: String, currentUserId: String): Flow<Connection?> {
        return LocalStore.connectionsFlow.map { list ->
            val conn = list.find { it.id == connectionId }
            if (conn != null) {
                val isMember = if (conn.id.startsWith("intro_")) {
                    val reqId = conn.id.removePrefix("intro_")
                    val req = LocalStore.introRequestsFlow.value.find { it.id == reqId }
                    req != null && (req.requester.id == currentUserId || req.target.id == currentUserId)
                } else {
                    conn.id.startsWith("${currentUserId}_") || conn.id.endsWith("_${currentUserId}")
                }
                if (isMember) {
                    val otherId = if (conn.id.startsWith("intro_")) {
                        val reqId = conn.id.removePrefix("intro_")
                        val req = LocalStore.introRequestsFlow.value.find { it.id == reqId }!!
                        if (req.requester.id == currentUserId) req.target.id else req.requester.id
                    } else {
                        if (conn.id.startsWith("${currentUserId}_")) {
                            conn.id.removePrefix("${currentUserId}_")
                        } else {
                            conn.id.removeSuffix("_${currentUserId}")
                        }
                    }
                    val otherProfile = LocalStore.users[otherId] ?: conn.member
                    conn.copy(member = otherProfile)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    override fun getMessages(connectionId: String): Flow<List<ChatMessage>> {
        val flow = LocalStore.messagesMap.getOrPut(connectionId) {
            MutableStateFlow(emptyList())
        }
        return flow
    }

    override suspend fun sendMessage(connectionId: String, senderId: String, text: String, type: MessageType): Result<Unit> = runCatching {
        val flow = LocalStore.messagesMap.getOrPut(connectionId) {
            MutableStateFlow(emptyList())
        }
        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            text = text,
            timestamp = "Now",
            isOwn = senderId == LocalStore.currentUserId,
            type = type
        )
        flow.value = flow.value + newMessage
        
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
            if (conn.id == connectionId) {
                conn.copy(
                    lastMessage = if (type == MessageType.AI_SUMMARY) "AI Summary Ready" else text,
                    lastMessageTime = "Now"
                )
            } else {
                conn
            }
        }
        LocalStore.save()

        // Simulating the other user replying in real-time
        if (senderId == LocalStore.currentUserId) {
            val otherId = if (connectionId.startsWith("intro_")) {
                val reqId = connectionId.removePrefix("intro_")
                val req = LocalStore.introRequestsFlow.value.find { it.id == reqId }
                if (req != null) {
                    if (req.requester.id == senderId) req.target.id else req.requester.id
                } else ""
            } else {
                if (connectionId.startsWith("${senderId}_")) {
                    connectionId.removePrefix("${senderId}_")
                } else if (connectionId.endsWith("_${senderId}")) {
                    connectionId.removeSuffix("_${senderId}")
                } else {
                    ""
                }
            }
            if (otherId.isNotEmpty()) {
                scope.launch {
                    delay(1500)
                    val replyText = when (otherId) {
                        "mock_user_sarah" -> listOf(
                            "Hey! Great to connect. I'd love to chat about sustainable food systems or potential collaborations.",
                            "Hi! How's your week going? What are you working on?",
                            "BioSphere is keeping me busy, but always love meeting new builders here on Cosmos!",
                            "Have you raised capital before, or are you bootstrapping?"
                        ).random()
                        "mock_user_david" -> listOf(
                            "Thanks for reaching out! What stage is your startup at? We're looking for early-stage AI founders.",
                            "Hello! Let's schedule a call to chat more about your goals.",
                            "We are active in B2B SaaS and AI. Tell me about your team.",
                            "Great. How do you think about distribution or acquisition?"
                        ).random()
                        "mock_user_elena" -> listOf(
                            "Hey there! Love your profile. Let's exchange design feedback sometime!",
                            "Hi! What do you think of the new design systems on mobile?",
                            "Awesome! I'm lead designer here, working on premium interfaces.",
                            "Design aesthetics are key. What tools do you guys use?"
                        ).random()
                        "mock_user_marcus" -> listOf(
                            "Scale specialist here. Always happy to chat about product growth or Stripe/Airbnb scale days.",
                            "Hey! How are you scaling user acquisition right now?",
                            "Product market fit is hard, but growth optimization is where the fun starts.",
                            "Hi! What PM challenges are you facing today?"
                        ).random()
                        else -> "Hey! Thanks for matching. Let's catch up soon."
                    }

                    val replyMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = otherId,
                        text = replyText,
                        timestamp = "Now",
                        isOwn = false,
                        type = MessageType.TEXT
                    )
                    flow.value = flow.value + replyMsg
                    
                    LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
                        if (conn.id == connectionId) {
                            conn.copy(
                                lastMessage = replyText,
                                lastMessageTime = "Now",
                                unreadCount = conn.unreadCount + 1
                            )
                        } else {
                            conn
                        }
                    }
                    
                    // Create a real-time message notification for the user!
                    val senderProfile = LocalStore.users[otherId]
                    val notif = Notification(
                        id = UUID.randomUUID().toString(),
                        type = NotificationType.MESSAGE,
                        title = senderProfile?.name ?: "New Message",
                        body = replyText,
                        timestamp = "Now",
                        isRead = false,
                        actionId = otherId
                    )
                    LocalStore.notificationsFlow.value = listOf(notif) + LocalStore.notificationsFlow.value
                    LocalStore.save()
                }
            }
        }
    }

    override suspend fun updateCrmLabels(connectionId: String, userId: String, labels: List<String>): Result<Unit> = runCatching {
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
            if (conn.id == connectionId) {
                conn.copy(labels = labels)
            } else {
                conn
            }
        }
        LocalStore.save()
    }

    override suspend fun updatePrivateGoal(connectionId: String, userId: String, goal: String): Result<Unit> = runCatching {
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
            if (conn.id == connectionId) {
                conn.copy(privateGoal = goal)
            } else {
                conn
            }
        }
        LocalStore.save()
    }

    override suspend fun saveAiSummary(connectionId: String, summaryText: String): Result<Unit> = runCatching {
        sendMessage(connectionId, "system", summaryText, MessageType.AI_SUMMARY).getOrThrow()
    }
}

// ── MockEventRepository ───────────────────────────────────────────────────────
class MockEventRepository : EventRepository {
    override fun getEvents(): Flow<List<NetworkEvent>> {
        return LocalStore.eventsFlow
    }

    override fun getEvent(eventId: String, currentUserId: String): Flow<NetworkEvent?> {
        return LocalStore.eventsFlow.map { list ->
            list.find { it.id == eventId }
        }
    }

    override suspend fun registerForEvent(eventId: String, userId: String): Result<Unit> = runCatching {
        LocalStore.eventsFlow.value = LocalStore.eventsFlow.value.map { event ->
            if (event.id == eventId) {
                event.copy(
                    isRegistered = true,
                    participantCount = event.participantCount + 1
                )
            } else {
                event
            }
        }
        LocalStore.save()
    }

    override fun getEventRounds(eventId: String): Flow<List<EventRound>> {
        return LocalStore.eventsFlow.map { list ->
            val event = list.find { it.id == eventId }
            val isUserRegistered = event?.isRegistered == true
            val uid = LocalStore.currentUserId
            val participants = LocalStore.users.values.toList()
            val otherParticipants = participants.filter { it.id != uid }
            
            val rounds = mutableListOf<EventRound>()
            if (isUserRegistered && uid != null) {
                val currentUser = LocalStore.users[uid]
                if (currentUser != null) {
                    val p1 = otherParticipants.getOrNull(0)
                    val p2 = otherParticipants.getOrNull(1)
                    if (p1 != null) {
                        rounds.add(EventRound("r1", "Round 1: Intros", 15, listOf(currentUser, p1)))
                    }
                    if (p2 != null) {
                        rounds.add(EventRound("r2", "Round 2: Collaborative Ideas", 15, listOf(currentUser, p2)))
                    }
                }
            } else {
                val p1 = participants.getOrNull(0)
                val p2 = participants.getOrNull(1)
                val p3 = participants.getOrNull(2)
                val p4 = participants.getOrNull(3)
                if (p1 != null && p2 != null) {
                    rounds.add(EventRound("r1", "Round 1: Intros", 15, listOf(p1, p2)))
                }
                if (p3 != null && p4 != null) {
                    rounds.add(EventRound("r2", "Round 2: Collaborative Ideas", 15, listOf(p3, p4)))
                }
            }
            rounds
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
        // Mock feedback successful
    }

    override fun getEventParticipants(eventId: String): Flow<List<Member>> {
        return flow {
            emit(LocalStore.users.values.toList())
        }
    }
}

// ── MockCircleRepository ──────────────────────────────────────────────────────
class MockCircleRepository : CircleRepository {
    override fun getCircles(currentUserId: String): Flow<List<Circle>> {
        return LocalStore.circlesFlow
    }

    override suspend fun getCircle(circleId: String, currentUserId: String): Result<Circle> = runCatching {
        LocalStore.circlesFlow.value.find { it.id == circleId } ?: throw IllegalArgumentException("Circle not found")
    }

    override suspend fun joinCircle(circleId: String, userId: String): Result<Unit> = runCatching {
        LocalStore.circlesFlow.value = LocalStore.circlesFlow.value.map { circle ->
            if (circle.id == circleId) {
                circle.copy(
                    isJoined = true,
                    memberCount = circle.memberCount + 1
                )
            } else {
                circle
            }
        }
        LocalStore.save()
    }

    private val postsMap = mutableMapOf<String, MutableStateFlow<List<CirclePost>>>()

    override fun getCirclePosts(circleId: String): Flow<List<CirclePost>> {
        val flow = postsMap.getOrPut(circleId) {
            MutableStateFlow(emptyList())
        }
        return flow
    }

    override suspend fun createCirclePost(
        circleId: String,
        authorId: String,
        authorName: String,
        authorAvatar: String,
        content: String
    ): Result<Unit> = runCatching {
        val flow = postsMap.getOrPut(circleId) {
            MutableStateFlow(emptyList())
        }
        val newPost = CirclePost(
            authorId = authorId,
            author = authorName,
            avatarUrl = authorAvatar,
            content = content,
            timeString = "Just now"
        )
        flow.value = listOf(newPost) + flow.value
        LocalStore.save()
    }

    override suspend fun getCircleMembers(circleId: String): Result<List<Member>> = runCatching {
        LocalStore.users.values.toList()
    }
}

// ── MockNotificationRepository ────────────────────────────────────────────────
class MockNotificationRepository : NotificationRepository {
    override fun getNotifications(userId: String): Flow<List<Notification>> {
        return LocalStore.notificationsFlow
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        LocalStore.notificationsFlow.value = LocalStore.notificationsFlow.value.map { notif ->
            if (notif.id == notificationId) notif.copy(isRead = true) else notif
        }
        LocalStore.save()
    }

    override suspend fun createNotification(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        actionId: String
    ): Result<Unit> = runCatching {
        val newNotif = Notification(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            body = body,
            timestamp = "Just now",
            isRead = false,
            actionId = actionId
        )
        LocalStore.notificationsFlow.value = listOf(newNotif) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }
}

// ── MockIntroRepository ───────────────────────────────────────────────────────
class MockIntroRepository : IntroRepository {
    override suspend fun requestWarmIntro(
        requesterId: String,
        targetId: String,
        connectorId: String,
        message: String
    ): Result<Unit> = runCatching {
        val reqUser = LocalStore.users[requesterId] ?: SampleData.sampleMember
        val targetUser = LocalStore.users[targetId] ?: SampleData.sampleMember
        val connUser = LocalStore.users[connectorId] ?: SampleData.sampleMember
        
        val requestId = "req_${UUID.randomUUID()}"
        val newRequest = IntroRequest(
            id = requestId,
            requester = reqUser,
            target = targetUser,
            connector = connUser,
            message = message,
            status = IntroStatus.PENDING
        )
        
        LocalStore.introRequestsFlow.value = LocalStore.introRequestsFlow.value + newRequest
        
        val notification = Notification(
            id = UUID.randomUUID().toString(),
            type = NotificationType.WARM_INTRO_REQUEST,
            title = "Introduction Request",
            body = "${reqUser.name} is requesting an introduction through you.",
            timestamp = "Now",
            isRead = false,
            actionId = requestId
        )
        LocalStore.notificationsFlow.value = listOf(notification) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }

    override suspend fun getIntroRequest(requestId: String): Result<IntroRequest> = runCatching {
        LocalStore.introRequestsFlow.value.find { it.id == requestId } ?: throw IllegalArgumentException("Request not found")
    }

    override suspend fun respondToIntroRequest(requestId: String, status: IntroStatus): Result<Unit> = runCatching {
        LocalStore.introRequestsFlow.value = LocalStore.introRequestsFlow.value.map { req ->
            if (req.id == requestId) {
                req.copy(status = status)
            } else {
                req
            }
        }
        
        val req = LocalStore.introRequestsFlow.value.find { it.id == requestId } ?: return@runCatching
        
        val typeStr = if (status == IntroStatus.ACCEPTED) NotificationType.WARM_INTRO_ACCEPTED else NotificationType.WARM_INTRO_DECLINED
        val title = if (status == IntroStatus.ACCEPTED) "Intro Request Accepted!" else "Intro Request Declined"
        val body = if (status == IntroStatus.ACCEPTED) {
            "Your introduction request has been accepted by ${req.connector.name}. Say hello!"
        } else {
            "Your introduction request was declined by ${req.connector.name}."
        }

        val notification = Notification(
            id = UUID.randomUUID().toString(),
            type = typeStr,
            title = title,
            body = body,
            timestamp = "Now",
            isRead = false,
            actionId = req.target.id
        )
        LocalStore.notificationsFlow.value = listOf(notification) + LocalStore.notificationsFlow.value

        if (status == IntroStatus.ACCEPTED) {
            val connectionId = "intro_${requestId}"
            val connection = Connection(
                id = connectionId,
                member = req.target,
                lastMessage = "3-Way Intro started! Say hello.",
                lastMessageTime = "Now",
                unreadCount = 0,
                labels = emptyList(),
                privateGoal = "",
                status = ConnectionStatus.INTRO_REQUESTED
            )
            LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value + connection
        }
        LocalStore.save()
    }

    override fun getIntroRequestsForUser(userId: String): Flow<List<IntroRequest>> {
        return LocalStore.introRequestsFlow.map { list ->
            list.filter { it.connector.id == userId }
        }
    }
}
