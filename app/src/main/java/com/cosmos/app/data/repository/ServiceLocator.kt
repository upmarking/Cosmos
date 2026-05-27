package com.cosmos.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object ServiceLocator {

    // Toggle this to manually force offline mock mode without connecting to Firebase.
    var forceMockMode: Boolean = false

    val isMockMode: Boolean = false

    init {
        try {
            val app = FirebaseApp.getInstance()
            val apiKey = app.options.apiKey
            if (apiKey != null && apiKey.contains("Dumpo", ignoreCase = true)) {
                forceMockMode = true
            }
        } catch (e: Exception) {
            forceMockMode = true
        }
    }

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore: FirebaseFirestore by lazy {
        val db = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        db.firestoreSettings = settings
        db
    }

    // Expose mock repositories so fallback can access them directly
    val mockAuthRepository = MockAuthRepository()
    val mockProfileRepository = MockProfileRepository()
    val mockSwipeRepository = MockSwipeRepository()
    val mockChatRepository = MockChatRepository()
    val mockEventRepository = MockEventRepository()
    val mockCircleRepository = MockCircleRepository()
    val mockNotificationRepository = MockNotificationRepository()
    val mockIntroRepository = MockIntroRepository()

    // Expose real repositories
    val firebaseAuthRepository by lazy { FirebaseAuthRepository(auth, firestore) }
    val firestoreProfileRepository by lazy { FirestoreProfileRepository(firestore) }
    val firestoreSwipeRepository by lazy { FirestoreSwipeRepository(firestore) }
    val firestoreChatRepository by lazy { FirestoreChatRepository(firestore, profileRepository) }
    val firestoreEventRepository by lazy { FirestoreEventRepository(firestore) }
    val firestoreCircleRepository by lazy { FirestoreCircleRepository(firestore) }
    val firestoreNotificationRepository by lazy { FirestoreNotificationRepository(firestore) }
    val firestoreIntroRepository by lazy { FirestoreIntroRepository(firestore, profileRepository) }

    val authRepository: AuthRepository
        get() = if (forceMockMode || isMockMode) mockAuthRepository else firebaseAuthRepository

    val profileRepository: ProfileRepository
        get() = if (forceMockMode || isMockMode) mockProfileRepository else firestoreProfileRepository

    val swipeRepository: SwipeRepository
        get() = if (forceMockMode || isMockMode) mockSwipeRepository else firestoreSwipeRepository

    val chatRepository: ChatRepository
        get() = if (forceMockMode || isMockMode) mockChatRepository else firestoreChatRepository

    val eventRepository: EventRepository
        get() = if (forceMockMode || isMockMode) mockEventRepository else firestoreEventRepository

    val circleRepository: CircleRepository
        get() = if (forceMockMode || isMockMode) mockCircleRepository else firestoreCircleRepository

    val notificationRepository: NotificationRepository
        get() = if (forceMockMode || isMockMode) mockNotificationRepository else firestoreNotificationRepository

    val introRepository: IntroRepository
        get() = if (forceMockMode || isMockMode) mockIntroRepository else firestoreIntroRepository

    val aiSummaryService: AiSummaryService by lazy {
        GeminiAiSummaryService()
    }
}
