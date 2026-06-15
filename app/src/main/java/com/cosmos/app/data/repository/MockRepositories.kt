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
    val connectionRequestsFlow = MutableStateFlow<List<ConnectionRequest>>(emptyList())
    val eventRounds = mutableMapOf<String, List<EventRound>>()
    
    val circleMembersMap = mutableMapOf<String, MutableSet<String>>()
    val circlePostsMap = mutableMapOf<String, MutableStateFlow<List<CirclePost>>>()

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

        val circleMembersJson = JSONObject()
        circleMembersMap.forEach { (circleId, membersSet) ->
            val array = JSONArray()
            membersSet.forEach { array.put(it) }
            circleMembersJson.put(circleId, array)
        }
        editor.putString("circle_members_map", circleMembersJson.toString())

        val circlePostsJson = JSONObject()
        circlePostsMap.forEach { (circleId, flow) ->
            val array = JSONArray()
            flow.value.forEach { post -> array.put(circlePostToJson(post)) }
            circlePostsJson.put(circleId, array)
        }
        editor.putString("circle_posts_map", circlePostsJson.toString())

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

        circleMembersMap.clear()
        val circleMembersStr = p.getString("circle_members_map", null)
        if (circleMembersStr != null) {
            val json = JSONObject(circleMembersStr)
            json.keys().forEach { circleId ->
                val array = json.getJSONArray(circleId)
                val membersSet = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    membersSet.add(array.getString(i))
                }
                circleMembersMap[circleId] = membersSet
            }
        }

        circlePostsMap.clear()
        val circlePostsStr = p.getString("circle_posts_map", null)
        if (circlePostsStr != null) {
            val json = JSONObject(circlePostsStr)
            json.keys().forEach { circleId ->
                val array = json.getJSONArray(circleId)
                val list = mutableListOf<CirclePost>()
                for (i in 0 until array.length()) {
                    list.add(jsonToCirclePost(array.getJSONObject(i)))
                }
                circlePostsMap[circleId] = MutableStateFlow(list)
            }
        }

        // Update currentUserFlow
        currentUserFlow.value = currentUserId?.let { users[it] }
    }

    private fun seed() {
        val u1Id = "mock_user_sarah"
        val u1 = Member(
            id = u1Id, name = "Sarah Jenkins", email = "sarah@biosphere.com",
            headline = "Founder & CEO at BioSphere", role = "CEO", company = "BioSphere",
            avatarUrl = "", location = "Boston, MA",
            bio = "Building the future of sustainable food systems.",
            tags = listOf("ClimateTech", "Biotech", "Female Founder"),
            primaryUserType = "Founder", membershipTier = MembershipTier.FOUNDER,
            goalStatement = "Find a co-founder for climate tech startup",
            longTermVision = "Revolutionize sustainable agriculture through biotech innovation",
            lookingFor = listOf("Co-founders", "Investors", "Mentors"),
            isProfileComplete = true, isLinkedInConnected = true,
            connectionsCount = 14, eventsAttended = 5, followUpsCompleted = 8, introsMade = 6, goalsHit = 3
        )
        users[u1Id] = u1
        userPasswords["sarah@biosphere.com"] = "password"
        userEmailsToIds["sarah@biosphere.com"] = u1Id

        val u2Id = "mock_user_david"
        val u2 = Member(
            id = u2Id, name = "David Chen", email = "david@nexus.com",
            headline = "General Partner at Nexus Ventures", role = "GP", company = "Nexus Ventures",
            avatarUrl = "", location = "San Francisco, CA",
            bio = "Investing in early stage AI and enterprise SaaS. Former developer.",
            tags = listOf("AI/ML", "B2B SaaS", "VC"),
            primaryUserType = "Investor", membershipTier = MembershipTier.INNER_CIRCLE,
            goalStatement = "Discover promising AI-first startups to invest in",
            longTermVision = "Build a portfolio of transformative enterprise AI companies",
            lookingFor = listOf("Founders", "Strategic introductions"),
            isProfileComplete = true, isLinkedInConnected = true,
            connectionsCount = 42, eventsAttended = 12, followUpsCompleted = 20, introsMade = 15, goalsHit = 8
        )
        users[u2Id] = u2
        userPasswords["david@nexus.com"] = "password"
        userEmailsToIds["david@nexus.com"] = u2Id

        val u3Id = "mock_user_elena"
        val u3 = Member(
            id = u3Id, name = "Elena Rostova", email = "elena@cosmos.design",
            headline = "Lead Designer at Cosmos Studio", role = "Designer", company = "Cosmos Studio",
            avatarUrl = "", location = "New York, NY",
            bio = "Passionate about premium design systems and minimalist interfaces.",
            tags = listOf("UI/UX", "Product Design", "Creative"),
            primaryUserType = "Creator", membershipTier = MembershipTier.MEMBER,
            goalStatement = "Connect with founders who need world-class design",
            longTermVision = "Build a design agency focused on premium startup branding",
            lookingFor = listOf("Collaborators", "Clients", "Industry peers"),
            isProfileComplete = true
        )
        users[u3Id] = u3
        userPasswords["elena@cosmos.design"] = "password"
        userEmailsToIds["elena@cosmos.design"] = u3Id

        val u4Id = "mock_user_marcus"
        val u4 = Member(
            id = u4Id, name = "Marcus Vance", email = "marcus@scaleup.com",
            headline = "VP of Product at ScaleUp", role = "Product VP", company = "ScaleUp",
            avatarUrl = "", location = "Austin, TX",
            bio = "Scale specialist. Former 0-to-1 PM at Stripe and Airbnb.",
            tags = listOf("Product Management", "Growth", "Scaling"),
            primaryUserType = "Startup Operator", membershipTier = MembershipTier.EXPLORER,
            goalStatement = "Mentor early-stage product teams",
            longTermVision = "Transition from operator to advisor across multiple startups",
            lookingFor = listOf("Mentoring", "Hiring opportunities", "Business partners"),
            isProfileComplete = true
        )
        users[u4Id] = u4
        userPasswords["marcus@scaleup.com"] = "password"
        userEmailsToIds["marcus@scaleup.com"] = u4Id

        circlesFlow.value = listOf(
            Circle(id = "circle_1", name = "AI Builders & Founders",
                description = "A circle for developers and founders building next-gen generative AI applications.",
                memberCount = 0, theme = "AI & Technology",
                tags = listOf("AI/ML", "LLMs", "Tech"), adminName = "David Chen", createdBy = "mock_user_david"),
            Circle(id = "circle_2", name = "Cosmos Founders Club",
                description = "Official private circle for verified Cosmos startup founders to share tips and resources.",
                memberCount = 0, theme = "Entrepreneurship",
                tags = listOf("Founders", "Fundraising", "Cosmos"), adminName = "Sarah Jenkins", isPrivate = true, createdBy = "mock_user_sarah"),
            Circle(id = "circle_3", name = "Premium Design Collective",
                description = "Discussing aesthetics, premium typography, and UI/UX design trends.",
                memberCount = 0, theme = "Design",
                tags = listOf("Design", "UI/UX", "Aesthetics"), adminName = "Elena Rostova", createdBy = "mock_user_elena")
        )

        circleMembersMap["circle_1"] = mutableSetOf("mock_user_sarah", "mock_user_david", "mock_user_elena", "mock_user_marcus")
        circleMembersMap["circle_2"] = mutableSetOf("mock_user_sarah", "mock_user_david", "mock_user_marcus")
        circleMembersMap["circle_3"] = mutableSetOf("mock_user_sarah", "mock_user_david", "mock_user_elena", "mock_user_marcus")

        circlePostsMap["circle_1"] = MutableStateFlow(listOf(
            CirclePost(id = "p1_1", authorId = "mock_user_david", author = "David Chen", avatarUrl = "", content = "Welcome to the AI Builders circle! Share what you are working on. We've got a lot of exciting LLM projects coming up.", timeString = "2h ago", likesCount = 15, repliesCount = 4),
            CirclePost(id = "p1_2", authorId = "mock_user_sarah", author = "Sarah Jenkins", avatarUrl = "", content = "Great to be here! Working on using agentic workflows for bio-tech research.", timeString = "1h ago", likesCount = 8, repliesCount = 2)
        ))
        circlePostsMap["circle_2"] = MutableStateFlow(listOf(
            CirclePost(id = "p2_1", authorId = "mock_user_sarah", author = "Sarah Jenkins", avatarUrl = "", content = "Hello fellow founders! Remember that our goal is 10 high-quality connections per month. Keep it high-trust!", timeString = "3h ago", likesCount = 24, repliesCount = 5),
            CirclePost(id = "p2_2", authorId = "mock_user_marcus", author = "Marcus Vance", avatarUrl = "", content = "Welcome everyone. Let's use this circle to discuss fundraising, hiring, and scaling challenges.", timeString = "2h ago", likesCount = 12, repliesCount = 3)
        ))
        circlePostsMap["circle_3"] = MutableStateFlow(listOf(
            CirclePost(id = "p3_1", authorId = "mock_user_elena", author = "Elena Rostova", avatarUrl = "", content = "Typography is the unsung hero of premium interfaces. What's your favorite sans-serif font lately? I'm loving Outfit and Inter.", timeString = "4h ago", likesCount = 19, repliesCount = 6),
            CirclePost(id = "p3_2", authorId = "mock_user_sarah", author = "Sarah Jenkins", avatarUrl = "", content = "I really like Outfit, it gives that modern tech-organic vibe.", timeString = "3h ago", likesCount = 10, repliesCount = 1)
        ))

        val calendar = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val date1 = sdf.format(calendar.time)
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 2)
        val date2 = sdf.format(calendar.time)
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 3)
        val date3 = sdf.format(calendar.time)

        eventsFlow.value = listOf(
            NetworkEvent(id = "event_1", title = "Founder Speed Matchmaking",
                description = "Intentional 1-on-1 networking matches for founders and operators seeking co-founders.",
                date = date1, time = "6:00 PM UTC", location = "Cosmos Virtual Room",
                type = EventType.FOUNDER_MEETUP, participantCount = 18, maxParticipants = 30,
                tags = listOf("Speed Dating", "Co-Founder", "Interactive")),
            NetworkEvent(id = "event_2", title = "AI & B2B SaaS Showcase",
                description = "Presenting three breakout startups building AI-first enterprise workflows.",
                date = date2, time = "4:00 PM UTC", location = "Cosmos Hall",
                type = EventType.INDUSTRY_SPECIFIC, participantCount = 45, maxParticipants = 100,
                tags = listOf("AI", "B2B SaaS", "Showcase")),
            NetworkEvent(id = "event_3", title = "Cosmos Open Networking Hour",
                description = "Meet and chat with other members of the club in a relaxed, open format.",
                date = date3, time = "7:00 PM UTC", location = "Cosmos Lounge",
                type = EventType.OPEN_NETWORKING, participantCount = 22, maxParticipants = 50,
                tags = listOf("Networking", "Open", "Social"))
        )
    }

    // ── JSON serialization helpers ──────────────────────────────────────────

    fun memberToJson(member: Member): JSONObject {
        val json = JSONObject()
        json.put("id", member.id)
        json.put("name", member.name)
        json.put("email", member.email)
        json.put("headline", member.headline)
        json.put("role", member.role)
        json.put("company", member.company)
        json.put("avatarUrl", member.avatarUrl)
        json.put("location", member.location)
        json.put("bio", member.bio)
        json.put("tags", JSONArray().apply { member.tags.forEach { put(it) } })
        json.put("goalStatement", member.goalStatement)
        json.put("longTermVision", member.longTermVision)
        json.put("endorsedSkills", JSONArray().apply {
            member.endorsedSkills.forEach { skill ->
                put(JSONObject().apply {
                    put("name", skill.name)
                    put("count", skill.count)
                    put("endorsers", JSONArray().apply { skill.endorsers.forEach { put(it) } })
                })
            }
        })
        json.put("mutualConnectionsCount", member.mutualConnectionsCount)
        json.put("isLinkedInConnected", member.isLinkedInConnected)
        json.put("memberSince", member.memberSince)
        json.put("membershipTier", member.membershipTier.name)
        json.put("connectionsCount", member.connectionsCount)
        json.put("eventsAttended", member.eventsAttended)
        json.put("followUpsCompleted", member.followUpsCompleted)
        json.put("introsMade", member.introsMade)
        json.put("goalsHit", member.goalsHit)
        json.put("primaryUserType", member.primaryUserType)
        json.put("userRole", member.userRole.name)
        json.put("isProfileComplete", member.isProfileComplete)
        json.put("isRestricted", member.isRestricted)
        json.put("lookingFor", JSONArray().apply { member.lookingFor.forEach { put(it) } })
        json.put("availabilityPreferences", member.availabilityPreferences)
        json.put("notificationNewMatches", member.notificationNewMatches)
        json.put("notificationMessages", member.notificationMessages)
        json.put("notificationEventInvitations", member.notificationEventInvitations)
        json.put("notificationEventReminders", member.notificationEventReminders)
        json.put("notificationAiSummaries", member.notificationAiSummaries)
        json.put("notificationFollowUpReminders", member.notificationFollowUpReminders)
        json.put("notificationWarmIntroRequests", member.notificationWarmIntroRequests)
        json.put("notificationCommunityAnnouncements", member.notificationCommunityAnnouncements)
        json.put("notificationEndorsements", member.notificationEndorsements)
        json.put("privacyProfileVisibility", member.privacyProfileVisibility)
        json.put("privacyShowLinkedIn", member.privacyShowLinkedIn)
        json.put("privacyAllowWarmIntros", member.privacyAllowWarmIntros)
        json.put("privacyShowMutualConnections", member.privacyShowMutualConnections)
        json.put("privacyDataAnalytics", member.privacyDataAnalytics)
        json.put("monthlyConnectionLimit", member.monthlyConnectionLimit)
        json.put("matchingPreferences", JSONArray().apply { member.matchingPreferences.forEach { put(it) } })
        json.put("blockedUsers", JSONArray().apply { member.blockedUsers.forEach { put(it) } })
        return json
    }

    fun jsonToMember(json: JSONObject): Member {
        val tagsList = mutableListOf<String>()
        json.optJSONArray("tags")?.let { arr -> for (i in 0 until arr.length()) tagsList.add(arr.getString(i)) }
        
        val skillsList = mutableListOf<EndorsedSkill>()
        json.optJSONArray("endorsedSkills")?.let { arr ->
            for (i in 0 until arr.length()) {
                val sj = arr.getJSONObject(i)
                val endorsers = mutableListOf<String>()
                sj.optJSONArray("endorsers")?.let { ea -> for (j in 0 until ea.length()) endorsers.add(ea.getString(j)) }
                skillsList.add(EndorsedSkill(sj.getString("name"), sj.getInt("count"), endorsers))
            }
        }

        val lookingForList = mutableListOf<String>()
        json.optJSONArray("lookingFor")?.let { arr -> for (i in 0 until arr.length()) lookingForList.add(arr.getString(i)) }

        val matchingPrefsList = mutableListOf<String>()
        json.optJSONArray("matchingPreferences")?.let { arr -> for (i in 0 until arr.length()) matchingPrefsList.add(arr.getString(i)) }

        val blockedUsersList = mutableListOf<String>()
        json.optJSONArray("blockedUsers")?.let { arr -> for (i in 0 until arr.length()) blockedUsersList.add(arr.getString(i)) }
        
        val tierStr = json.optString("membershipTier", MembershipTier.EXPLORER.name)
        val tier = try { MembershipTier.valueOf(tierStr) } catch(e: Exception) { MembershipTier.EXPLORER }
        val roleStr = json.optString("userRole", UserRole.USER.name)
        val userRole = try { UserRole.valueOf(roleStr) } catch(e: Exception) { UserRole.USER }
        
        return Member(
            id = json.getString("id"), name = json.getString("name"),
            email = json.optString("email", ""),
            headline = json.optString("headline", ""), role = json.optString("role", ""),
            company = json.optString("company", ""), avatarUrl = json.optString("avatarUrl", ""),
            location = json.optString("location", ""), bio = json.optString("bio", ""),
            tags = tagsList, goalStatement = json.optString("goalStatement", ""),
            longTermVision = json.optString("longTermVision", ""),
            endorsedSkills = skillsList, mutualConnectionsCount = json.optInt("mutualConnectionsCount", 0),
            isLinkedInConnected = json.optBoolean("isLinkedInConnected", false),
            memberSince = json.optString("memberSince", ""), membershipTier = tier,
            connectionsCount = json.optInt("connectionsCount", 0),
            eventsAttended = json.optInt("eventsAttended", 0),
            followUpsCompleted = json.optInt("followUpsCompleted", 0),
            introsMade = json.optInt("introsMade", 0),
            goalsHit = json.optInt("goalsHit", 0),
            primaryUserType = json.optString("primaryUserType", ""),
            userRole = userRole,
            isProfileComplete = json.optBoolean("isProfileComplete", false),
            isRestricted = json.optBoolean("isRestricted", false),
            lookingFor = lookingForList,
            availabilityPreferences = json.optString("availabilityPreferences", ""),
            notificationNewMatches = json.optBoolean("notificationNewMatches", true),
            notificationMessages = json.optBoolean("notificationMessages", true),
            notificationEventInvitations = json.optBoolean("notificationEventInvitations", true),
            notificationEventReminders = json.optBoolean("notificationEventReminders", true),
            notificationAiSummaries = json.optBoolean("notificationAiSummaries", true),
            notificationFollowUpReminders = json.optBoolean("notificationFollowUpReminders", true),
            notificationWarmIntroRequests = json.optBoolean("notificationWarmIntroRequests", true),
            notificationCommunityAnnouncements = json.optBoolean("notificationCommunityAnnouncements", true),
            notificationEndorsements = json.optBoolean("notificationEndorsements", true),
            privacyProfileVisibility = json.optBoolean("privacyProfileVisibility", true),
            privacyShowLinkedIn = json.optBoolean("privacyShowLinkedIn", true),
            privacyAllowWarmIntros = json.optBoolean("privacyAllowWarmIntros", true),
            privacyShowMutualConnections = json.optBoolean("privacyShowMutualConnections", true),
            privacyDataAnalytics = json.optBoolean("privacyDataAnalytics", true),
            monthlyConnectionLimit = json.optInt("monthlyConnectionLimit", 10),
            matchingPreferences = matchingPrefsList,
            blockedUsers = blockedUsersList
        )
    }

    private fun connectionToJson(connection: Connection): JSONObject {
        val json = JSONObject()
        json.put("id", connection.id)
        json.put("member", memberToJson(connection.member))
        json.put("lastMessage", connection.lastMessage)
        json.put("lastMessageTime", connection.lastMessageTime)
        json.put("unreadCount", connection.unreadCount)
        json.put("labels", JSONArray().apply { connection.labels.forEach { put(it) } })
        json.put("privateGoal", connection.privateGoal)
        json.put("status", connection.status.name)
        return json
    }

    private fun jsonToConnection(json: JSONObject): Connection {
        val labelsList = mutableListOf<String>()
        json.optJSONArray("labels")?.let { arr -> for (i in 0 until arr.length()) labelsList.add(arr.getString(i)) }
        val statusStr = json.optString("status", ConnectionStatus.ACTIVE.name)
        val status = try { ConnectionStatus.valueOf(statusStr) } catch(e: Exception) { ConnectionStatus.ACTIVE }
        return Connection(
            id = json.getString("id"), member = jsonToMember(json.getJSONObject("member")),
            lastMessage = json.optString("lastMessage", ""),
            lastMessageTime = json.optString("lastMessageTime", ""),
            unreadCount = json.optInt("unreadCount", 0), labels = labelsList,
            privateGoal = json.optString("privateGoal", ""), status = status
        )
    }

    private fun chatMessageToJson(msg: ChatMessage): JSONObject {
        val json = JSONObject()
        json.put("id", msg.id); json.put("senderId", msg.senderId)
        json.put("text", msg.text); json.put("timestamp", msg.timestamp)
        json.put("isOwn", msg.isOwn); json.put("type", msg.type.name)
        json.put("isDeleted", msg.isDeleted)
        return json
    }

    private fun jsonToChatMessage(json: JSONObject): ChatMessage {
        val typeStr = json.optString("type", MessageType.TEXT.name)
        val type = try { MessageType.valueOf(typeStr) } catch(e: Exception) { MessageType.TEXT }
        return ChatMessage(
            id = json.getString("id"), senderId = json.getString("senderId"),
            text = json.getString("text"), timestamp = json.getString("timestamp"),
            isOwn = json.getBoolean("isOwn"), type = type,
            isDeleted = json.optBoolean("isDeleted", false)
        )
    }

    private fun circleToJson(circle: Circle): JSONObject {
        val json = JSONObject()
        json.put("id", circle.id); json.put("name", circle.name)
        json.put("description", circle.description); json.put("coverUrl", circle.coverUrl)
        json.put("theme", circle.theme)
        json.put("tags", JSONArray().apply { circle.tags.forEach { put(it) } })
        json.put("isPrivate", circle.isPrivate)
        json.put("adminName", circle.adminName)
        json.put("createdBy", circle.createdBy)
        // Note: isJoined, memberCount, isPending are computed dynamically from circleMembersMap
        return json
    }

    private fun circlePostToJson(post: CirclePost): JSONObject {
        val json = JSONObject()
        json.put("id", post.id)
        json.put("authorId", post.authorId)
        json.put("author", post.author)
        json.put("avatarUrl", post.avatarUrl)
        json.put("content", post.content)
        json.put("timeString", post.timeString)
        json.put("isPinned", post.isPinned)
        json.put("likesCount", post.likesCount)
        json.put("repliesCount", post.repliesCount)
        return json
    }

    private fun jsonToCirclePost(json: JSONObject): CirclePost {
        return CirclePost(
            id = json.getString("id"),
            authorId = json.getString("authorId"),
            author = json.getString("author"),
            avatarUrl = json.optString("avatarUrl", ""),
            content = json.getString("content"),
            timeString = json.getString("timeString"),
            isPinned = json.optBoolean("isPinned", false),
            likesCount = json.optInt("likesCount", 0),
            repliesCount = json.optInt("repliesCount", 0)
        )
    }

    private fun jsonToCircle(json: JSONObject): Circle {
        val tagsList = mutableListOf<String>()
        json.optJSONArray("tags")?.let { arr -> for (i in 0 until arr.length()) tagsList.add(arr.getString(i)) }
        return Circle(
            id = json.getString("id"), name = json.getString("name"),
            description = json.optString("description", ""), coverUrl = json.optString("coverUrl", ""),
            memberCount = 0, // Computed dynamically from circleMembersMap
            theme = json.optString("theme", ""),
            tags = tagsList,
            isJoined = false, // Computed dynamically based on current user's membership
            isPrivate = json.optBoolean("isPrivate", false),
            adminName = json.optString("adminName", ""),
            createdBy = json.optString("createdBy", ""),
            isPending = false // Computed dynamically
        )
    }

    private fun notificationToJson(notif: Notification): JSONObject {
        val json = JSONObject()
        json.put("id", notif.id); json.put("type", notif.type.name)
        json.put("title", notif.title); json.put("body", notif.body)
        json.put("timestamp", notif.timestamp); json.put("isRead", notif.isRead)
        json.put("actionId", notif.actionId)
        return json
    }

    private fun jsonToNotification(json: JSONObject): Notification {
        val typeStr = json.optString("type", NotificationType.MESSAGE.name)
        val type = try { NotificationType.valueOf(typeStr) } catch(e: Exception) { NotificationType.MESSAGE }
        return Notification(
            id = json.getString("id"), type = type, title = json.getString("title"),
            body = json.getString("body"), timestamp = json.getString("timestamp"),
            isRead = json.optBoolean("isRead", false), actionId = json.optString("actionId", "")
        )
    }

    private fun introRequestToJson(req: IntroRequest): JSONObject {
        val json = JSONObject()
        json.put("id", req.id); json.put("requester", memberToJson(req.requester))
        json.put("target", memberToJson(req.target)); json.put("connector", memberToJson(req.connector))
        json.put("message", req.message); json.put("status", req.status.name)
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
            message = json.getString("message"), status = status
        )
    }
}

// ── MockAuthRepository ────────────────────────────────────────────────────────
class MockAuthRepository : AuthRepository {
    override val currentUserId: String? get() = LocalStore.currentUserId
    override val currentUser: Flow<Member?> get() = LocalStore.currentUserFlow

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
            throw Exception("An account already exists with this email. Please sign in instead.")
        }
        val uid = "mock_user_${UUID.randomUUID()}"
        val newUser = Member(
            id = uid, name = name, email = normalizedEmail,
            headline = "$primaryType at Cosmos", role = primaryType, company = "Cosmos",
            avatarUrl = "", location = "San Francisco, CA",
            membershipTier = MembershipTier.EXPLORER, primaryUserType = primaryType,
            userRole = UserRole.ORGANIZER
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
        val updatedMember = member.copy(id = uid, isProfileComplete = true)
        LocalStore.users[uid] = updatedMember
        LocalStore.currentUserFlow.value = updatedMember
        LocalStore.save()
    }

    override suspend fun uploadProfileImage(uid: String, bytes: ByteArray): Result<String> = runCatching {
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    }

    override suspend fun resetPassword(email: String): Result<Unit> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        if (!LocalStore.userEmailsToIds.containsKey(normalizedEmail)) {
            throw Exception("No account found with this email.")
        }
        // In mock mode, just succeed silently
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val uid = LocalStore.currentUserId ?: throw IllegalStateException("Not logged in")
        val email = LocalStore.users[uid]?.email ?: ""
        LocalStore.users.remove(uid)
        if (email.isNotBlank()) {
            LocalStore.userPasswords.remove(email)
            LocalStore.userEmailsToIds.remove(email)
        }
        LocalStore.currentUserId = null
        LocalStore.currentUserFlow.value = null
        LocalStore.save()
    }

    override suspend fun updateEmail(newEmail: String): Result<Unit> = runCatching {
        val uid = LocalStore.currentUserId ?: throw IllegalStateException("Not logged in")
        val user = LocalStore.users[uid] ?: throw IllegalStateException("User not found")
        val oldEmail = user.email
        val normalizedNew = newEmail.trim().lowercase()
        if (oldEmail.isNotBlank()) {
            val pwd = LocalStore.userPasswords[oldEmail]
            LocalStore.userPasswords.remove(oldEmail)
            LocalStore.userEmailsToIds.remove(oldEmail)
            if (pwd != null) LocalStore.userPasswords[normalizedNew] = pwd
        }
        LocalStore.userEmailsToIds[normalizedNew] = uid
        LocalStore.users[uid] = user.copy(email = normalizedNew)
        LocalStore.currentUserFlow.value = LocalStore.users[uid]
        LocalStore.save()
    }

    override suspend fun updatePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val uid = LocalStore.currentUserId ?: throw IllegalStateException("Not logged in")
        val user = LocalStore.users[uid] ?: throw IllegalStateException("User not found")
        if (user.email.isNotBlank()) {
            val storedPassword = LocalStore.userPasswords[user.email]
            if (storedPassword != currentPassword) {
                throw Exception("Incorrect current password")
            }
            LocalStore.userPasswords[user.email] = newPassword
        }
        LocalStore.save()
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
            .filter { it.id != currentUserId && !swipedIds.contains(it.id) && !it.isRestricted }
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
                currentSkills[index] = skill.copy(count = skill.count + 1, endorsers = skill.endorsers + endorserName)
            }
        } else {
            currentSkills.add(EndorsedSkill(skillName, 1, listOf(endorserName)))
        }
        LocalStore.users[userId] = user.copy(endorsedSkills = currentSkills)
        val notification = Notification(
            id = UUID.randomUUID().toString(), type = NotificationType.ENDORSEMENT_RECEIVED,
            title = "New Endorsement", body = "$endorserName endorsed your $skillName skill.",
            timestamp = "Just now", isRead = false, actionId = userId
        )
        LocalStore.notificationsFlow.value = listOf(notification) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }

    override suspend fun getEndorsedSkills(userId: String): Result<List<EndorsedSkill>> = runCatching {
        LocalStore.users[userId]?.endorsedSkills ?: emptyList()
    }

    override suspend fun updateProfile(userId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        val user = LocalStore.users[userId] ?: throw IllegalArgumentException("User not found")
        var updated = user
        updates.forEach { (key, value) ->
            updated = when (key) {
                "name" -> updated.copy(name = value as String)
                "headline" -> updated.copy(headline = value as String)
                "bio" -> updated.copy(bio = value as String)
                "role" -> updated.copy(role = value as String)
                "company" -> updated.copy(company = value as String)
                "location" -> updated.copy(location = value as String)
                "goalStatement" -> updated.copy(goalStatement = value as String)
                "longTermVision" -> updated.copy(longTermVision = value as String)
                "avatarUrl" -> updated.copy(avatarUrl = value as String)
                "tags" -> updated.copy(tags = (value as? List<*>)?.filterIsInstance<String>() ?: updated.tags)
                "lookingFor" -> updated.copy(lookingFor = (value as? List<*>)?.filterIsInstance<String>() ?: updated.lookingFor)
                else -> updated
            }
        }
        LocalStore.users[userId] = updated
        if (userId == LocalStore.currentUserId) LocalStore.currentUserFlow.value = updated
        LocalStore.save()
    }

    override suspend fun searchProfiles(query: String, tags: List<String>, userType: String): Result<List<Member>> = runCatching {
        val queryLower = query.lowercase().trim()
        LocalStore.users.values.filter { member ->
            val matchesQuery = queryLower.isEmpty() || member.name.lowercase().contains(queryLower) ||
                member.headline.lowercase().contains(queryLower) || member.company.lowercase().contains(queryLower)
            val matchesTags = tags.isEmpty() || member.tags.any { t -> tags.any { it.equals(t, ignoreCase = true) } }
            val matchesType = userType.isEmpty() || member.primaryUserType.equals(userType, ignoreCase = true)
            matchesQuery && matchesTags && matchesType && !member.isRestricted
        }
    }

    override suspend fun getProfilesPaginated(lastDocId: String?, limit: Int): Result<List<Member>> = runCatching {
        val all = LocalStore.users.values.sortedBy { it.name }
        if (lastDocId == null) all.take(limit)
        else {
            val idx = all.indexOfFirst { it.id == lastDocId }
            if (idx >= 0) all.drop(idx + 1).take(limit) else all.take(limit)
        }
    }
}

// ── MockSwipeRepository ───────────────────────────────────────────────────────
class MockSwipeRepository : SwipeRepository {
    override suspend fun recordSwipe(likerId: String, likedId: String, action: String): Result<Boolean> = runCatching {
        LocalStore.swipes.add(mapOf("likerId" to likerId, "likedId" to likedId, "action" to action))
        if (action == "LIKE") {
            // Auto-swipe back in Mock Mode to enable testing match flows
            val reverseSwipe = mapOf("likerId" to likedId, "likedId" to likerId, "action" to "LIKE")
            if (LocalStore.swipes.none { it["likerId"] == likedId && it["likedId"] == likerId }) {
                LocalStore.swipes.add(reverseSwipe)
            }
            val connectionId = if (likerId < likedId) "${likerId}_${likedId}" else "${likedId}_${likerId}"
            val member2 = LocalStore.users[likedId] ?: SampleData.sampleMember
            val newConn = Connection(
                id = connectionId, member = member2,
                lastMessage = "You matched! Say hello.", lastMessageTime = "Now",
                status = ConnectionStatus.ACTIVE
            )
            if (LocalStore.connectionsFlow.value.none { it.id == connectionId }) {
                LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value + newConn
            }
            val member1 = LocalStore.users[likerId] ?: SampleData.sampleMember
            val notif1 = Notification(id = UUID.randomUUID().toString(), type = NotificationType.NEW_MATCH,
                title = "New Match! 🎉", body = "You matched with ${member2.name}. Start a conversation now!",
                timestamp = "Now", isRead = false, actionId = likedId)
            val notif2 = Notification(id = UUID.randomUUID().toString(), type = NotificationType.NEW_MATCH,
                title = "New Match! 🎉", body = "You matched with ${member1.name}. Start a conversation now!",
                timestamp = "Now", isRead = false, actionId = likerId)
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

    override suspend fun undoLastSwipe(userId: String): Result<Unit> = runCatching {
        if (LocalStore.swipes.isNotEmpty()) {
            val lastIdx = LocalStore.swipes.indexOfLast { it["likerId"] == userId }
            if (lastIdx >= 0) {
                val likedId = LocalStore.swipes[lastIdx]["likedId"] as? String
                LocalStore.swipes.removeAt(lastIdx)
                if (likedId != null) {
                    val connectionId = if (userId < likedId) "${userId}_${likedId}" else "${likedId}_${userId}"
                    LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.filter { it.id != connectionId }
                }
                LocalStore.save()
            }
        }
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
                    if (conn.id.startsWith("${userId}_")) conn.id.removePrefix("${userId}_")
                    else conn.id.removeSuffix("_${userId}")
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
                        if (conn.id.startsWith("${currentUserId}_")) conn.id.removePrefix("${currentUserId}_")
                        else conn.id.removeSuffix("_${currentUserId}")
                    }
                    conn.copy(member = LocalStore.users[otherId] ?: conn.member)
                } else null
            } else null
        }
    }

    override fun getMessages(connectionId: String): Flow<List<ChatMessage>> {
        return LocalStore.messagesMap.getOrPut(connectionId) { MutableStateFlow(emptyList()) }
    }

    override suspend fun sendMessage(connectionId: String, senderId: String, text: String, type: MessageType): Result<Unit> = runCatching {
        val flow = LocalStore.messagesMap.getOrPut(connectionId) { MutableStateFlow(emptyList()) }
        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(), senderId = senderId, text = text,
            timestamp = "Now", isOwn = senderId == LocalStore.currentUserId, type = type
        )
        flow.value = flow.value + newMessage
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
            if (conn.id == connectionId) conn.copy(
                lastMessage = if (type == MessageType.AI_SUMMARY) "AI Summary Ready" else text,
                lastMessageTime = "Now"
            ) else conn
        }
        LocalStore.save()

        // Simulate auto-reply
        if (senderId == LocalStore.currentUserId && type == MessageType.TEXT) {
            val otherId = resolveOtherMemberId(connectionId, senderId)
            if (otherId.isNotEmpty()) {
                scope.launch {
                    delay(1500)
                    val replyText = getAutoReply(otherId)
                    val replyMsg = ChatMessage(
                        id = UUID.randomUUID().toString(), senderId = otherId,
                        text = replyText, timestamp = "Now", isOwn = false, type = MessageType.TEXT
                    )
                    flow.value = flow.value + replyMsg
                    LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
                        if (conn.id == connectionId) conn.copy(lastMessage = replyText, lastMessageTime = "Now", unreadCount = conn.unreadCount + 1)
                        else conn
                    }
                    val senderProfile = LocalStore.users[otherId]
                    val notif = Notification(
                        id = UUID.randomUUID().toString(), type = NotificationType.MESSAGE,
                        title = senderProfile?.name ?: "New Message", body = replyText,
                        timestamp = "Now", isRead = false, actionId = otherId
                    )
                    LocalStore.notificationsFlow.value = listOf(notif) + LocalStore.notificationsFlow.value
                    LocalStore.save()
                }
            }
        }
    }

    override suspend fun updateCrmLabels(connectionId: String, userId: String, labels: List<String>): Result<Unit> = runCatching {
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
            if (conn.id == connectionId) conn.copy(labels = labels) else conn
        }
        LocalStore.save()
    }

    override suspend fun updatePrivateGoal(connectionId: String, userId: String, goal: String): Result<Unit> = runCatching {
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
            if (conn.id == connectionId) conn.copy(privateGoal = goal) else conn
        }
        LocalStore.save()
    }

    override suspend fun saveAiSummary(connectionId: String, summaryText: String): Result<Unit> = runCatching {
        sendMessage(connectionId, "system", summaryText, MessageType.AI_SUMMARY).getOrThrow()
    }

    override suspend fun markMessagesAsRead(connectionId: String, userId: String): Result<Unit> = runCatching {
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value.map { conn ->
            if (conn.id == connectionId) conn.copy(unreadCount = 0) else conn
        }
        LocalStore.save()
    }

    override suspend fun deleteMessage(connectionId: String, messageId: String): Result<Unit> = runCatching {
        val flow = LocalStore.messagesMap[connectionId] ?: return@runCatching
        flow.value = flow.value.map { msg ->
            if (msg.id == messageId) msg.copy(isDeleted = true, text = "This message was deleted") else msg
        }
        LocalStore.save()
    }

    override suspend fun searchMessages(connectionId: String, query: String): Result<List<ChatMessage>> = runCatching {
        val flow = LocalStore.messagesMap[connectionId]
        val queryLower = query.lowercase().trim()
        flow?.value?.filter { !it.isDeleted && it.text.lowercase().contains(queryLower) } ?: emptyList()
    }

    override fun getUnreadCountTotal(userId: String): Flow<Int> {
        return LocalStore.connectionsFlow.map { list ->
            list.filter { conn ->
                conn.id.startsWith("${userId}_") || conn.id.endsWith("_${userId}") || conn.id.startsWith("intro_")
            }.sumOf { it.unreadCount }
        }
    }

    private fun resolveOtherMemberId(connectionId: String, senderId: String): String {
        return if (connectionId.startsWith("intro_")) {
            val reqId = connectionId.removePrefix("intro_")
            val req = LocalStore.introRequestsFlow.value.find { it.id == reqId }
            if (req != null) { if (req.requester.id == senderId) req.target.id else req.requester.id } else ""
        } else {
            if (connectionId.startsWith("${senderId}_")) connectionId.removePrefix("${senderId}_")
            else if (connectionId.endsWith("_${senderId}")) connectionId.removeSuffix("_${senderId}")
            else ""
        }
    }

    private fun getAutoReply(otherId: String): String = when (otherId) {
        "mock_user_sarah" -> listOf("Hey! Great to connect.", "Hi! How's your week going?", "BioSphere is keeping me busy!").random()
        "mock_user_david" -> listOf("Thanks for reaching out!", "Hello! Let's schedule a call.", "We're active in B2B SaaS and AI.").random()
        "mock_user_elena" -> listOf("Hey! Love your profile.", "Hi! What do you think of the new design trends?", "Design aesthetics are key!").random()
        "mock_user_marcus" -> listOf("Scale specialist here.", "Hey! How are you scaling?", "Product market fit is hard but fun.").random()
        else -> "Hey! Thanks for matching. Let's catch up soon."
    }
}

// ── MockEventRepository ───────────────────────────────────────────────────────
class MockEventRepository : EventRepository {
    override fun getEvents(): Flow<List<NetworkEvent>> = LocalStore.eventsFlow

    override fun getEvent(eventId: String, currentUserId: String): Flow<NetworkEvent?> {
        return LocalStore.eventsFlow.map { list -> list.find { it.id == eventId } }
    }

    override suspend fun registerForEvent(eventId: String, userId: String): Result<Unit> = runCatching {
        LocalStore.eventsFlow.value = LocalStore.eventsFlow.value.map { event ->
            if (event.id == eventId) event.copy(isRegistered = true, participantCount = event.participantCount + 1)
            else event
        }
        LocalStore.save()
    }

    override suspend fun unregisterFromEvent(eventId: String, userId: String): Result<Unit> = runCatching {
        LocalStore.eventsFlow.value = LocalStore.eventsFlow.value.map { event ->
            if (event.id == eventId) event.copy(isRegistered = false, participantCount = (event.participantCount - 1).coerceAtLeast(0))
            else event
        }
        LocalStore.save()
    }

    override fun getEventRounds(eventId: String): Flow<List<EventRound>> {
        return LocalStore.eventsFlow.map { list ->
            val event = list.find { it.id == eventId }
            val uid = LocalStore.currentUserId
            val participants = LocalStore.users.values.toList()
            val others = participants.filter { it.id != uid }
            val rounds = mutableListOf<EventRound>()
            if (event?.isRegistered == true && uid != null) {
                val currentUser = LocalStore.users[uid]
                if (currentUser != null) {
                    others.getOrNull(0)?.let { rounds.add(EventRound("r1", "Round 1: Intros", 15, listOf(currentUser, it))) }
                    others.getOrNull(1)?.let { rounds.add(EventRound("r2", "Round 2: Collaborative Ideas", 15, listOf(currentUser, it))) }
                }
            } else {
                val p1 = participants.getOrNull(0); val p2 = participants.getOrNull(1)
                val p3 = participants.getOrNull(2); val p4 = participants.getOrNull(3)
                if (p1 != null && p2 != null) rounds.add(EventRound("r1", "Round 1: Intros", 15, listOf(p1, p2)))
                if (p3 != null && p4 != null) rounds.add(EventRound("r2", "Round 2: Collaborative Ideas", 15, listOf(p3, p4)))
            }
            rounds
        }
    }

    override suspend fun submitRoundFeedback(eventId: String, roundId: String, raterId: String, rateeId: String, rating: Int, feedbackText: String): Result<Unit> = runCatching { }

    override fun getEventParticipants(eventId: String): Flow<List<Member>> {
        return flow { emit(LocalStore.users.values.toList()) }
    }

    override suspend fun createEvent(event: NetworkEvent, creatorId: String): Result<String> = runCatching {
        val id = "event_${UUID.randomUUID()}"
        val newEvent = event.copy(id = id, createdBy = creatorId)
        LocalStore.eventsFlow.value = LocalStore.eventsFlow.value + newEvent
        LocalStore.save()
        id
    }

    override suspend fun updateEvent(eventId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        LocalStore.eventsFlow.value = LocalStore.eventsFlow.value.map { event ->
            if (event.id == eventId) {
                var updated = event
                updates.forEach { (key, value) ->
                    updated = when (key) {
                        "title" -> updated.copy(title = value as String)
                        "description" -> updated.copy(description = value as String)
                        "date" -> updated.copy(date = value as String)
                        "time" -> updated.copy(time = value as String)
                        "location" -> updated.copy(location = value as String)
                        "maxParticipants" -> updated.copy(maxParticipants = value as Int)
                        else -> updated
                    }
                }
                updated
            } else event
        }
        LocalStore.save()
    }

    override suspend fun deleteEvent(eventId: String): Result<Unit> = runCatching {
        LocalStore.eventsFlow.value = LocalStore.eventsFlow.value.filter { it.id != eventId }
        LocalStore.save()
    }

    override suspend fun searchEvents(query: String, type: EventType?, tags: List<String>): Result<List<NetworkEvent>> = runCatching {
        val queryLower = query.lowercase().trim()
        LocalStore.eventsFlow.value.filter { event ->
            val matchesQuery = queryLower.isEmpty() || event.title.lowercase().contains(queryLower) || event.description.lowercase().contains(queryLower)
            val matchesType = type == null || event.type == type
            val matchesTags = tags.isEmpty() || event.tags.any { t -> tags.any { it.equals(t, ignoreCase = true) } }
            matchesQuery && matchesType && matchesTags
        }
    }
}

// ── MockCircleRepository ──────────────────────────────────────────────────────
class MockCircleRepository : CircleRepository {

    // Track liked posts: Map<"circleId_postId", Set<userId>>
    private val likedPostsMap = mutableMapOf<String, MutableSet<String>>()

    override fun getCircles(currentUserId: String): Flow<List<Circle>> = LocalStore.circlesFlow.map { circles ->
        circles.map { circle ->
            val members = LocalStore.circleMembersMap[circle.id] ?: emptySet()
            val isMember = members.contains(currentUserId)
            circle.copy(
                isJoined = isMember,
                memberCount = members.size,
                isPending = false // Mock mode doesn't track pending separately
            )
        }
    }
    override suspend fun getCircle(circleId: String, currentUserId: String): Result<Circle> = runCatching {
        val circle = LocalStore.circlesFlow.value.find { it.id == circleId } ?: throw IllegalArgumentException("Circle not found")
        val members = LocalStore.circleMembersMap[circleId] ?: emptySet()
        val isMember = members.contains(currentUserId)
        circle.copy(
            isJoined = isMember,
            memberCount = members.size,
            isPending = false
        )
    }

    override suspend fun joinCircle(circleId: String, userId: String): Result<Unit> = runCatching {
        val circle = LocalStore.circlesFlow.value.find { it.id == circleId }
        val isPrivateCircle = circle?.isPrivate == true
        val circleName = circle?.name ?: ""

        if (!isPrivateCircle) {
            // Public circle: add member immediately
            val membersSet = LocalStore.circleMembersMap.getOrPut(circleId) { mutableSetOf() }
            membersSet.add(userId)
            // Trigger flow re-emission so getCircles recomputes isJoined/memberCount
            LocalStore.circlesFlow.value = LocalStore.circlesFlow.value.toList()
            LocalStore.save()
        } else {
            // Private circle: simulate approval after delay
            LocalStore.save()
            CoroutineScope(Dispatchers.Default).launch {
                delay(5000)
                val membersSet = LocalStore.circleMembersMap.getOrPut(circleId) { mutableSetOf() }
                membersSet.add(userId)
                // Trigger flow re-emission
                LocalStore.circlesFlow.value = LocalStore.circlesFlow.value.toList()
                val newNotif = Notification(
                    id = UUID.randomUUID().toString(),
                    type = NotificationType.COMMUNITY_ANNOUNCEMENT,
                    title = "Access Approved! 🎉",
                    body = "Your request to join $circleName has been approved by the moderator.",
                    timestamp = "Just now",
                    isRead = false,
                    actionId = circleId
                )
                LocalStore.notificationsFlow.value = listOf(newNotif) + LocalStore.notificationsFlow.value
                LocalStore.save()
            }
        }
    }

    override suspend fun leaveCircle(circleId: String, userId: String): Result<Unit> = runCatching {
        val membersSet = LocalStore.circleMembersMap[circleId]
        membersSet?.remove(userId)
        // Trigger flow re-emission so getCircles recomputes isJoined/memberCount
        LocalStore.circlesFlow.value = LocalStore.circlesFlow.value.toList()
        LocalStore.save()
    }

    override fun getCirclePosts(circleId: String): Flow<List<CirclePost>> {
        val flow = LocalStore.circlePostsMap.getOrPut(circleId) { MutableStateFlow(emptyList()) }
        return flow.map { list ->
            list.map { post ->
                val user = LocalStore.users[post.authorId]
                post.copy(
                    author = user?.name ?: post.author,
                    avatarUrl = user?.avatarUrl ?: post.avatarUrl
                )
            }
        }
    }

    override suspend fun createCirclePost(circleId: String, authorId: String, authorName: String, authorAvatar: String, content: String): Result<Unit> = runCatching {
        val flow = LocalStore.circlePostsMap.getOrPut(circleId) { MutableStateFlow(emptyList()) }
        val newPost = CirclePost(
            id = UUID.randomUUID().toString(),
            authorId = authorId,
            author = authorName,
            avatarUrl = authorAvatar,
            content = content,
            timeString = "Just now",
            likesCount = 0,
            repliesCount = 0
        )
        flow.value = listOf(newPost) + flow.value
        LocalStore.save()
    }

    override suspend fun deleteCirclePost(circleId: String, postId: String): Result<Unit> = runCatching {
        val flow = LocalStore.circlePostsMap[circleId] ?: return@runCatching
        flow.value = flow.value.filter { it.id != postId }
        LocalStore.save()
    }

    override suspend fun getCircleMembers(circleId: String): Result<List<Member>> = runCatching {
        val memberIds = LocalStore.circleMembersMap[circleId] ?: emptySet()
        memberIds.mapNotNull { LocalStore.users[it] }
    }

    override suspend fun createCircle(circle: Circle, creatorId: String): Result<String> = runCatching {
        val id = "circle_${UUID.randomUUID()}"
        val membersSet = LocalStore.circleMembersMap.getOrPut(id) { mutableSetOf() }
        membersSet.add(creatorId)
        val newCircle = circle.copy(id = id, createdBy = creatorId, memberCount = 1, isJoined = true)
        LocalStore.circlesFlow.value = LocalStore.circlesFlow.value + newCircle
        LocalStore.save()
        id
    }

    override suspend fun updateCircle(circleId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        LocalStore.circlesFlow.value = LocalStore.circlesFlow.value.map { c ->
            if (c.id == circleId) {
                var updated = c
                updates.forEach { (key, value) ->
                    updated = when (key) {
                        "name" -> updated.copy(name = value as String)
                        "description" -> updated.copy(description = value as String)
                        "theme" -> updated.copy(theme = value as String)
                        else -> updated
                    }
                }
                updated
            } else c
        }
        LocalStore.save()
    }

    override suspend fun deleteCircle(circleId: String): Result<Unit> = runCatching {
        LocalStore.circlesFlow.value = LocalStore.circlesFlow.value.filter { it.id != circleId }
        LocalStore.circlePostsMap.remove(circleId)
        LocalStore.circleMembersMap.remove(circleId)
        LocalStore.save()
    }

    override suspend fun searchCircles(query: String): Result<List<Circle>> = runCatching {
        val queryLower = query.lowercase().trim()
        LocalStore.circlesFlow.value.filter { c ->
            queryLower.isEmpty() || c.name.lowercase().contains(queryLower) ||
            c.description.lowercase().contains(queryLower) || c.theme.lowercase().contains(queryLower)
        }
    }

    override suspend fun likePost(circleId: String, postId: String, userId: String): Result<Unit> = runCatching {
        val key = "${circleId}_${postId}"
        val likers = likedPostsMap.getOrPut(key) { mutableSetOf() }
        if (likers.add(userId)) {
            val flow = LocalStore.circlePostsMap[circleId] ?: return@runCatching
            flow.value = flow.value.map { post ->
                if (post.id == postId) post.copy(likesCount = post.likesCount + 1) else post
            }
            LocalStore.save()
        }
    }

    override suspend fun unlikePost(circleId: String, postId: String, userId: String): Result<Unit> = runCatching {
        val key = "${circleId}_${postId}"
        val likers = likedPostsMap[key] ?: return@runCatching
        if (likers.remove(userId)) {
            val flow = LocalStore.circlePostsMap[circleId] ?: return@runCatching
            flow.value = flow.value.map { post ->
                if (post.id == postId) post.copy(likesCount = (post.likesCount - 1).coerceAtLeast(0)) else post
            }
            LocalStore.save()
        }
    }

    override suspend fun getLikedPostIds(circleId: String, userId: String): Result<Set<String>> = runCatching {
        likedPostsMap.entries
            .filter { it.key.startsWith("${circleId}_") && it.value.contains(userId) }
            .map { it.key.removePrefix("${circleId}_") }
            .toSet()
    }
}

// ── MockNotificationRepository ────────────────────────────────────────────────
class MockNotificationRepository : NotificationRepository {
    override fun getNotifications(userId: String): Flow<List<Notification>> = LocalStore.notificationsFlow

    override suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        LocalStore.notificationsFlow.value = LocalStore.notificationsFlow.value.map { n ->
            if (n.id == notificationId) n.copy(isRead = true) else n
        }
        LocalStore.save()
    }

    override suspend fun markAllAsRead(userId: String): Result<Unit> = runCatching {
        LocalStore.notificationsFlow.value = LocalStore.notificationsFlow.value.map { it.copy(isRead = true) }
        LocalStore.save()
    }

    override suspend fun createNotification(userId: String, type: NotificationType, title: String, body: String, actionId: String): Result<Unit> = runCatching {
        val newNotif = Notification(
            id = UUID.randomUUID().toString(), type = type, title = title,
            body = body, timestamp = "Just now", isRead = false, actionId = actionId
        )
        LocalStore.notificationsFlow.value = listOf(newNotif) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }

    override suspend fun deleteNotification(notificationId: String): Result<Unit> = runCatching {
        LocalStore.notificationsFlow.value = LocalStore.notificationsFlow.value.filter { it.id != notificationId }
        LocalStore.save()
    }

    override fun getUnreadCount(userId: String): Flow<Int> {
        return LocalStore.notificationsFlow.map { list -> list.count { !it.isRead } }
    }
}

// ── MockIntroRepository ───────────────────────────────────────────────────────
class MockIntroRepository : IntroRepository {
    override suspend fun requestWarmIntro(requesterId: String, targetId: String, connectorId: String, message: String): Result<Unit> = runCatching {
        val reqUser = LocalStore.users[requesterId] ?: SampleData.sampleMember
        val targetUser = LocalStore.users[targetId] ?: SampleData.sampleMember
        val connUser = LocalStore.users[connectorId] ?: SampleData.sampleMember
        val requestId = "req_${UUID.randomUUID()}"
        val newRequest = IntroRequest(id = requestId, requester = reqUser, target = targetUser, connector = connUser, message = message, status = IntroStatus.PENDING)
        LocalStore.introRequestsFlow.value = LocalStore.introRequestsFlow.value + newRequest
        val notification = Notification(
            id = UUID.randomUUID().toString(), type = NotificationType.WARM_INTRO_REQUEST,
            title = "Introduction Request", body = "${reqUser.name} is requesting an introduction through you.",
            timestamp = "Now", isRead = false, actionId = requestId
        )
        LocalStore.notificationsFlow.value = listOf(notification) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }

    override suspend fun getIntroRequest(requestId: String): Result<IntroRequest> = runCatching {
        LocalStore.introRequestsFlow.value.find { it.id == requestId } ?: throw IllegalArgumentException("Request not found")
    }

    override suspend fun respondToIntroRequest(requestId: String, status: IntroStatus): Result<Unit> = runCatching {
        LocalStore.introRequestsFlow.value = LocalStore.introRequestsFlow.value.map { req ->
            if (req.id == requestId) req.copy(status = status) else req
        }
        val req = LocalStore.introRequestsFlow.value.find { it.id == requestId } ?: return@runCatching
        val typeStr = if (status == IntroStatus.ACCEPTED) NotificationType.WARM_INTRO_ACCEPTED else NotificationType.WARM_INTRO_DECLINED
        val title = if (status == IntroStatus.ACCEPTED) "Intro Request Accepted!" else "Intro Request Declined"
        val body = if (status == IntroStatus.ACCEPTED) "Your introduction request has been accepted by ${req.connector.name}. Say hello!"
        else "Your introduction request was declined by ${req.connector.name}."
        val notification = Notification(id = UUID.randomUUID().toString(), type = typeStr, title = title, body = body, timestamp = "Now", isRead = false, actionId = req.target.id)
        LocalStore.notificationsFlow.value = listOf(notification) + LocalStore.notificationsFlow.value
        if (status == IntroStatus.ACCEPTED) {
            val connectionId = "intro_${requestId}"
            val connection = Connection(id = connectionId, member = req.target, lastMessage = "3-Way Intro started! Say hello.", lastMessageTime = "Now", status = ConnectionStatus.INTRO_REQUESTED)
            LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value + connection
        }
        LocalStore.save()
    }

    override fun getIntroRequestsForUser(userId: String): Flow<List<IntroRequest>> {
        return LocalStore.introRequestsFlow.map { list -> list.filter { it.connector.id == userId } }
    }
}

// ── MockConnectionRequestRepository ───────────────────────────────────────────
class MockConnectionRequestRepository : ConnectionRequestRepository {

    override suspend fun sendConnectionRequest(
        senderId: String, receiverId: String,
        senderName: String, senderHeadline: String, senderAvatarUrl: String,
        receiverName: String, receiverHeadline: String, receiverAvatarUrl: String,
        message: String
    ): Result<Unit> = runCatching {
        // Check for existing pending request
        val existing = LocalStore.connectionRequestsFlow.value.find {
            it.senderId == senderId && it.receiverId == receiverId && it.status == ConnectionRequestStatus.PENDING
        }
        if (existing != null) throw IllegalStateException("Connection request already pending")

        // Check if reverse request exists — auto accept
        val reverse = LocalStore.connectionRequestsFlow.value.find {
            it.senderId == receiverId && it.receiverId == senderId && it.status == ConnectionRequestStatus.PENDING
        }
        if (reverse != null) {
            acceptConnectionRequest(reverse.id).getOrThrow()
            return@runCatching
        }

        // Check if already connected
        val connectionId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
        val alreadyConnected = LocalStore.connectionsFlow.value.any { it.id == connectionId }
        if (alreadyConnected) throw IllegalStateException("Already connected")

        val requestId = "req_${senderId}_${receiverId}"
        val request = ConnectionRequest(
            id = requestId, senderId = senderId, receiverId = receiverId,
            senderName = senderName, senderHeadline = senderHeadline, senderAvatarUrl = senderAvatarUrl,
            receiverName = receiverName, receiverHeadline = receiverHeadline, receiverAvatarUrl = receiverAvatarUrl,
            message = message, status = ConnectionRequestStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        LocalStore.connectionRequestsFlow.value = LocalStore.connectionRequestsFlow.value + request

        val notification = Notification(
            id = UUID.randomUUID().toString(), type = NotificationType.CONNECTION_REQUEST,
            title = "New Connection Request",
            body = "$senderName wants to connect with you${if (message.isNotBlank()) ": \"$message\"" else ""}",
            timestamp = "Now", isRead = false, actionId = senderId
        )
        LocalStore.notificationsFlow.value = listOf(notification) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }

    override suspend fun acceptConnectionRequest(requestId: String): Result<Unit> = runCatching {
        val request = LocalStore.connectionRequestsFlow.value.find { it.id == requestId }
            ?: throw IllegalArgumentException("Request not found")

        // Update request status
        LocalStore.connectionRequestsFlow.value = LocalStore.connectionRequestsFlow.value.map {
            if (it.id == requestId) it.copy(status = ConnectionRequestStatus.ACCEPTED) else it
        }

        // Create connection
        val senderId = request.senderId
        val receiverId = request.receiverId
        val connectionId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
        val senderMember = LocalStore.users[senderId] ?: Member(
            id = senderId, name = request.senderName, headline = request.senderHeadline,
            role = "", company = "", avatarUrl = request.senderAvatarUrl
        )
        val receiverMember = LocalStore.users[receiverId] ?: Member(
            id = receiverId, name = request.receiverName, headline = request.receiverHeadline,
            role = "", company = "", avatarUrl = request.receiverAvatarUrl
        )

        // The connection should use the OTHER user as the member
        val uid = LocalStore.currentUserId
        val otherMember = if (uid == senderId) receiverMember else senderMember
        val connection = Connection(
            id = connectionId, member = otherMember,
            lastMessage = "Connection established! Say hello.",
            lastMessageTime = "Now", status = ConnectionStatus.ACTIVE
        )
        LocalStore.connectionsFlow.value = LocalStore.connectionsFlow.value + connection

        // Notification to sender
        val notifSender = Notification(
            id = UUID.randomUUID().toString(), type = NotificationType.CONNECTION_ACCEPTED,
            title = "Connection Accepted! \uD83C\uDF89",
            body = "Your connection request was accepted. Start a conversation now!",
            timestamp = "Now", isRead = false, actionId = receiverId
        )
        // Notification to receiver
        val notifReceiver = Notification(
            id = UUID.randomUUID().toString(), type = NotificationType.CONNECTION_ACCEPTED,
            title = "Connection Established! \uD83C\uDF89",
            body = "You are now connected with ${request.senderName}. Start a conversation!",
            timestamp = "Now", isRead = false, actionId = senderId
        )
        LocalStore.notificationsFlow.value = listOf(notifSender, notifReceiver) + LocalStore.notificationsFlow.value
        LocalStore.save()
    }

    override suspend fun declineConnectionRequest(requestId: String): Result<Unit> = runCatching {
        LocalStore.connectionRequestsFlow.value = LocalStore.connectionRequestsFlow.value.map {
            if (it.id == requestId) it.copy(status = ConnectionRequestStatus.DECLINED) else it
        }
        LocalStore.save()
    }

    override suspend fun withdrawConnectionRequest(requestId: String): Result<Unit> = runCatching {
        LocalStore.connectionRequestsFlow.value = LocalStore.connectionRequestsFlow.value.map {
            if (it.id == requestId) it.copy(status = ConnectionRequestStatus.WITHDRAWN) else it
        }
        LocalStore.save()
    }

    override fun getIncomingRequests(userId: String): Flow<List<ConnectionRequest>> {
        return LocalStore.connectionRequestsFlow.map { list ->
            list.filter { it.receiverId == userId && it.status == ConnectionRequestStatus.PENDING }
                .sortedByDescending { it.createdAt }
        }
    }

    override fun getOutgoingRequests(userId: String): Flow<List<ConnectionRequest>> {
        return LocalStore.connectionRequestsFlow.map { list ->
            list.filter { it.senderId == userId && it.status == ConnectionRequestStatus.PENDING }
                .sortedByDescending { it.createdAt }
        }
    }

    override suspend fun getConnectionStatus(
        currentUserId: String, otherUserId: String
    ): Result<ConnectionProfileStatus> = runCatching {
        val connectionId = if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        if (LocalStore.connectionsFlow.value.any { it.id == connectionId }) {
            return@runCatching ConnectionProfileStatus.CONNECTED
        }
        val sentPending = LocalStore.connectionRequestsFlow.value.any {
            it.senderId == currentUserId && it.receiverId == otherUserId && it.status == ConnectionRequestStatus.PENDING
        }
        if (sentPending) return@runCatching ConnectionProfileStatus.PENDING_SENT
        val receivedPending = LocalStore.connectionRequestsFlow.value.any {
            it.senderId == otherUserId && it.receiverId == currentUserId && it.status == ConnectionRequestStatus.PENDING
        }
        if (receivedPending) return@runCatching ConnectionProfileStatus.PENDING_RECEIVED
        ConnectionProfileStatus.NONE
    }

    override fun getConnectionStatusFlow(
        currentUserId: String, otherUserId: String
    ): Flow<ConnectionProfileStatus> {
        val connectionId = if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        return kotlinx.coroutines.flow.combine(
            LocalStore.connectionsFlow,
            LocalStore.connectionRequestsFlow
        ) { connections, requests ->
            if (connections.any { it.id == connectionId }) {
                ConnectionProfileStatus.CONNECTED
            } else {
                val sentPending = requests.any {
                    it.senderId == currentUserId && it.receiverId == otherUserId && it.status == ConnectionRequestStatus.PENDING
                }
                if (sentPending) ConnectionProfileStatus.PENDING_SENT
                else {
                    val receivedPending = requests.any {
                        it.senderId == otherUserId && it.receiverId == currentUserId && it.status == ConnectionRequestStatus.PENDING
                    }
                    if (receivedPending) ConnectionProfileStatus.PENDING_RECEIVED
                    else ConnectionProfileStatus.NONE
                }
            }
        }
    }

    override fun getIncomingRequestCount(userId: String): Flow<Int> {
        return LocalStore.connectionRequestsFlow.map { list ->
            list.count { it.receiverId == userId && it.status == ConnectionRequestStatus.PENDING }
        }
    }
}
