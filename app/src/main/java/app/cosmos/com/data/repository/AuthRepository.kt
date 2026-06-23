package app.cosmos.com.data.repository

import app.cosmos.com.data.ValidationUtils
import app.cosmos.com.data.model.Member
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseUser

interface AuthRepository {
    val currentUser: Flow<Member?>
    val currentUserId: String?
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String, name: String, primaryType: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun saveOnboardingData(member: Member): Result<Unit>
    suspend fun uploadProfileImage(uid: String, bytes: ByteArray): Result<String>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun verifyPasswordResetCode(code: String): Result<String>
    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun updateEmail(newEmail: String): Result<Unit>
    suspend fun updatePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun reloadUser(): Result<Boolean>
    suspend fun resendVerificationEmail(): Result<Unit>
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    // A dedicated scope for the repository-level hot flows.
    // SupervisorJob ensures one child failure doesn't cancel the whole scope.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Step 1: Auth state as a hot shared flow ───────────────────────────────
    // shareIn(Eagerly) starts collecting immediately and replays the last
    // value to every new subscriber — no cold-start lag.
    private val firebaseUserFlow: Flow<FirebaseUser?> = callbackFlow {
        // Emit the current user synchronously so the first subscriber
        // gets a value without waiting for the listener to fire.
        trySend(auth.currentUser)
        val authListener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser)
        }
        auth.addAuthStateListener(authListener)
        awaitClose { auth.removeAuthStateListener(authListener) }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = repoScope,
            started = SharingStarted.Eagerly,
            replay = 1          // new subscribers immediately get the last auth state
        )

    // ── Step 2: User document as a hot shared flow ────────────────────────────
    // flatMapLatest switches to a new Firestore listener whenever auth changes.
    // shareIn(Eagerly, replay=1) means:
    //   • only ONE Firestore listener is ever active (no matter how many subscribers)
    //   • any new subscriber immediately gets the last emitted Member (no spinner)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val currentUser: Flow<Member?> = firebaseUserFlow
        .flatMapLatest { firebaseUser ->
            if (firebaseUser == null) return@flatMapLatest flowOf(null)

            callbackFlow<Member?> {
                var skillsJob: kotlinx.coroutines.Job? = null

                val firestoreListener = firestore
                    .collection("users")
                    .document(firebaseUser.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            // Log the error but DO NOT emit null — keep showing the last
                            // known good value so the UI doesn't blank out on a transient error.
                            android.util.Log.e("CosmosAuth", "Firestore snapshot listener error", error)
                            error.printStackTrace()
                            return@addSnapshotListener
                        }

                        if (snapshot != null && snapshot.exists()) {
                            // Cancel any in-flight skills fetch from a previous snapshot
                            skillsJob?.cancel()
                            skillsJob = launch {
                                val member = mapDocumentToMember(
                                    snapshot.id,
                                    snapshot.data ?: emptyMap()
                                ).copy(isFromCache = snapshot.metadata.isFromCache)

                                // If the existing profile is blank/incomplete, check if we can populate it from a seed user
                                if (member.name.isBlank()) {
                                    try {
                                        val email = firebaseUser.email
                                        if (!email.isNullOrBlank()) {
                                            val seedQuery = firestore.collection("users")
                                                .whereEqualTo("email", email.trim().lowercase())
                                                .get()
                                                .await()
                                            val seedDoc = seedQuery.documents.firstOrNull { it.id != firebaseUser.uid }
                                            if (seedDoc != null && seedDoc.exists()) {
                                                val data = seedDoc.data?.toMutableMap() ?: mutableMapOf<String, Any>()
                                                data["id"] = firebaseUser.uid
                                                
                                                // Write user document
                                                firestore.collection("users")
                                                    .document(firebaseUser.uid)
                                                    .set(data)
                                                    .await()
                                                
                                                // Copy skills subcollection
                                                val skillsQuery = seedDoc.reference.collection("skills").get().await()
                                                for (skillDoc in skillsQuery.documents) {
                                                    firestore.collection("users")
                                                        .document(firebaseUser.uid)
                                                        .collection("skills")
                                                        .document(skillDoc.id)
                                                        .set(skillDoc.data ?: emptyMap<String, Any>())
                                                        .await()
                                                }
                                                return@launch
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("CosmosAuth", "Error cloning seed profile inside exists", e)
                                    }
                                }

                                // 1. Emit basic member data IMMEDIATELY so the UI unblocks
                                trySend(member)

                                // 2. Fetch skills in the background and emit again
                                val skills = runCatching {
                                    firestore.collection("users")
                                        .document(snapshot.id)
                                        .collection("skills")
                                        .get()
                                        .await()
                                        .documents
                                        .map { doc ->
                                            app.cosmos.com.data.model.EndorsedSkill(
                                                name = doc.getString("name") ?: "",
                                                count = doc.getLong("count")?.toInt() ?: 0,
                                                endorsers = (doc.get("endorsers") as? List<*>)
                                                    ?.filterIsInstance<String>() ?: emptyList()
                                            )
                                        }
                                }.getOrDefault(emptyList())

                                trySend(member.copy(endorsedSkills = skills))
                            }
                        } else {
                            // Document does not exist (new user / just deleted / cache miss).
                            // Check if there is a seeded profile with this email that needs to be cloned to the new UID.
                            launch {
                                try {
                                    val email = firebaseUser.email
                                    if (!email.isNullOrBlank()) {
                                        val seedQuery = firestore.collection("users")
                                            .whereEqualTo("email", email.trim().lowercase())
                                            .get()
                                            .await()
                                        val seedDoc = seedQuery.documents.firstOrNull { it.id != firebaseUser.uid }
                                        if (seedDoc != null && seedDoc.exists()) {
                                            val data = seedDoc.data?.toMutableMap() ?: mutableMapOf<String, Any>()
                                            data["id"] = firebaseUser.uid
                                            
                                            // Write user document
                                            firestore.collection("users")
                                                .document(firebaseUser.uid)
                                                .set(data)
                                                .await()
                                            
                                            // Copy skills subcollection
                                            val skillsQuery = seedDoc.reference.collection("skills").get().await()
                                            for (skillDoc in skillsQuery.documents) {
                                                firestore.collection("users")
                                                    .document(firebaseUser.uid)
                                                    .collection("skills")
                                                    .document(skillDoc.id)
                                                    .set(skillDoc.data ?: emptyMap<String, Any>())
                                                    .await()
                                            }
                                            // The addSnapshotListener will trigger automatically upon creation.
                                            return@launch
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("CosmosAuth", "Error cloning seed profile", e)
                                }

                                val defaultMember = Member(
                                    id = firebaseUser.uid,
                                    name = firebaseUser.displayName ?: "",
                                    headline = "",
                                    role = "",
                                    company = "",
                                    avatarUrl = "",
                                    email = firebaseUser.email ?: "",
                                    membershipTier = MembershipTier.EXPLORER,
                                    primaryUserType = "",
                                    isProfileComplete = false,
                                    isFromCache = snapshot?.metadata?.isFromCache ?: false
                                )
                                trySend(defaultMember)
                            }
                        }
                    }

                awaitClose { firestoreListener.remove() }
            }
        }
        .distinctUntilChanged()
        .shareIn(
            scope = repoScope,
            started = SharingStarted.Eagerly,
            replay = 1          // replay the last Member to any new collector
        )

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        val emailValidation = ValidationUtils.validateEmail(email)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))
        if (password.isBlank()) return Result.failure(Exception("Password is required"))

        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            Result.success(Unit)
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception("ACCOUNT_NOT_FOUND"))
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("no user record", ignoreCase = true) || msg.contains("user not found", ignoreCase = true) || msg.contains("invalid login credential", ignoreCase = true)) {
                // If enumeration protection is on, invalid login credentials might mean account not found. 
                // We map this to ACCOUNT_NOT_FOUND to prompt the user to sign up, as per user requirement.
                Result.failure(Exception("ACCOUNT_NOT_FOUND"))
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun signUp(email: String, password: String, name: String, primaryType: String): Result<Unit> {
        val emailValidation = ValidationUtils.validateEmail(email)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))
        val passwordValidation = ValidationUtils.validatePassword(password)
        if (!passwordValidation.isValid) return Result.failure(Exception(passwordValidation.errorMessage))
        val nameValidation = ValidationUtils.validateName(name)
        if (!nameValidation.isValid) return Result.failure(Exception(nameValidation.errorMessage))

        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: throw IllegalStateException("UID not found after signup")

            // Send verification email
            user.sendEmailVerification().await()

            val uid = user.uid

            val basicUserMap = mapOf(
                "id" to uid,
                "name" to name.trim(),
                "email" to email.trim().lowercase(),
                "primaryUserType" to primaryType,
                "userRole" to UserRole.ORGANIZER.name,
                "headline" to "",
                "role" to "",
                "company" to "",
                "avatarUrl" to "",
                "location" to "",
                "bio" to "",
                "tags" to emptyList<String>(),
                "goalStatement" to "",
                "longTermVision" to "",
                "lookingFor" to emptyList<String>(),
                "isLinkedInConnected" to false,
                "isProfileComplete" to false,
                "isRestricted" to false,
                "membershipTier" to MembershipTier.EXPLORER.name,
                "connectionsCount" to 0,
                "followersCount" to 0,
                "followingCount" to 0,
                "eventsAttended" to 0,
                "followUpsCompleted" to 0,
                "joinedCircles" to emptyList<String>(),
                "pendingCircles" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).set(basicUserMap).await()

            Result.success(Unit)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(Exception("An account already exists with this email. Please sign in instead."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveOnboardingData(member: Member): Result<Unit> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
            val dataMap = mapOf(
                "id" to uid,
                "name" to member.name,
                "headline" to member.headline,
                "role" to member.role,
                "company" to member.company,
                "avatarUrl" to member.avatarUrl,
                "location" to member.location,
                "bio" to member.bio,
                "tags" to member.tags,
                "goalStatement" to member.goalStatement,
                "longTermVision" to member.longTermVision,
                "lookingFor" to member.lookingFor,
                "availabilityPreferences" to member.availabilityPreferences,
                "isLinkedInConnected" to member.isLinkedInConnected,
                "isProfileComplete" to member.isProfileComplete,
                "membershipTier" to member.membershipTier.name,
                "primaryUserType" to member.primaryUserType,
                "isRestricted" to member.isRestricted,
                "notificationNewMatches" to member.notificationNewMatches,
                "notificationMessages" to member.notificationMessages,
                "notificationEventInvitations" to member.notificationEventInvitations,
                "notificationEventReminders" to member.notificationEventReminders,
                "notificationAiSummaries" to member.notificationAiSummaries,
                "notificationFollowUpReminders" to member.notificationFollowUpReminders,
                "notificationWarmIntroRequests" to member.notificationWarmIntroRequests,
                "notificationCommunityAnnouncements" to member.notificationCommunityAnnouncements,
                "notificationEndorsements" to member.notificationEndorsements,
                "privacyProfileVisibility" to member.privacyProfileVisibility,
                "privacyShowLinkedIn" to member.privacyShowLinkedIn,
                "privacyAllowWarmIntros" to member.privacyAllowWarmIntros,
                "privacyShowMutualConnections" to member.privacyShowMutualConnections,
                "privacyDataAnalytics" to member.privacyDataAnalytics,
                "monthlyConnectionLimit" to member.monthlyConnectionLimit,
                "matchingPreferences" to member.matchingPreferences,
                "blockedUsers" to member.blockedUsers,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).set(dataMap, SetOptions.merge()).await()
        }
    }

    override suspend fun uploadProfileImage(uid: String, bytes: ByteArray): Result<String> {
        return try {
            val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$uid.jpg")
            storageRef.putBytes(bytes).await()
            val downloadUrl = storageRef.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success("") // always succeed so onboarding is never blocked
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        val emailValidation = ValidationUtils.validateEmail(email)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))

        val trimmedEmail = email.trim().lowercase()

        return try {
            // Trigger Firebase Auth password reset email directly (non-blocking).
            // Firebase Auth does the backend validation and email triggering securely.
            auth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            // Silence user not found to prevent user enumeration
            android.util.Log.i("CosmosAuth", "Silenced user not found for reset: $trimmedEmail")
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("user-not-found", ignoreCase = true) || msg.contains("USER_NOT_FOUND", ignoreCase = true)) {
                android.util.Log.i("CosmosAuth", "Silenced user not found (by message) for reset: $trimmedEmail")
                Result.success(Unit)
            } else {
                android.util.Log.e("CosmosAuth", "Error sending password reset email for $trimmedEmail", e)
                Result.success(Unit)
            }
        }
    }

    override suspend fun verifyPasswordResetCode(code: String): Result<String> {
        if (code.isBlank()) return Result.failure(Exception("Reset code is blank"))
        return try {
            val email = auth.verifyPasswordResetCode(code).await()
            Result.success(email)
        } catch (e: Exception) {
            android.util.Log.e("CosmosAuth", "Error verifying password reset code", e)
            Result.failure(e)
        }
    }

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> {
        if (code.isBlank()) return Result.failure(Exception("Reset code is blank"))
        val passwordValidation = ValidationUtils.validatePassword(newPassword)
        if (!passwordValidation.isValid) return Result.failure(Exception(passwordValidation.errorMessage))

        return try {
            auth.confirmPasswordReset(code, newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CosmosAuth", "Error confirming password reset", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")
            firestore.collection("users").document(uid).delete().await()
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateEmail(newEmail: String): Result<Unit> {
        val emailValidation = ValidationUtils.validateEmail(newEmail)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))

        return try {
            auth.currentUser?.verifyBeforeUpdateEmail(newEmail.trim())?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePassword(currentPassword: String, newPassword: String): Result<Unit> {
        if (currentPassword.isBlank()) return Result.failure(Exception("Current password is required"))
        val passwordValidation = ValidationUtils.validatePassword(newPassword)
        if (!passwordValidation.isValid) return Result.failure(Exception(passwordValidation.errorMessage))

        return try {
            val user = auth.currentUser ?: throw IllegalStateException("User not logged in")
            val email = user.email ?: throw IllegalStateException("User email not found")
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reloadUser(): Result<Boolean> {
        return try {
            val user = auth.currentUser
            if (user != null) {
                user.reload().await()
                Result.success(user.isEmailVerified)
            } else {
                Result.failure(Exception("No user logged in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resendVerificationEmail(): Result<Unit> {
        return try {
            val user = auth.currentUser
            if (user != null) {
                user.sendEmailVerification().await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("No user logged in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun mapDocumentToMember(id: String, data: Map<String, Any>): Member {
            val membershipTierStr = data["membershipTier"] as? String ?: MembershipTier.EXPLORER.name
            val membershipTier = runCatching { MembershipTier.valueOf(membershipTierStr) }.getOrDefault(MembershipTier.EXPLORER)
            val userRoleStr = data["userRole"] as? String ?: UserRole.USER.name
            val userRole = runCatching { UserRole.valueOf(userRoleStr) }.getOrDefault(UserRole.USER)
            
            return Member(
                id = id,
                name = data["name"] as? String ?: "",
                headline = data["headline"] as? String ?: "",
                role = data["role"] as? String ?: "",
                company = data["company"] as? String ?: "",
                avatarUrl = data["avatarUrl"] as? String ?: "",
                email = data["email"] as? String ?: "",
                location = data["location"] as? String ?: "",
                bio = data["bio"] as? String ?: "",
                tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                goalStatement = data["goalStatement"] as? String ?: "",
                longTermVision = data["longTermVision"] as? String ?: "",
                isLinkedInConnected = data["isLinkedInConnected"] as? Boolean ?: false,
                mutualConnectionsCount = (data["mutualConnectionsCount"] as? Number)?.toInt() ?: 0,
                membershipTier = membershipTier,
                connectionsCount = (data["connectionsCount"] as? Number)?.toInt() ?: 0,
                followersCount = (data["followersCount"] as? Number)?.toInt() ?: 0,
                followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
                eventsAttended = (data["eventsAttended"] as? Number)?.toInt() ?: 0,
                followUpsCompleted = (data["followUpsCompleted"] as? Number)?.toInt() ?: 0,
                primaryUserType = data["primaryUserType"] as? String ?: "",
                userRole = userRole,
                createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L,
                updatedAt = (data["updatedAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L,
                isProfileComplete = data["isProfileComplete"] as? Boolean ?: false,
                isRestricted = data["isRestricted"] as? Boolean ?: false,
                lookingFor = (data["lookingFor"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                availabilityPreferences = data["availabilityPreferences"] as? String ?: "",
                notificationNewMatches = data["notificationNewMatches"] as? Boolean ?: true,
                notificationMessages = data["notificationMessages"] as? Boolean ?: true,
                notificationEventInvitations = data["notificationEventInvitations"] as? Boolean ?: true,
                notificationEventReminders = data["notificationEventReminders"] as? Boolean ?: true,
                notificationAiSummaries = data["notificationAiSummaries"] as? Boolean ?: true,
                notificationFollowUpReminders = data["notificationFollowUpReminders"] as? Boolean ?: true,
                notificationWarmIntroRequests = data["notificationWarmIntroRequests"] as? Boolean ?: true,
                notificationCommunityAnnouncements = data["notificationCommunityAnnouncements"] as? Boolean ?: true,
                notificationEndorsements = data["notificationEndorsements"] as? Boolean ?: true,
                privacyProfileVisibility = data["privacyProfileVisibility"] as? Boolean ?: true,
                privacyShowLinkedIn = data["privacyShowLinkedIn"] as? Boolean ?: true,
                privacyAllowWarmIntros = data["privacyAllowWarmIntros"] as? Boolean ?: true,
                privacyShowMutualConnections = data["privacyShowMutualConnections"] as? Boolean ?: true,
                privacyDataAnalytics = data["privacyDataAnalytics"] as? Boolean ?: true,
                monthlyConnectionLimit = (data["monthlyConnectionLimit"] as? Number)?.toInt() ?: 10,
                matchingPreferences = (data["matchingPreferences"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                blockedUsers = (data["blockedUsers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}
