package app.cosmos.com.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmos.com.data.model.ChatMessage
import app.cosmos.com.data.model.Circle
import app.cosmos.com.data.model.Connection
import app.cosmos.com.data.model.ConnectionStatus
import app.cosmos.com.data.model.ConnectionProfileStatus
import app.cosmos.com.data.model.ConnectionRequest
import app.cosmos.com.data.model.ConnectionRequestStatus
import app.cosmos.com.data.model.EventRound
import kotlinx.coroutines.Job
import app.cosmos.com.data.model.EventType
import app.cosmos.com.data.model.IntroRequest
import app.cosmos.com.data.model.IntroStatus
import app.cosmos.com.data.model.Member
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.model.MessageType
import app.cosmos.com.data.model.NetworkEvent
import app.cosmos.com.data.model.Notification
import app.cosmos.com.data.model.CirclePost
import app.cosmos.com.data.model.CirclePostReply
import app.cosmos.com.data.model.SocialPost
import app.cosmos.com.data.model.SocialPostReply
import app.cosmos.com.data.repository.AuthRepository
import app.cosmos.com.data.repository.CircleRepository
import app.cosmos.com.data.repository.SocialRepository
import app.cosmos.com.data.repository.FirestoreSeedService

import app.cosmos.com.data.repository.ChatRepository
import app.cosmos.com.data.repository.EventRepository
import app.cosmos.com.data.repository.IntroRepository
import app.cosmos.com.data.repository.NotificationRepository
import app.cosmos.com.data.repository.ProfileRepository
import app.cosmos.com.data.repository.ConnectionRequestRepository
import app.cosmos.com.data.repository.ServiceLocator
import app.cosmos.com.data.repository.SwipeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Pricing filter options for the Events screen */
enum class PricingFilter(val label: String) {
    ALL("All"),
    FREE_ONLY("Free"),
    PAID_ONLY("Paid")
}

// ── AuthViewModel ────────────────────────────────────────────────────────────
class AuthViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    // Backing store for local optimistic updates (e.g. profile edits before Firestore confirms).
    // When non-null, this value is preferred over the Firestore-sourced value.
    private val _localOverride = MutableStateFlow<Member?>(null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableSharedFlow<String>()
    val authError: SharedFlow<String> = _authError.asSharedFlow()

    // authRepo.currentUser is already a HOT shared flow (shareIn Eagerly, replay=1)
    // at the repository level. We combine with _localOverride so that optimistic
    // profile saves show up instantly in the UI even before Firestore confirms.
    val currentUser: StateFlow<Member?> = authRepo.currentUser
        .combine(_localOverride) { remote, local -> local ?: remote }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

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

    fun saveOnboarding(
        member: Member,
        onSuccess: () -> Unit,
        imageBytes: ByteArray? = null,
        optimisticNavigation: Boolean = true
    ) {
        val previousUser = _localOverride.value ?: currentUser.value
        
        // Optimistically update the avatarUrl locally using a base64 encoding if a new image was captured/selected,
        // so that the UI updates instantly even if storage upload takes time.
        // Also set isProfileComplete to true since they are completing/saving onboarding data.
        val tempAvatarMember = if (imageBytes != null) {
            try {
                val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                member.copy(avatarUrl = "data:image/jpeg;base64,$base64")
            } catch (e: Exception) {
                member
            }
        } else {
            member
        }

        // Optimistically update current user so UI reflects immediately
        _localOverride.value = tempAvatarMember

        if (optimisticNavigation) {
            // Fire onSuccess (navigate back/dismiss) immediately for instant UX feedback
            onSuccess()
        } else {
            _isLoading.value = true
        }

        // Perform photo upload and database sync
        viewModelScope.launch {
            try {
                val avatarUrl = if (imageBytes != null) {
                    authRepo.uploadProfileImage(authRepo.currentUserId ?: "", imageBytes)
                        .getOrDefault("")
                } else {
                    ""
                }

                val memberToSave = if (avatarUrl.isNotEmpty()) tempAvatarMember.copy(avatarUrl = avatarUrl) else tempAvatarMember
                
                // Update override with final storage URL
                _localOverride.value = memberToSave

                authRepo.saveOnboardingData(memberToSave)
                    .onSuccess {
                        // Clear local override once Firestore confirms the write —
                        // the Firestore snapshot listener will now be the source of truth.
                        _localOverride.value = null
                        if (!optimisticNavigation) {
                            _isLoading.value = false
                            onSuccess()
                        }
                    }
                    .onFailure { error ->
                        // Since Firestore has built-in offline caching/syncing, we don't revert the optimistic update
                        // because it will retry syncing once online.
                        if (!optimisticNavigation) {
                            _isLoading.value = false
                            _authError.emit(error.message ?: "Failed to save details")
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!optimisticNavigation) {
                    _isLoading.value = false
                    _authError.emit(e.message ?: "Failed to save details")
                }
            }
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepo.signOut().onSuccess { onSuccess() }
        }
    }

    fun updateProfile(member: Member, onSuccess: () -> Unit = {}) {
        val previousUser = _localOverride.value ?: currentUser.value
        _localOverride.value = member
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.saveOnboardingData(member)
                .onSuccess {
                    _isLoading.value = false
                    // Clear local override; Firestore snapshot listener now owns state
                    _localOverride.value = null
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _localOverride.value = previousUser
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

    private val _resetEmail = MutableStateFlow<String?>(null)
    val resetEmail: StateFlow<String?> = _resetEmail.asStateFlow()

    fun verifyResetCode(oobCode: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.verifyPasswordResetCode(oobCode)
                .onSuccess { email ->
                    _isLoading.value = false
                    _resetEmail.value = email
                    onSuccess(email)
                }
                .onFailure { error ->
                    _isLoading.value = false
                    onError(error.message ?: "Invalid or expired reset link")
                }
        }
    }

    fun confirmPasswordReset(oobCode: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.confirmPasswordReset(oobCode, newPassword)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _isLoading.value = false
                    onError(error.message ?: "Failed to reset password")
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

    val currentUser = authRepo.currentUser

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
                    profileRepo.getProfile(likedId, fetchSkills = false).onSuccess { member ->
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
    private val aiService: app.cosmos.com.data.repository.AiSummaryService = ServiceLocator.aiSummaryService
) : ViewModel() {

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _activeConnection = MutableStateFlow<Connection?>(null)
    val activeConnection: StateFlow<Connection?> = _activeConnection.asStateFlow()

    private var selectConnectionJob: Job? = null
    private var messagesCollectionJob: Job? = null

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
                // Map connection placeholders to real member profiles in parallel
                val fullyMapped = coroutineScope {
                    list.map { conn ->
                        async {
                            val profile = profileRepo.getProfile(conn.member.id, fetchSkills = false).getOrDefault(conn.member)
                            conn.copy(member = profile)
                        }
                    }.awaitAll()
                }
                _connections.value = fullyMapped
            }
        }
    }

    fun selectConnection(connectionId: String) {
        val uid = authRepo.currentUserId ?: return
        val resolvedId = resolveId(connectionId, uid)
        
        // Reset states to avoid showing stale data during loading
        _activeConnection.value = null
        _messages.value = emptyList()
        
        selectConnectionJob?.cancel()
        messagesCollectionJob?.cancel()

        viewModelScope.launch {
            chatRepo.markMessagesAsRead(resolvedId, uid)
        }
        
        selectConnectionJob = viewModelScope.launch {
            chatRepo.getConnection(resolvedId, uid).collectLatest { conn ->
                if (conn != null) {
                    val profile = profileRepo.getProfile(conn.member.id, fetchSkills = false).getOrDefault(conn.member)
                    _activeConnection.value = conn.copy(member = profile)
                } else {
                    // Falls back to creating a local draft Connection if the document doesn't exist yet
                    if (resolvedId.contains("_") && !resolvedId.startsWith("intro_")) {
                        val otherUserId = resolvedId.split("_").firstOrNull { it != uid } ?: ""
                        if (otherUserId.isNotEmpty()) {
                            profileRepo.getProfile(otherUserId, fetchSkills = false).onSuccess { profile ->
                                if (_activeConnection.value == null || _activeConnection.value?.id == resolvedId) {
                                    _activeConnection.value = Connection(
                                        id = resolvedId,
                                        member = profile,
                                        lastMessage = "",
                                        lastMessageTime = "Now",
                                        unreadCount = 0,
                                        labels = emptyList(),
                                        privateGoal = "",
                                        status = ConnectionStatus.ACTIVE
                                    )
                                }
                            }.onFailure {
                                val placeholderProfile = Member(
                                    id = otherUserId,
                                    name = "User",
                                    headline = "",
                                    role = "",
                                    company = "",
                                    avatarUrl = "",
                                    membershipTier = MembershipTier.EXPLORER
                                )
                                if (_activeConnection.value == null || _activeConnection.value?.id == resolvedId) {
                                    _activeConnection.value = Connection(
                                        id = resolvedId,
                                        member = placeholderProfile,
                                        lastMessage = "",
                                        lastMessageTime = "Now",
                                        unreadCount = 0,
                                        labels = emptyList(),
                                        privateGoal = "",
                                        status = ConnectionStatus.ACTIVE
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        messagesCollectionJob = viewModelScope.launch {
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
    private val eventRepo: EventRepository = ServiceLocator.eventRepository,
    private val aiService: app.cosmos.com.data.repository.AiSummaryService = ServiceLocator.aiSummaryService
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

    // ── Filter state ─────────────────────────────────────────────────────────
    private val _selectedEventTypes = MutableStateFlow<Set<EventType>>(emptySet())
    val selectedEventTypes: StateFlow<Set<EventType>> = _selectedEventTypes.asStateFlow()

    private val _pricingFilter = MutableStateFlow(PricingFilter.ALL)
    val pricingFilter: StateFlow<PricingFilter> = _pricingFilter.asStateFlow()

    private val _showRegisteredOnly = MutableStateFlow(false)
    val showRegisteredOnly: StateFlow<Boolean> = _showRegisteredOnly.asStateFlow()

    val isFilterActive: Boolean
        get() = _selectedEventTypes.value.isNotEmpty() ||
                _pricingFilter.value != PricingFilter.ALL ||
                _showRegisteredOnly.value

    /** Derived filtered list based on current filter state */
    val filteredEvents: List<NetworkEvent>
        get() {
            var result = _events.value
            val types = _selectedEventTypes.value
            if (types.isNotEmpty()) {
                result = result.filter { it.type in types }
            }
            when (_pricingFilter.value) {
                PricingFilter.PAID_ONLY -> result = result.filter { it.isPaid }
                PricingFilter.FREE_ONLY -> result = result.filter { !it.isPaid }
                PricingFilter.ALL -> { /* no-op */ }
            }
            if (_showRegisteredOnly.value) {
                result = result.filter { it.isRegistered }
            }
            return result
        }

    fun toggleEventType(type: EventType) {
        val current = _selectedEventTypes.value.toMutableSet()
        if (type in current) current.remove(type) else current.add(type)
        _selectedEventTypes.value = current
    }

    fun setPricingFilter(filter: PricingFilter) {
        _pricingFilter.value = filter
    }

    fun toggleRegisteredOnly() {
        _showRegisteredOnly.value = !_showRegisteredOnly.value
    }

    fun resetFilters() {
        _selectedEventTypes.value = emptySet()
        _pricingFilter.value = PricingFilter.ALL
        _showRegisteredOnly.value = false
    }

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

    fun createEventWithImage(
        event: NetworkEvent,
        imageBytes: ByteArray?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            _isCreatingEvent.value = true
            if (imageBytes != null) {
                try {
                    val tempId = java.util.UUID.randomUUID().toString()
                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("events/$tempId.jpg")
                    storageRef.putBytes(imageBytes).await()
                    val downloadUrl = storageRef.downloadUrl.await().toString()
                    val eventWithImage = event.copy(coverUrl = downloadUrl)
                    eventRepo.createEvent(eventWithImage, uid).onSuccess {
                        _isCreatingEvent.value = false
                        onSuccess()
                    }.onFailure { error ->
                        _isCreatingEvent.value = false
                        onError(error.message ?: "Failed to create event")
                    }
                } catch (e: Exception) {
                    _isCreatingEvent.value = false
                    onError(e.message ?: "Failed to upload cover image")
                }
            } else {
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

    fun generateEventDescription(
        title: String,
        location: String,
        details: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            aiService.generateEventDescription(title, location, details)
                .onSuccess { onSuccess(it) }
                .onFailure { onError(it.message ?: "Failed to generate event description") }
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

    private val _pendingCircleMembers = MutableStateFlow<List<Member>>(emptyList())
    val pendingCircleMembers: StateFlow<List<Member>> = _pendingCircleMembers.asStateFlow()

    private val _isPendingMembersLoading = MutableStateFlow(false)
    val isPendingMembersLoading: StateFlow<Boolean> = _isPendingMembersLoading.asStateFlow()

    private val _activeOrbitPost = MutableStateFlow<CirclePost?>(null)
    val activeOrbitPost: StateFlow<CirclePost?> = _activeOrbitPost.asStateFlow()

    private val _orbitPostReplies = MutableStateFlow<List<CirclePostReply>>(emptyList())
    val orbitPostReplies: StateFlow<List<CirclePostReply>> = _orbitPostReplies.asStateFlow()

    private val _isOrbitRepliesLoading = MutableStateFlow(false)
    val isOrbitRepliesLoading: StateFlow<Boolean> = _isOrbitRepliesLoading.asStateFlow()

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

    fun loadPendingCircleMembers(circleId: String) {
        _isPendingMembersLoading.value = true
        viewModelScope.launch {
            circleRepo.getPendingCircleMembers(circleId).onSuccess { list ->
                _pendingCircleMembers.value = list
                _isPendingMembersLoading.value = false
            }.onFailure {
                _isPendingMembersLoading.value = false
            }
        }
    }

    fun approveCircleJoinRequest(circleId: String, userId: String) {
        viewModelScope.launch {
            circleRepo.approveCircleJoinRequest(circleId, userId).onSuccess {
                loadCircleMembers(circleId)
                loadPendingCircleMembers(circleId)
                loadCircles()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to approve request"
            }
        }
    }

    fun declineCircleJoinRequest(circleId: String, userId: String) {
        viewModelScope.launch {
            circleRepo.declineCircleJoinRequest(circleId, userId).onSuccess {
                loadPendingCircleMembers(circleId)
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to decline request"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun selectOrbitPost(circleId: String, postId: String) {
        _isOrbitRepliesLoading.value = true
        viewModelScope.launch {
            circleRepo.getCirclePost(circleId, postId).collectLatest { post ->
                _activeOrbitPost.value = post
            }
        }
        viewModelScope.launch {
            circleRepo.getCirclePostReplies(circleId, postId).collectLatest { replies ->
                _orbitPostReplies.value = replies
                _isOrbitRepliesLoading.value = false
            }
        }
    }

    fun addOrbitPostReply(circleId: String, postId: String, content: String) {
        viewModelScope.launch {
            authRepo.currentUser.firstOrNull()?.let { user ->
                val reply = CirclePostReply(
                    authorId = user.id,
                    authorName = user.name,
                    authorAvatarUrl = user.avatarUrl,
                    authorHeadline = user.headline,
                    content = content,
                    timeString = "Just now"
                )
                circleRepo.addCirclePostReply(circleId, postId, reply).onFailure { e ->
                    _errorMessage.value = e.message ?: "Failed to post comment"
                }
            }
        }
    }

    fun deleteOrbitPost(circleId: String, postId: String) {
        viewModelScope.launch {
            circleRepo.deleteCirclePost(circleId, postId).onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to delete post"
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

    val currentUserId: String? get() = authRepo.currentUserId

    init {
        loadNotifications()
    }

    private val _connectionProfileStatus = MutableStateFlow(ConnectionProfileStatus.NONE)
    val connectionProfileStatus: StateFlow<ConnectionProfileStatus> = _connectionProfileStatus.asStateFlow()

    private val _connectionSetupPayload = MutableStateFlow<String?>(null)
    val connectionSetupPayload: StateFlow<String?> = _connectionSetupPayload.asStateFlow()

    fun resetConnectionSetupPayload() {
        _connectionSetupPayload.value = null
    }

    fun checkConnectionStatus(memberId: String) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            ServiceLocator.connectionRequestRepository.getConnectionStatusFlow(uid, memberId)
                .collectLatest { status ->
                    _connectionProfileStatus.value = status
                }
        }
    }

    fun connectWithMember(memberId: String, message: String = "", onResult: (String?) -> Unit) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            val currentUser = authRepo.currentUser.firstOrNull()
            val targetUser = profileRepo.getProfile(memberId, fetchSkills = false).getOrNull()
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
                onResult(null)
            }.onFailure { error ->
                android.util.Log.e("CosmosConnection", "Failed to connect with member $memberId", error)
                onResult(error.localizedMessage ?: error.message ?: "Unknown error")
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
                    loadProfile(memberId)
                }
        }
    }

    fun acceptConnectionFromProfileWithLifecycle(memberId: String, onResult: (String?) -> Unit = {}) {
        val uid = authRepo.currentUserId ?: return
        val requestId = "req_${memberId}_${uid}"
        viewModelScope.launch {
            _connectionSetupPayload.value = "[INITIATED] Accepting request... Initiating a secure connection. Please hold."
            kotlinx.coroutines.delay(1000)
            
            _connectionSetupPayload.value = "[CONNECTING] Securing end-to-end connection... Finalizing channel setup."
            kotlinx.coroutines.delay(1000)

            var attempts = 0
            var success = false
            var errorMsg: String? = null

            while (attempts < 3 && !success) {
                attempts++
                val result = ServiceLocator.connectionRequestRepository.acceptConnectionRequest(requestId)
                if (result.isSuccess) {
                    success = true
                } else {
                    val ex = result.exceptionOrNull()
                    errorMsg = ex?.localizedMessage ?: ex?.message ?: "Unknown error"
                    if (attempts < 3) {
                        kotlinx.coroutines.delay(1500)
                    }
                }
            }

            if (success) {
                _connectionSetupPayload.value = "[SUCCESS] Connection successfully established. You are now securely connected."
                _connectionProfileStatus.value = ConnectionProfileStatus.CONNECTED
                loadProfile(memberId)
                kotlinx.coroutines.delay(1500)
                _connectionSetupPayload.value = null
                onResult(null)
            } else {
                _connectionSetupPayload.value = "[FAILURE] Unable to establish a stable connection at this time. Please check your network and try again."
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
            // 1. Fetch instantly without skills for immediate UI rendering
            profileRepo.getProfile(memberId, fetchSkills = false).onSuccess { member ->
                _selectedMember.value = member
            }
            // 2. Fetch skills asynchronously in the background
            profileRepo.getProfile(memberId, fetchSkills = true).onSuccess { memberWithSkills ->
                _selectedMember.value = memberWithSkills
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
                val resolved = coroutineScope {
                    list.map { req ->
                        async {
                            val requesterDef = async { profileRepo.getProfile(req.requester.id, fetchSkills = false).getOrDefault(req.requester) }
                            val targetDef = async { profileRepo.getProfile(req.target.id, fetchSkills = false).getOrDefault(req.target) }
                            val connectorDef = async { profileRepo.getProfile(req.connector.id, fetchSkills = false).getOrDefault(req.connector) }
                            req.copy(
                                requester = requesterDef.await(),
                                target = targetDef.await(),
                                connector = connectorDef.await()
                            )
                        }
                    }.awaitAll()
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

    private val _connectionSetupPayload = MutableStateFlow<String?>(null)
    val connectionSetupPayload: StateFlow<String?> = _connectionSetupPayload.asStateFlow()

    fun resetConnectionSetupPayload() {
        _connectionSetupPayload.value = null
    }

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

    fun sendRequest(
        receiverId: String,
        message: String = "",
        receiverName: String? = null,
        receiverHeadline: String? = null,
        receiverAvatarUrl: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            val currentUser = authRepo.currentUser.firstOrNull()
            // Use pre-fetched receiver data if provided, otherwise fetch from Firestore
            val finalReceiverName: String
            val finalReceiverHeadline: String
            val finalReceiverAvatarUrl: String
            if (receiverName != null) {
                finalReceiverName = receiverName
                finalReceiverHeadline = receiverHeadline ?: ""
                finalReceiverAvatarUrl = receiverAvatarUrl ?: ""
            } else {
                val targetUser = profileRepo.getProfile(receiverId, fetchSkills = false).getOrNull()
                finalReceiverName = targetUser?.name ?: ""
                finalReceiverHeadline = targetUser?.headline ?: ""
                finalReceiverAvatarUrl = targetUser?.avatarUrl ?: ""
            }
            connectionRequestRepo.sendConnectionRequest(
                senderId = uid,
                receiverId = receiverId,
                senderName = currentUser?.name ?: "",
                senderHeadline = currentUser?.headline ?: "",
                senderAvatarUrl = currentUser?.avatarUrl ?: "",
                receiverName = finalReceiverName,
                receiverHeadline = finalReceiverHeadline,
                receiverAvatarUrl = finalReceiverAvatarUrl,
                message = message
            ).onSuccess {
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Failed to send request")
            }
        }
    }

    fun acceptRequest(requestId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            connectionRequestRepo.acceptConnectionRequest(requestId)
                .onSuccess { onResult(null) }
                .onFailure { error -> onResult(error.localizedMessage ?: error.message ?: "Unknown error") }
        }
    }

    fun acceptRequestWithLifecycle(requestId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _connectionSetupPayload.value = "[INITIATED] Accepting request... Initiating a secure connection. Please hold."
            kotlinx.coroutines.delay(1000)
            
            _connectionSetupPayload.value = "[CONNECTING] Securing end-to-end connection... Finalizing channel setup."
            kotlinx.coroutines.delay(1000)

            var attempts = 0
            var success = false
            var errorMsg: String? = null

            while (attempts < 3 && !success) {
                attempts++
                val result = connectionRequestRepo.acceptConnectionRequest(requestId)
                if (result.isSuccess) {
                    success = true
                } else {
                    val ex = result.exceptionOrNull()
                    errorMsg = ex?.localizedMessage ?: ex?.message ?: "Unknown error"
                    if (attempts < 3) {
                        kotlinx.coroutines.delay(1500)
                    }
                }
            }

            if (success) {
                _connectionSetupPayload.value = "[SUCCESS] Connection successfully established. You are now securely connected."
                kotlinx.coroutines.delay(1500)
                _connectionSetupPayload.value = null
                onResult(null)
            } else {
                _connectionSetupPayload.value = "[FAILURE] Unable to establish a stable connection at this time. Please check your network and try again."
            }
        }
    }

    fun declineRequest(requestId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            connectionRequestRepo.declineConnectionRequest(requestId)
                .onSuccess { onResult(null) }
                .onFailure { error -> onResult(error.localizedMessage ?: error.message ?: "Unknown error") }
        }
    }

    fun withdrawRequest(requestId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            connectionRequestRepo.withdrawConnectionRequest(requestId)
                .onSuccess { onResult(null) }
                .onFailure { error -> onResult(error.localizedMessage ?: error.message ?: "Unknown error") }
        }
    }
}

// ── SearchViewModel ────────────────────────────────────────────────────────────
class SearchViewModel(
    private val profileRepo: ProfileRepository = ServiceLocator.profileRepository,
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val connectionRequestRepo: ConnectionRequestRepository = ServiceLocator.connectionRequestRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Member>>(emptyList())
    val searchResults: StateFlow<List<Member>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** Per-member connection status map: memberId -> ConnectionProfileStatus */
    private val _connectionStatuses = MutableStateFlow<Map<String, ConnectionProfileStatus>>(emptyMap())
    val connectionStatuses: StateFlow<Map<String, ConnectionProfileStatus>> = _connectionStatuses.asStateFlow()

    /** Error messages emitted when a connect attempt fails */
    private val _connectError = MutableSharedFlow<String>()
    val connectError: SharedFlow<String> = _connectError.asSharedFlow()

    fun updateQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().length >= 2) {
            executeSearch(query)
        } else {
            _searchResults.value = emptyList()
            _connectionStatuses.value = emptyMap()
        }
    }

    fun executeSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            profileRepo.searchProfiles(query).onSuccess { list ->
                _searchResults.value = list
                // Batch-fetch connection statuses for all results
                fetchConnectionStatuses(list)
            }
            _isSearching.value = false
        }
    }

    private fun fetchConnectionStatuses(members: List<Member>) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            val statusMap = mutableMapOf<String, ConnectionProfileStatus>()
            members.forEach { member ->
                if (member.id != uid) {
                    connectionRequestRepo.getConnectionStatus(uid, member.id)
                        .onSuccess { status -> statusMap[member.id] = status }
                        .onFailure { statusMap[member.id] = ConnectionProfileStatus.NONE }
                }
            }
            _connectionStatuses.value = statusMap
        }
    }

    /** Optimistic connect: immediately update UI, fire API, rollback on failure */
    fun sendConnectionRequest(member: Member) {
        val uid = authRepo.currentUserId ?: return
        if (member.id == uid) return

        // Optimistic update
        val previousStatuses = _connectionStatuses.value
        _connectionStatuses.value = previousStatuses + (member.id to ConnectionProfileStatus.PENDING_SENT)

        viewModelScope.launch {
            val currentUser = authRepo.currentUser.firstOrNull()
            connectionRequestRepo.sendConnectionRequest(
                senderId = uid,
                receiverId = member.id,
                senderName = currentUser?.name ?: "",
                senderHeadline = currentUser?.headline ?: "",
                senderAvatarUrl = currentUser?.avatarUrl ?: "",
                receiverName = member.name,
                receiverHeadline = member.headline,
                receiverAvatarUrl = member.avatarUrl,
                message = ""
            ).onFailure { error ->
                // Rollback optimistic update
                _connectionStatuses.value = previousStatuses
                _connectError.emit(error.message ?: "Failed to send connection request")
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _connectionStatuses.value = emptyMap()
    }
}

// ── SocialViewModel ──────────────────────────────────────────────────────────
class SocialViewModel(
    private val authRepo: AuthRepository = ServiceLocator.authRepository,
    private val socialRepo: SocialRepository = ServiceLocator.socialRepository
) : ViewModel() {

    val currentUser = authRepo.currentUser

    private val _socialPosts = MutableStateFlow<List<SocialPost>>(emptyList())
    val socialPosts: StateFlow<List<SocialPost>> = _socialPosts.asStateFlow()

    private val _likedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val likedPostIds: StateFlow<Set<String>> = _likedPostIds.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Post detail state
    private val _activePost = MutableStateFlow<SocialPost?>(null)
    val activePost: StateFlow<SocialPost?> = _activePost.asStateFlow()

    private val _activePostReplies = MutableStateFlow<List<SocialPostReply>>(emptyList())
    val activePostReplies: StateFlow<List<SocialPostReply>> = _activePostReplies.asStateFlow()

    private val _isRepliesLoading = MutableStateFlow(false)
    val isRepliesLoading: StateFlow<Boolean> = _isRepliesLoading.asStateFlow()

    private var postsCollectJob: kotlinx.coroutines.Job? = null
    private var repliesCollectJob: kotlinx.coroutines.Job? = null

    init {
        loadSocialPosts()
    }

    fun loadSocialPosts() {
        postsCollectJob?.cancel()
        _isLoading.value = true
        _errorMessage.value = null
        
        val uid = authRepo.currentUserId ?: ""
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                socialRepo.getLikedPostIds(uid).onSuccess { ids ->
                    _likedPostIds.value = ids
                }
            }
        }

        postsCollectJob = viewModelScope.launch {
            try {
                socialRepo.getSocialPosts().collectLatest { list ->
                    val filtered = list.filter { !it.authorId.startsWith("mock_user_") }
                    _socialPosts.value = filtered
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load social posts"
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun refreshSocialPosts() {
        _isRefreshing.value = true
        loadSocialPosts()
    }

    fun selectPost(postId: String) {
        repliesCollectJob?.cancel()
        _isRepliesLoading.value = true
        
        val foundPost = _socialPosts.value.find { it.id == postId }
        if (foundPost != null) {
            _activePost.value = foundPost
        }

        repliesCollectJob = viewModelScope.launch {
            try {
                socialRepo.getPostReplies(postId).collectLatest { list ->
                    _activePostReplies.value = list
                    _isRepliesLoading.value = false
                    _activePost.value = _socialPosts.value.find { it.id == postId } ?: _activePost.value?.copy(repliesCount = list.size)
                }
            } catch (e: Exception) {
                _isRepliesLoading.value = false
            }
        }
    }

    fun createPost(content: String, imageBytes: ByteArray? = null, onComplete: () -> Unit = {}) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                authRepo.currentUser.firstOrNull()?.let { user ->
                    val headline = user.headline.takeIf { it.isNotEmpty() } ?: user.role.takeIf { it.isNotEmpty() }?.let { "$it @ ${user.company}" } ?: "Member"
                    
                    var imageUrl: String? = null
                    if (imageBytes != null) {
                        val tempPostId = java.util.UUID.randomUUID().toString()
                        socialRepo.uploadPostImage(tempPostId, imageBytes).onSuccess { url ->
                            imageUrl = url
                        }.onFailure {
                            it.printStackTrace()
                        }
                    }

                    socialRepo.createSocialPost(
                        authorId = user.id,
                        authorName = user.name,
                        authorAvatarUrl = user.avatarUrl,
                        authorHeadline = headline,
                        content = content,
                        imageUrl = imageUrl,
                        isLinkedInConnected = user.isLinkedInConnected
                    ).onSuccess {
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            socialRepo.deleteSocialPost(postId)
        }
    }

    fun likePost(postId: String) {
        val uid = authRepo.currentUserId ?: return
        if (_likedPostIds.value.contains(postId)) return
        
        _likedPostIds.value = _likedPostIds.value + postId
        _socialPosts.value = _socialPosts.value.map { post ->
            if (post.id == postId) post.copy(likesCount = post.likesCount + 1) else post
        }
        if (_activePost.value?.id == postId) {
            _activePost.value = _activePost.value?.copy(likesCount = (_activePost.value?.likesCount ?: 0) + 1)
        }

        viewModelScope.launch {
            socialRepo.likePost(postId, uid).onFailure {
                _likedPostIds.value = _likedPostIds.value - postId
                _socialPosts.value = _socialPosts.value.map { post ->
                    if (post.id == postId) post.copy(likesCount = (post.likesCount - 1).coerceAtLeast(0)) else post
                }
                if (_activePost.value?.id == postId) {
                    _activePost.value = _activePost.value?.copy(likesCount = ((_activePost.value?.likesCount ?: 1) - 1).coerceAtLeast(0))
                }
            }
        }
    }

    fun unlikePost(postId: String) {
        val uid = authRepo.currentUserId ?: return
        if (!_likedPostIds.value.contains(postId)) return

        _likedPostIds.value = _likedPostIds.value - postId
        _socialPosts.value = _socialPosts.value.map { post ->
            if (post.id == postId) post.copy(likesCount = (post.likesCount - 1).coerceAtLeast(0)) else post
        }
        if (_activePost.value?.id == postId) {
            _activePost.value = _activePost.value?.copy(likesCount = ((_activePost.value?.likesCount ?: 1) - 1).coerceAtLeast(0))
        }

        viewModelScope.launch {
            socialRepo.unlikePost(postId, uid).onFailure {
                _likedPostIds.value = _likedPostIds.value + postId
                _socialPosts.value = _socialPosts.value.map { post ->
                    if (post.id == postId) post.copy(likesCount = post.likesCount + 1) else post
                }
                if (_activePost.value?.id == postId) {
                    _activePost.value = _activePost.value?.copy(likesCount = (_activePost.value?.likesCount ?: 0) + 1)
                }
            }
        }
    }

    fun addReply(postId: String, content: String) {
        viewModelScope.launch {
            try {
                authRepo.currentUser.firstOrNull()?.let { user ->
                    val headline = user.headline.takeIf { it.isNotEmpty() } ?: user.role.takeIf { it.isNotEmpty() }?.let { "$it @ ${user.company}" } ?: "Member"
                    val reply = SocialPostReply(
                        id = "",
                        authorId = user.id,
                        authorName = user.name,
                        authorAvatarUrl = user.avatarUrl,
                        authorHeadline = headline,
                        content = content,
                        timeString = "now",
                        isLinkedInConnected = user.isLinkedInConnected
                    )
                    socialRepo.addPostReply(postId, reply)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        postsCollectJob?.cancel()
        repliesCollectJob?.cancel()
    }
}

