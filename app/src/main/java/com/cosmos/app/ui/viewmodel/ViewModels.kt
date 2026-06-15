package com.cosmos.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmos.app.data.model.ChatMessage
import com.cosmos.app.data.model.Circle
import com.cosmos.app.data.model.Connection
import com.cosmos.app.data.model.ConnectionProfileStatus
import com.cosmos.app.data.model.ConnectionRequest
import com.cosmos.app.data.model.ConnectionRequestStatus
import com.cosmos.app.data.model.EventRound
import com.cosmos.app.data.model.IntroRequest
import com.cosmos.app.data.model.IntroStatus
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MembershipTier
import com.cosmos.app.data.model.MessageType
import com.cosmos.app.data.model.NetworkEvent
import com.cosmos.app.data.model.Notification
import com.cosmos.app.data.model.CirclePost
import com.cosmos.app.data.repository.AuthRepository
import com.cosmos.app.data.repository.CircleRepository
import com.cosmos.app.data.repository.ChatRepository
import com.cosmos.app.data.repository.EventRepository
import com.cosmos.app.data.repository.IntroRepository
import com.cosmos.app.data.repository.NotificationRepository
import com.cosmos.app.data.repository.ProfileRepository
import com.cosmos.app.data.repository.ConnectionRequestRepository
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
        val previousUser = _currentUser.value
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

            // Optimistically update current user so UI reflects immediately
            _currentUser.value = memberToSave

            // Always proceed to save onboarding data regardless of upload outcome
            authRepo.saveOnboardingData(memberToSave)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _currentUser.value = previousUser
                    _authError.emit(error.message ?: "Failed to save profile")
                }
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepo.signOut().onSuccess { onSuccess() }
        }
    }

    fun updateProfile(member: Member, onSuccess: () -> Unit = {}) {
        val previousUser = _currentUser.value
        _currentUser.value = member
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.saveOnboardingData(member)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _currentUser.value = previousUser
                    _authError.emit(error.message ?: "Failed to update profile settings")
                }
        }
    }

    fun updatePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.updatePassword(currentPassword, newPassword)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    onError(error.message ?: "Failed to update password")
                }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.deleteAccount()
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _authError.emit(error.message ?: "Failed to delete account")
                }
        }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.resetPassword(email)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    onError(error.message ?: "Failed to send reset email")
                }
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
            chatRepo.markMessagesAsRead(resolvedId, uid)
        }
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
                chatRepo.markMessagesAsRead(resolvedId, uid)
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

    private val _isCreatingEvent = MutableStateFlow(false)
    val isCreatingEvent: StateFlow<Boolean> = _isCreatingEvent.asStateFlow()

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

    fun createEvent(event: NetworkEvent, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            _isCreatingEvent.value = true
            eventRepo.createEvent(event, uid).onSuccess {
                _isCreatingEvent.value = false
                onSuccess()
            }.onFailure { error ->
                _isCreatingEvent.value = false
                onError(error.message ?: "Failed to create event")
            }
        }
    }
}

// ── CommunityViewModel ───────────────────────────────────────────────────────
class CommunityViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val circleRepo: CircleRepository = ServiceLocator.circleRepository
) : ViewModel() {

    val currentUser = authRepo.currentUser

    private val _circles = MutableStateFlow<List<Circle>>(emptyList())
    val circles: StateFlow<List<Circle>> = _circles.asStateFlow()

    private val _feedPosts = MutableStateFlow<List<CirclePost>>(emptyList())
    val feedPosts: StateFlow<List<CirclePost>> = _feedPosts.asStateFlow()

    private val _circleMembers = MutableStateFlow<List<Member>>(emptyList())
    val circleMembers: StateFlow<List<Member>> = _circleMembers.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _likedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val likedPostIds: StateFlow<Set<String>> = _likedPostIds.asStateFlow()

    private val _isMembersLoading = MutableStateFlow(false)
    val isMembersLoading: StateFlow<Boolean> = _isMembersLoading.asStateFlow()

    private val _isPostsLoading = MutableStateFlow(false)
    val isPostsLoading: StateFlow<Boolean> = _isPostsLoading.asStateFlow()

    init {
        loadCircles()
    }

    fun loadCircles() {
        val uid = authRepo.currentUserId ?: return
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                circleRepo.getCircles(uid).collectLatest { list ->
                    _circles.value = list
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load circles"
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun refreshCircles() {
        _isRefreshing.value = true
        _errorMessage.value = null
        loadCircles()
    }

    fun selectCircle(circleId: String) {
        _isPostsLoading.value = true
        viewModelScope.launch {
            try {
                // Load liked posts for this circle
                val uid = authRepo.currentUserId ?: ""
                if (uid.isNotEmpty()) {
                    circleRepo.getLikedPostIds(circleId, uid).onSuccess { ids ->
                        _likedPostIds.value = ids
                    }
                }
                circleRepo.getCirclePosts(circleId).collectLatest { list ->
                    _feedPosts.value = list
                    _isPostsLoading.value = false
                }
            } catch (e: Exception) {
                _isPostsLoading.value = false
            }
        }
    }

    fun joinCircle(circleId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            circleRepo.joinCircle(circleId, uid).onSuccess {
                loadCircles()
                loadCircleMembers(circleId)
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to join circle"
            }
        }
    }

    fun leaveCircle(circleId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            circleRepo.leaveCircle(circleId, uid).onSuccess {
                loadCircles()
                loadCircleMembers(circleId)
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to leave circle"
            }
        }
    }

    fun createCircle(
        name: String,
        description: String,
        theme: String,
        tags: List<String>,
        isPrivate: Boolean,
        onSuccess: () -> Unit
    ) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            val currentMember = authRepo.currentUser.firstOrNull()
            val newCircle = Circle(
                id = "",
                name = name,
                description = description,
                theme = theme,
                tags = tags,
                isPrivate = isPrivate,
                memberCount = 1,
                isJoined = true,
                adminName = currentMember?.name ?: "Organizer",
                createdBy = uid
            )
            circleRepo.createCircle(newCircle, uid).onSuccess {
                loadCircles()
                onSuccess()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to create circle"
            }
        }
    }

    fun loadCircleMembers(circleId: String) {
        _isMembersLoading.value = true
        viewModelScope.launch {
            circleRepo.getCircleMembers(circleId).onSuccess { list ->
                _circleMembers.value = list
                _isMembersLoading.value = false
            }.onFailure {
                _isMembersLoading.value = false
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

    fun likePost(circleId: String, postId: String) {
        val uid = authRepo.currentUserId ?: return
        // Optimistic update
        _likedPostIds.value = _likedPostIds.value + postId
        _feedPosts.value = _feedPosts.value.map { post ->
            if (post.id == postId) post.copy(likesCount = post.likesCount + 1) else post
        }
        viewModelScope.launch {
            circleRepo.likePost(circleId, postId, uid).onFailure {
                // Revert optimistic update
                _likedPostIds.value = _likedPostIds.value - postId
                _feedPosts.value = _feedPosts.value.map { post ->
                    if (post.id == postId) post.copy(likesCount = (post.likesCount - 1).coerceAtLeast(0)) else post
                }
            }
        }
    }

    fun unlikePost(circleId: String, postId: String) {
        val uid = authRepo.currentUserId ?: return
        // Optimistic update
        _likedPostIds.value = _likedPostIds.value - postId
        _feedPosts.value = _feedPosts.value.map { post ->
            if (post.id == postId) post.copy(likesCount = (post.likesCount - 1).coerceAtLeast(0)) else post
        }
        viewModelScope.launch {
            circleRepo.unlikePost(circleId, postId, uid).onFailure {
                // Revert optimistic update
                _likedPostIds.value = _likedPostIds.value + postId
                _feedPosts.value = _feedPosts.value.map { post ->
                    if (post.id == postId) post.copy(likesCount = post.likesCount + 1) else post
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
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

    private val _connectionProfileStatus = MutableStateFlow(ConnectionProfileStatus.NONE)
    val connectionProfileStatus: StateFlow<ConnectionProfileStatus> = _connectionProfileStatus.asStateFlow()

    fun checkConnectionStatus(memberId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            ServiceLocator.connectionRequestRepository.getConnectionStatusFlow(uid, memberId)
                .collectLatest { status ->
                    _connectionProfileStatus.value = status
                }
        }
    }

    fun connectWithMember(memberId: String, message: String = "", onResult: (Boolean) -> Unit) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            val currentUser = authRepo.currentUser.firstOrNull()
            val targetUser = profileRepo.getProfile(memberId).getOrNull()
            ServiceLocator.connectionRequestRepository.sendConnectionRequest(
                senderId = uid,
                receiverId = memberId,
                senderName = currentUser?.name ?: "",
                senderHeadline = currentUser?.headline ?: "",
                senderAvatarUrl = currentUser?.avatarUrl ?: "",
                receiverName = targetUser?.name ?: "",
                receiverHeadline = targetUser?.headline ?: "",
                receiverAvatarUrl = targetUser?.avatarUrl ?: "",
                message = message
            ).onSuccess {
                _connectionProfileStatus.value = ConnectionProfileStatus.PENDING_SENT
                onResult(true)
            }.onFailure {
                onResult(false)
            }
        }
    }

    fun acceptConnectionFromProfile(memberId: String) {
        val uid = authRepo.currentUserId ?: return
        val requestId = "req_${memberId}_${uid}"
        viewModelScope.launch {
            ServiceLocator.connectionRequestRepository.acceptConnectionRequest(requestId)
                .onSuccess {
                    _connectionProfileStatus.value = ConnectionProfileStatus.CONNECTED
                }
        }
    }

    fun declineConnectionFromProfile(memberId: String) {
        val uid = authRepo.currentUserId ?: return
        val requestId = "req_${memberId}_${uid}"
        viewModelScope.launch {
            ServiceLocator.connectionRequestRepository.declineConnectionRequest(requestId)
                .onSuccess {
                    _connectionProfileStatus.value = ConnectionProfileStatus.NONE
                }
        }
    }

    fun withdrawConnectionRequest(memberId: String) {
        val uid = authRepo.currentUserId ?: return
        val requestId = "req_${uid}_${memberId}"
        viewModelScope.launch {
            ServiceLocator.connectionRequestRepository.withdrawConnectionRequest(requestId)
                .onSuccess {
                    _connectionProfileStatus.value = ConnectionProfileStatus.NONE
                }
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

// ── ConnectionViewModel ─────────────────────────────────────────────────────
class ConnectionViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val connectionRequestRepo: ConnectionRequestRepository = ServiceLocator.connectionRequestRepository,
    private val profileRepo: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    private val _incomingRequests = MutableStateFlow<List<ConnectionRequest>>(emptyList())
    val incomingRequests: StateFlow<List<ConnectionRequest>> = _incomingRequests.asStateFlow()

    private val _outgoingRequests = MutableStateFlow<List<ConnectionRequest>>(emptyList())
    val outgoingRequests: StateFlow<List<ConnectionRequest>> = _outgoingRequests.asStateFlow()

    private val _incomingCount = MutableStateFlow(0)
    val incomingCount: StateFlow<Int> = _incomingCount.asStateFlow()

    init {
        loadRequests()
    }

    private fun loadRequests() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            connectionRequestRepo.getIncomingRequests(uid).collectLatest { list ->
                _incomingRequests.value = list
            }
        }
        viewModelScope.launch {
            connectionRequestRepo.getOutgoingRequests(uid).collectLatest { list ->
                _outgoingRequests.value = list
            }
        }
        viewModelScope.launch {
            connectionRequestRepo.getIncomingRequestCount(uid).collectLatest { count ->
                _incomingCount.value = count
            }
        }
    }

    fun sendRequest(receiverId: String, message: String = "", onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            val currentUser = authRepo.currentUser.firstOrNull()
            val targetUser = profileRepo.getProfile(receiverId).getOrNull()
            connectionRequestRepo.sendConnectionRequest(
                senderId = uid,
                receiverId = receiverId,
                senderName = currentUser?.name ?: "",
                senderHeadline = currentUser?.headline ?: "",
                senderAvatarUrl = currentUser?.avatarUrl ?: "",
                receiverName = targetUser?.name ?: "",
                receiverHeadline = targetUser?.headline ?: "",
                receiverAvatarUrl = targetUser?.avatarUrl ?: "",
                message = message
            ).onSuccess {
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Failed to send request")
            }
        }
    }

    fun acceptRequest(requestId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            connectionRequestRepo.acceptConnectionRequest(requestId).onSuccess {
                onSuccess()
            }
        }
    }

    fun declineRequest(requestId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            connectionRequestRepo.declineConnectionRequest(requestId).onSuccess {
                onSuccess()
            }
        }
    }

    fun withdrawRequest(requestId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            connectionRequestRepo.withdrawConnectionRequest(requestId).onSuccess {
                onSuccess()
            }
        }
    }
}

// ── SearchViewModel ────────────────────────────────────────────────────────────
class SearchViewModel(
    private val profileRepo: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Member>>(emptyList())
    val searchResults: StateFlow<List<Member>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun updateQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().length >= 2) {
            executeSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun executeSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            profileRepo.searchProfiles(query).onSuccess { list ->
                _searchResults.value = list
            }
            _isSearching.value = false
        }
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }
}
