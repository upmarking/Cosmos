package com.cosmos.app.data.repository

import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MembershipTier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.signIn(email, password)
        }
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
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
        if (ServiceLocator.forceMockMode) {
            return ServiceLocator.mockAuthRepository.signUp(email, password, name, primaryType)
        }
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw IllegalStateException("UID not found after signup")

            // Initialize basic document in Firestore
            val basicUserMap = mapOf(
                "id" to uid,
                "name" to name,
                "primaryUserType" to primaryType,
                "headline" to "",
                "role" to "",
                "company" to "",
                "avatarUrl" to "",
                "location" to "",
                "bio" to "",
                "tags" to emptyList<String>(),
                "goalStatement" to "",
                "longTermVision" to "",
                "isLinkedInConnected" to false,
                "membershipTier" to MembershipTier.EXPLORER.name,
                "connectionsCount" to 0,
                "eventsAttended" to 0,
                "followUpsCompleted" to 0
            )
            firestore.collection("users").document(uid).set(basicUserMap).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthUserCollisionException) {
            // Email is already registered — surface a clear, actionable message
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
                "isLinkedInConnected" to member.isLinkedInConnected,
                "membershipTier" to member.membershipTier.name,
                "primaryUserType" to member.primaryUserType
            )
            // Use set() with merge=true so it works even if the document doesn't exist yet
            // (avoids CONFIGURATION_NOT_FOUND / NOT_FOUND errors from update())
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
            // (CONFIGURATION_NOT_FOUND), the bucket doesn't exist, or any other error
            // occurs, we silently skip the photo and let onboarding continue normally.
            e.printStackTrace()
            Result.success("") // always succeed so onboarding is never blocked
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun mapDocumentToMember(id: String, data: Map<String, Any>): Member {
            val membershipTierStr = data["membershipTier"] as? String ?: MembershipTier.EXPLORER.name
            val membershipTier = runCatching { MembershipTier.valueOf(membershipTierStr) }.getOrDefault(MembershipTier.EXPLORER)
            
            return Member(
                id = id,
                name = data["name"] as? String ?: "",
                headline = data["headline"] as? String ?: "",
                role = data["role"] as? String ?: "",
                company = data["company"] as? String ?: "",
                avatarUrl = data["avatarUrl"] as? String ?: "",
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
                primaryUserType = data["primaryUserType"] as? String ?: ""
            )
        }
    }
}
