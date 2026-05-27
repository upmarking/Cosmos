package com.cosmos.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmos.app.data.model.ChatMessage
import com.cosmos.app.data.model.Circle
import com.cosmos.app.data.model.Connection
import com.cosmos.app.data.model.EventRound
import com.cosmos.app.data.model.IntroRequest
import com.cosmos.app.data.model.IntroStatus
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MembershipTier
import com.cosmos.app.data.model.MessageType
import com.cosmos.app.data.model.NetworkEvent
import com.cosmos.app.data.model.Notification
import com.cosmos.app.data.repository.AuthRepository
import com.cosmos.app.data.repository.CirclePost
import com.cosmos.app.data.repository.CircleRepository
import com.cosmos.app.data.repository.ChatRepository
import com.cosmos.app.data.repository.EventRepository
import com.cosmos.app.data.repository.IntroRepository
import com.cosmos.app.data.repository.NotificationRepository
import com.cosmos.app.data.repository.ProfileRepository
import com.cosmos.app.data.repository.ServiceLocator
import com.cosmos.app.data.repository.SwipeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

// ── AuthViewModel ────────────────────────────────────────────────────────────
class AuthViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<Member?>(null)
    val currentUser: StateFlow<Member?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableSharedFlow<String>()
    val authError: SharedFlow<String> = _authError.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepo.currentUser.collectLatest { member ->
                _currentUser.value = member
            }
        }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.signIn(email, password)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _authError.emit(error.message ?: "Login failed")
                }
        }
    }

    fun signUp(email: String, password: String, name: String, type: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.signUp(email, password, name, type)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _authError.emit(error.message ?: "Registration failed")
                }
        }
    }

    fun saveOnboarding(member: Member, onSuccess: () -> Unit, imageBytes: ByteArray? = null) {
        viewModelScope.launch {
            _isLoading.value = true

            // Attempt photo upload — always returns success (empty URL if Storage unavailable)
            val avatarUrl = if (imageBytes != null) {
                authRepo.uploadProfileImage(authRepo.currentUserId ?: "", imageBytes)
                    .getOrDefault("") // never block onboarding on upload failure
            } else {
                ""
            }

            val memberToSave = if (avatarUrl.isNotEmpty()) member.copy(avatarUrl = avatarUrl) else member

            // Always proceed to save onboarding data regardless of upload outcome
            authRepo.saveOnboardingData(memberToSave)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _authError.emit(error.message ?: "Failed to save profile")
                }
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepo.signOut().onSuccess { onSuccess() }
        }
    }
}

// ── DiscoveryViewModel ────────────────────────────────────────────────────────
class DiscoveryViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val profileRepo: ProfileRepository = ServiceLocator.profileRepository,
    private val swipeRepo: SwipeRepository = ServiceLocator.swipeRepository
) : ViewModel() {

    private val _deck = MutableStateFlow<List<Member>>(emptyList())
    val deck: StateFlow<List<Member>> = _deck.asStateFlow()

    private val _connectionsLimitCount = MutableStateFlow(0)
    val connectionsLimitCount: StateFlow<Int> = _connectionsLimitCount.asStateFlow()

    init {
        loadDeck()
    }

    fun loadDeck() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            profileRepo.getDiscoveryDeck(uid).onSuccess { list ->
                _deck.value = list
            }
            swipeRepo.getMonthlyConnectionsCount(uid).onSuccess { count ->
                _connectionsLimitCount.value = count
            }
        }
    }

    fun swipeRight(likedId: String, onMatch: (Member) -> Unit) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            swipeRepo.recordSwipe(uid, likedId, "LIKE").onSuccess { isMatch ->
                if (isMatch) {
                    profileRepo.getProfile(likedId).onSuccess { member ->
                        onMatch(member)
                    }
                }
                loadDeck()
            }
        }
    }

    fun swipeLeft(likedId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            swipeRepo.recordSwipe(uid, likedId, "SKIP").onSuccess {
                loadDeck()
            }
        }
    }
}

// ── ChatViewModel ────────────────────────────────────────────────────────────
class ChatViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val chatRepo: ChatRepository = ServiceLocator.chatRepository,
    private val profileRepo: ProfileRepository = ServiceLocator.profileRepository,
    private val aiService: com.cosmos.app.data.repository.AiSummaryService = ServiceLocator.aiSummaryService
) : ViewModel() {

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _activeConnection = MutableStateFlow<Connection?>(null)
    val activeConnection: StateFlow<Connection?> = _activeConnection.asStateFlow()

    init {
        loadConnections()
    }

    private fun resolveId(connectionId: String, uid: String): String {
        return if (!connectionId.contains("_") && !connectionId.startsWith("intro_")) {
            if (uid < connectionId) "${uid}_${connectionId}" else "${connectionId}_${uid}"
        } else {
            connectionId
        }
    }

    private fun loadConnections() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            chatRepo.getConnections(uid).collectLatest { list ->
                // Map connection placeholders to real member profiles
                val fullyMapped = list.map { conn ->
                    val profile = profileRepo.getProfile(conn.member.id).getOrDefault(conn.member)
                    conn.copy(member = profile)
                }
                _connections.value = fullyMapped
            }
        }
    }

    fun selectConnection(connectionId: String) {
        val uid = authRepo.currentUserId ?: return
        val resolvedId = resolveId(connectionId, uid)
        viewModelScope.launch {
            chatRepo.getConnection(resolvedId, uid).collectLatest { conn ->
                if (conn != null) {
                    val profile = profileRepo.getProfile(conn.member.id).getOrDefault(conn.member)
                    _activeConnection.value = conn.copy(member = profile)
                }
            }
        }
        viewModelScope.launch {
            chatRepo.getMessages(resolvedId).collectLatest { list ->
                val ownMarked = list.map { it.copy(isOwn = it.senderId == uid) }
                _messages.value = ownMarked
            }
        }
    }

    fun sendMessage(connectionId: String, text: String) {
        val uid = authRepo.currentUserId ?: return
        val resolvedId = resolveId(connectionId, uid)
        viewModelScope.launch {
            chatRepo.sendMessage(resolvedId, uid, text, MessageType.TEXT)
        }
    }

    fun addLabel(connectionId: String, labels: List<String>) {
        val uid = authRepo.currentUserId ?: return
        val resolvedId = resolveId(connectionId, uid)
        viewModelScope.launch {
            chatRepo.updateCrmLabels(resolvedId, uid, labels)
        }
    }

    fun updateGoal(connectionId: String, goal: String) {
        val uid = authRepo.currentUserId ?: return
        val resolvedId = resolveId(connectionId, uid)
        viewModelScope.launch {
            chatRepo.updatePrivateGoal(resolvedId, uid, goal)
        }
    }

    fun generateMeetingAiSummary(connectionId: String, transcript: String) {
        val uid = authRepo.currentUserId ?: return
        val resolvedId = resolveId(connectionId, uid)
        viewModelScope.launch {
            aiService.generateMeetingSummary(transcript).onSuccess { summary ->
                chatRepo.saveAiSummary(resolvedId, summary)
            }
        }
    }
}

// ── EventViewModel ───────────────────────────────────────────────────────────
class EventViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val eventRepo: EventRepository = ServiceLocator.eventRepository
) : ViewModel() {

    private val _events = MutableStateFlow<List<NetworkEvent>>(emptyList())
    val events: StateFlow<List<NetworkEvent>> = _events.asStateFlow()

    private val _activeEvent = MutableStateFlow<NetworkEvent?>(null)
    val activeEvent: StateFlow<NetworkEvent?> = _activeEvent.asStateFlow()

    private val _eventParticipants = MutableStateFlow<List<Member>>(emptyList())
    val eventParticipants: StateFlow<List<Member>> = _eventParticipants.asStateFlow()

    private val _eventRounds = MutableStateFlow<List<EventRound>>(emptyList())
    val eventRounds: StateFlow<List<EventRound>> = _eventRounds.asStateFlow()

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            eventRepo.getEvents().collectLatest { list ->
                _events.value = list
            }
        }
    }

    fun selectEvent(eventId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            eventRepo.getEvent(eventId, uid).collectLatest { event ->
                _activeEvent.value = event
            }
        }
    }

    fun register(eventId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            eventRepo.registerForEvent(eventId, uid)
        }
    }

    fun loadEventParticipants(eventId: String) {
        viewModelScope.launch {
            eventRepo.getEventParticipants(eventId).collectLatest { list ->
                _eventParticipants.value = list
            }
        }
    }

    fun loadEventRounds(eventId: String) {
        viewModelScope.launch {
            eventRepo.getEventRounds(eventId).collectLatest { list ->
                _eventRounds.value = list
            }
        }
    }
}

// ── CommunityViewModel ───────────────────────────────────────────────────────
class CommunityViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val circleRepo: CircleRepository = ServiceLocator.circleRepository
) : ViewModel() {

    private val _circles = MutableStateFlow<List<Circle>>(emptyList())
    val circles: StateFlow<List<Circle>> = _circles.asStateFlow()

    private val _feedPosts = MutableStateFlow<List<CirclePost>>(emptyList())
    val feedPosts: StateFlow<List<CirclePost>> = _feedPosts.asStateFlow()

    private val _circleMembers = MutableStateFlow<List<Member>>(emptyList())
    val circleMembers: StateFlow<List<Member>> = _circleMembers.asStateFlow()

    init {
        loadCircles()
    }

    fun loadCircles() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            circleRepo.getCircles(uid).collectLatest { list ->
                _circles.value = list
            }
        }
    }

    fun selectCircle(circleId: String) {
        viewModelScope.launch {
            circleRepo.getCirclePosts(circleId).collectLatest { list ->
                _feedPosts.value = list
            }
        }
    }

    fun joinCircle(circleId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            circleRepo.joinCircle(circleId, uid).onSuccess {
                loadCircles()
                loadCircleMembers(circleId)
            }
        }
    }

    fun loadCircleMembers(circleId: String) {
        viewModelScope.launch {
            circleRepo.getCircleMembers(circleId).onSuccess { list ->
                _circleMembers.value = list
            }
        }
    }

    fun postUpdate(circleId: String, content: String) {
        viewModelScope.launch {
            authRepo.currentUser.firstOrNull()?.let { currentUser ->
                circleRepo.createCirclePost(
                    circleId = circleId,
                    authorId = currentUser.id,
                    authorName = currentUser.name,
                    authorAvatar = currentUser.avatarUrl,
                    content = content
                )
            }
        }
    }
}

// ── ProfileViewModel ──────────────────────────────────────────────────────────
class ProfileViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val profileRepo: ProfileRepository = ServiceLocator.profileRepository,
    private val notificationRepo: NotificationRepository = ServiceLocator.notificationRepository
) : ViewModel() {

    private val _selectedMember = MutableStateFlow<Member?>(null)
    val selectedMember: StateFlow<Member?> = _selectedMember.asStateFlow()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    init {
        loadNotifications()
    }

    private val _isConnectionEstablished = MutableStateFlow(false)
    val isConnectionEstablished: StateFlow<Boolean> = _isConnectionEstablished.asStateFlow()

    fun checkConnectionStatus(memberId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            ServiceLocator.chatRepository.getConnections(uid).collectLatest { list ->
                _isConnectionEstablished.value = list.any { it.member.id == memberId }
            }
        }
    }

    fun connectWithMember(memberId: String, onResult: (Boolean) -> Unit) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            val result = ServiceLocator.swipeRepository.recordSwipe(uid, memberId, "LIKE")
            onResult(result.getOrDefault(false))
        }
    }

    fun loadProfile(memberId: String) {
        viewModelScope.launch {
            profileRepo.getProfile(memberId).onSuccess { member ->
                _selectedMember.value = member
            }
        }
    }

    fun endorseSkill(memberId: String, skillName: String) {
        viewModelScope.launch {
            authRepo.currentUser.firstOrNull()?.let { currentUser ->
                profileRepo.endorseSkill(memberId, currentUser.name, skillName).onSuccess {
                    loadProfile(memberId)
                }
            }
        }
    }

    fun loadNotifications() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            notificationRepo.getNotifications(uid).collectLatest { list ->
                _notifications.value = list
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepo.markAsRead(notificationId)
        }
    }
}

// ── IntroViewModel ───────────────────────────────────────────────────────────
class IntroViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val introRepo: IntroRepository = ServiceLocator.introRepository,
    private val profileRepo: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    private val _introRequests = MutableStateFlow<List<IntroRequest>>(emptyList())
    val introRequests: StateFlow<List<IntroRequest>> = _introRequests.asStateFlow()

    private val _selectedRequest = MutableStateFlow<IntroRequest?>(null)
    val selectedRequest: StateFlow<IntroRequest?> = _selectedRequest.asStateFlow()

    init {
        loadIntroRequests()
    }

    fun loadIntroRequests() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            introRepo.getIntroRequestsForUser(uid).collectLatest { list ->
                val resolved = list.map { req ->
                    val fullReq = profileRepo.getProfile(req.requester.id).getOrDefault(req.requester)
                    val fullTarget = profileRepo.getProfile(req.target.id).getOrDefault(req.target)
                    val fullConnector = profileRepo.getProfile(req.connector.id).getOrDefault(req.connector)
                    req.copy(requester = fullReq, target = fullTarget, connector = fullConnector)
                }
                _introRequests.value = resolved
            }
        }
    }

    fun loadIntroRequest(requestId: String) {
        viewModelScope.launch {
            introRepo.getIntroRequest(requestId).onSuccess { req ->
                _selectedRequest.value = req
            }
        }
    }

    fun requestWarmIntro(targetId: String, connectorId: String, message: String, onSuccess: () -> Unit) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            introRepo.requestWarmIntro(uid, targetId, connectorId, message).onSuccess {
                onSuccess()
            }
        }
    }

    fun respondToRequest(requestId: String, status: IntroStatus, onSuccess: () -> Unit) {
        viewModelScope.launch {
            introRepo.respondToIntroRequest(requestId, status).onSuccess {
                onSuccess()
            }
        }
    }
}
