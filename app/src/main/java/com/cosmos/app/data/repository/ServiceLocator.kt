package com.cosmos.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object ServiceLocator {

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

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(auth, firestore)
    }

    val profileRepository: ProfileRepository by lazy {
        FirestoreProfileRepository(firestore)
    }

    val swipeRepository: SwipeRepository by lazy {
        FirestoreSwipeRepository(firestore)
    }

    val chatRepository: ChatRepository by lazy {
        FirestoreChatRepository(firestore, profileRepository)
    }

    val eventRepository: EventRepository by lazy {
        FirestoreEventRepository(firestore)
    }

    val circleRepository: CircleRepository by lazy {
        FirestoreCircleRepository(firestore)
    }

    val notificationRepository: NotificationRepository by lazy {
        FirestoreNotificationRepository(firestore)
    }

    val introRepository: IntroRepository by lazy {
        FirestoreIntroRepository(firestore, profileRepository)
    }

    val aiSummaryService: AiSummaryService by lazy {
        GeminiAiSummaryService()
    }
}
