package com.cosmos.app.data.repository

import com.cosmos.app.data.ValidationUtils
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MembershipTier
import com.cosmos.app.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface AuthRepository {
    val currentUser: Flow<Member?>
    val currentUserId: String?
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String, name: String, primaryType: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun saveOnboardingData(member: Member): Result<Unit>
    suspend fun uploadProfileImage(uid: String, bytes: ByteArray): Result<String>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun updateEmail(newEmail: String): Result<Unit>
    suspend fun updatePassword(currentPassword: String, newPassword: String): Result<Unit>
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUserId: String?
        get() = if (ServiceLocator.forceMockMode) {
            ServiceLocator.mockAuthRepository.currentUserId
        } else {
            auth.currentUser?.uid
        }

    private val firebaseCurrentUser: Flow<Member?> = callbackFlow {
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                // Fetch user data from Firestore
                val docRef = firestore.collection("users").document(firebaseUser.uid)
                val registration = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(null)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val member = mapDocumentToMember(snapshot.id, snapshot.data ?: emptyMap())
                        trySend(member)
                    } else {
                        // Document does not exist yet (onboarding incomplete)
                        trySend(
                            Member(
                                id = firebaseUser.uid,
                                name = firebaseUser.displayName ?: "",
                                headline = "",
                                role = "",
                                company = "",
                                avatarUrl = "",
                                email = firebaseUser.email ?: "",
                                membershipTier = MembershipTier.EXPLORER
                            )
                        )
                    }
                }
                // Close firestore snapshot listener when auth state changes or flow cancelled
                invokeOnClose {
                    registration.remove()
                }
            }
        }
        auth.addAuthStateListener(authListener)
        awaitClose {
            auth.removeAuthStateListener(authListener)
        }
    }

    override val currentUser: Flow<Member?> = callbackFlow {
        var currentJob: kotlinx.coroutines.Job? = null
        
        val monitorJob = launch {
            var lastMode: Boolean? = null
            while (isActive) {
                val mode = ServiceLocator.forceMockMode
                if (mode != lastMode) {
                    lastMode = mode
                    currentJob?.cancel()
                    currentJob = launch {
                        if (mode) {
                            ServiceLocator.mockAuthRepository.currentUser.collect {
                                send(it)
                            }
                        } else {
                            firebaseCurrentUser.collect {
                                send(it)
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(200)
            }
        }
        
        awaitClose {
            monitorJob.cancel()
            currentJob?.cancel()
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        // Validate inputs
        val emailValidation = ValidationUtils.validateEmail(email)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))
        if (password.isBlank()) return Result.failure(Exception("Password is required"))

        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.signIn(email, password)
        }
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e.message?.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) == true) {
                ServiceLocator.forceMockMode = true
                ServiceLocator.mockAuthRepository.signIn(email, password)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun signUp(email: String, password: String, name: String, primaryType: String): Result<Unit> {
        // Validate inputs
        val emailValidation = ValidationUtils.validateEmail(email)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))
        val passwordValidation = ValidationUtils.validatePassword(password)
        if (!passwordValidation.isValid) return Result.failure(Exception(passwordValidation.errorMessage))
        val nameValidation = ValidationUtils.validateName(name)
        if (!nameValidation.isValid) return Result.failure(Exception(nameValidation.errorMessage))

        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.signUp(email, password, name, primaryType)
        }
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val uid = result.user?.uid ?: throw IllegalStateException("UID not found after signup")

            // Initialize basic document in Firestore
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
                "eventsAttended" to 0,
                "followUpsCompleted" to 0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).set(basicUserMap).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(Exception("An account already exists with this email. Please sign in instead."))
        } catch (e: Exception) {
            if (e.message?.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) == true) {
                ServiceLocator.forceMockMode = true
                ServiceLocator.mockAuthRepository.signUp(email, password, name, primaryType)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return if (ServiceLocator.forceMockMode) {
            ServiceLocator.mockAuthRepository.signOut()
        } else {
            runCatching {
                auth.signOut()
            }
        }
    }

    override suspend fun saveOnboardingData(member: Member): Result<Unit> {
        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.saveOnboardingData(member)
        }
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
                "isProfileComplete" to true,
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
            // Use set() with merge=true so it works even if the document doesn't exist yet
            firestore.collection("users").document(uid).set(dataMap, SetOptions.merge()).await()
        }
    }

    override suspend fun uploadProfileImage(uid: String, bytes: ByteArray): Result<String> {
        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.uploadProfileImage(uid, bytes)
        }
        return try {
            val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$uid.jpg")
            storageRef.putBytes(bytes).await()
            val downloadUrl = storageRef.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            // Profile photo upload is optional. If Firebase Storage is not configured
            // we silently skip the photo and let onboarding continue normally.
            e.printStackTrace()
            Result.success("") // always succeed so onboarding is never blocked
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        val emailValidation = ValidationUtils.validateEmail(email)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))

        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.resetPassword(email)
        }
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.deleteAccount()
        }
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")
            // Delete Firestore user document
            firestore.collection("users").document(uid).delete().await()
            // Delete auth account
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateEmail(newEmail: String): Result<Unit> {
        val emailValidation = ValidationUtils.validateEmail(newEmail)
        if (!emailValidation.isValid) return Result.failure(Exception(emailValidation.errorMessage))

        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.updateEmail(newEmail)
        }
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

        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.updatePassword(currentPassword, newPassword)
        }
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
