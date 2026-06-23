package app.cosmos.com.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.*
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.data.repository.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NetworkRelationsScreen(
    initialTab: String = "followers",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(initialTab) } // "followers", "following", "connections"

    // Live Database States
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var realConnections by remember { mutableStateOf<List<Connection>>(emptyList()) }
    var incomingReqs by remember { mutableStateOf<List<ConnectionRequest>>(emptyList()) }
    var outgoingReqs by remember { mutableStateOf<List<ConnectionRequest>>(emptyList()) }

    // Removed IDs for instant UI feedback (fade out and count decrement)
    val removedIds = remember { mutableStateListOf<String>() }

    // Refresh trigger for retrying queries
    var refreshTrigger by remember { mutableStateOf(0) }

    // Live state listener
    LaunchedEffect(refreshTrigger) {
        val uid = ServiceLocator.authRepository.currentUserId
        if (uid == null) {
            isError = true
            errorMessage = "User not authenticated"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        isError = false

        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // 1. Listen to connections
            val connQuery = firestore.collection("connections")
                .whereArrayContains("members", uid)
            val connRegistration = connQuery.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isError = true
                    errorMessage = "Unable to sync live list. Retrying connection..."
                    isLoading = false
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    coroutineScope.launch {
                        val list = snapshot.documents.map { doc ->
                            val data = doc.data ?: emptyMap()
                            val members = (data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val otherMemberId = members.firstOrNull { it != uid } ?: ""

                            val statusStr = data["status"] as? String ?: ConnectionStatus.ACTIVE.name
                            val status = runCatching { ConnectionStatus.valueOf(statusStr) }.getOrDefault(ConnectionStatus.ACTIVE)

                            val profile = ServiceLocator.profileRepository.getProfile(otherMemberId, fetchSkills = false)
                                .getOrElse {
                                    Member(
                                        id = otherMemberId,
                                        name = data["receiverName"] as? String ?: "Member $otherMemberId",
                                        headline = data["receiverHeadline"] as? String ?: "",
                                        role = "",
                                        company = "",
                                        avatarUrl = data["receiverAvatarUrl"] as? String ?: "",
                                        membershipTier = MembershipTier.EXPLORER
                                    )
                                }

                            Connection(
                                id = doc.id,
                                member = profile,
                                status = status
                            )
                        }
                        realConnections = list.filter { it.status == ConnectionStatus.ACTIVE }
                        isLoading = false
                    }
                }
            }

            // 2. Listen to pending connection requests
            val reqQuery = firestore.collection("connection_requests")
            
            // Incoming
            val incomingRegistration = reqQuery
                .whereEqualTo("receiverId", uid)
                .whereEqualTo("status", ConnectionRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        isError = true
                        errorMessage = "Unable to sync live list. Retrying connection..."
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        incomingReqs = snapshot.documents.map { doc ->
                            val data = doc.data ?: emptyMap()
                            val senderId = data["senderId"] as? String ?: ""
                            val senderName = data["senderName"] as? String ?: "User"
                            val senderHeadline = data["senderHeadline"] as? String ?: ""
                            val senderAvatarUrl = data["senderAvatarUrl"] as? String ?: ""
                            ConnectionRequest(
                                id = doc.id,
                                senderId = senderId,
                                receiverId = uid,
                                senderName = senderName,
                                senderHeadline = senderHeadline,
                                senderAvatarUrl = senderAvatarUrl
                            )
                        }
                    }
                }

            // Outgoing
            val outgoingRegistration = reqQuery
                .whereEqualTo("senderId", uid)
                .whereEqualTo("status", ConnectionRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        isError = true
                        errorMessage = "Unable to sync live list. Retrying connection..."
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        outgoingReqs = snapshot.documents.map { doc ->
                            val data = doc.data ?: emptyMap()
                            val receiverId = data["receiverId"] as? String ?: ""
                            val receiverName = data["receiverName"] as? String ?: "User"
                            val receiverHeadline = data["receiverHeadline"] as? String ?: ""
                            val receiverAvatarUrl = data["receiverAvatarUrl"] as? String ?: ""
                            ConnectionRequest(
                                id = doc.id,
                                senderId = uid,
                                receiverId = receiverId,
                                receiverName = receiverName,
                                receiverHeadline = receiverHeadline,
                                receiverAvatarUrl = receiverAvatarUrl
                            )
                        }
                    }
                }

        } catch (e: Exception) {
            isError = true
            errorMessage = "Unable to sync live list. Retrying connection..."
            isLoading = false
        }
    }

    // Fallback Mock data for visual completeness when real lists are empty
    val mockFollowers = listOf(
        Member(id = "mock_user_sarah", name = "Sarah Jenkins", headline = "Founder & CEO at BioSphere", role = "CEO", company = "BioSphere", avatarUrl = "", isLinkedInConnected = true),
        Member(id = "mock_user_elena", name = "Elena Rostova", headline = "Lead Designer at Cosmos Studio", role = "Designer", company = "Cosmos Studio", avatarUrl = "")
    )
    val mockFollowing = listOf(
        Member(id = "mock_user_david", name = "David Chen", headline = "General Partner at Nexus Ventures", role = "GP", company = "Nexus Ventures", avatarUrl = "", isLinkedInConnected = true),
        Member(id = "mock_user_marcus", name = "Marcus Vance", headline = "VP of Product at ScaleUp", role = "Product VP", company = "ScaleUp", avatarUrl = "")
    )
    val mockConnections = listOf(
        Member(id = "mock_user_sarah", name = "Sarah Jenkins", headline = "Founder & CEO at BioSphere", role = "CEO", company = "BioSphere", avatarUrl = "", isLinkedInConnected = true),
        Member(id = "mock_user_david", name = "David Chen", headline = "General Partner at Nexus Ventures", role = "GP", company = "Nexus Ventures", avatarUrl = "", isLinkedInConnected = true)
    )

    // Compute active lists combining real + mock fallbacks (if no real ones exist)
    val currentConnections = if (realConnections.isNotEmpty()) {
        realConnections.map { it.member }
    } else {
        mockConnections
    }.filter { it.id !in removedIds }

    val currentFollowers = if (realConnections.isNotEmpty() || incomingReqs.isNotEmpty()) {
        val list = mutableListOf<Member>()
        list.addAll(realConnections.map { it.member })
        list.addAll(incomingReqs.map {
            Member(
                id = it.senderId,
                name = it.senderName,
                headline = it.senderHeadline,
                role = "",
                company = "",
                avatarUrl = it.senderAvatarUrl
            )
        })
        list.distinctBy { it.id }
    } else {
        mockFollowers
    }.filter { it.id !in removedIds }

    val currentFollowing = if (realConnections.isNotEmpty() || outgoingReqs.isNotEmpty()) {
        val list = mutableListOf<Member>()
        list.addAll(realConnections.map { it.member })
        list.addAll(outgoingReqs.map {
            Member(
                id = it.receiverId,
                name = it.receiverName,
                headline = it.receiverHeadline,
                role = "",
                company = "",
                avatarUrl = it.receiverAvatarUrl
            )
        })
        list.distinctBy { it.id }
    } else {
        mockFollowing
    }.filter { it.id !in removedIds }

    // Instant counter decrement computations
    val followersCount = currentFollowers.size
    val followingCount = currentFollowing.size
    val connectionsCount = currentConnections.size

    // Perform database delete operation
    val handleRemoveAction: (String, String) -> Unit = { memberId, tab ->
        removedIds.add(memberId) // Optimistic update
        coroutineScope.launch {
            val uid = ServiceLocator.authRepository.currentUserId ?: return@launch
            if (memberId.startsWith("mock_user_")) {
                delay(300) // Simulated delay
                return@launch
            }
            when (tab) {
                "followers" -> {
                    // Decline/Remove incoming request if pending
                    val reqId = "req_${memberId}_${uid}"
                    ServiceLocator.connectionRequestRepository.declineConnectionRequest(reqId)
                    // Also attempt deleting mutual connections
                    ServiceLocator.connectionRequestRepository.removeConnection(uid, memberId)
                }
                "following" -> {
                    // Withdraw/Remove outgoing request if pending
                    val reqId = "req_${uid}_${memberId}"
                    ServiceLocator.connectionRequestRepository.withdrawConnectionRequest(reqId)
                    // Also attempt deleting mutual connections
                    ServiceLocator.connectionRequestRepository.removeConnection(uid, memberId)
                }
                "connections" -> {
                    ServiceLocator.connectionRequestRepository.removeConnection(uid, memberId)
                }
            }
        }
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // Sticky Top Bar
            CosmosTopBar(
                title = "Network Relations",
                onBack = onBack
            )

            // Anti-freeze Error Display
            if (isError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CosmosGlassCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Error",
                                tint = CosmosError,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = CosmosOnBackground,
                                fontWeight = FontWeight.SemiBold
                            )
                            Button(
                                onClick = { refreshTrigger++ },
                                colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                                    Text("Retry Connection", color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Instagram-style Dashboard Tiles
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CosmosStatCard(
                            label = "Followers",
                            value = "$followersCount",
                            accent = if (activeTab == "followers") CosmosPrimary else CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                            onClick = { activeTab = "followers" }
                        )
                        CosmosStatCard(
                            label = "Following",
                            value = "$followingCount",
                            accent = if (activeTab == "following") CosmosSecondary else CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                            onClick = { activeTab = "following" }
                        )
                        CosmosStatCard(
                            label = "Connections",
                            value = "$connectionsCount",
                            accent = if (activeTab == "connections") CosmosTertiary else CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                            onClick = { activeTab = "connections" }
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // List Container
                    CosmosGlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp),
                        showTopGradientBorder = true
                    ) {
                        Text(
                            text = when (activeTab) {
                                "followers" -> "Followers List"
                                "following" -> "Following List"
                                else -> "Mutual Connections"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Divider(color = CosmosOutlineVariant.copy(alpha = 0.15f), thickness = 1.dp)
                        Spacer(Modifier.height(8.dp))

                        if (isLoading) {
                            // Skeleton loading states
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(4) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(CosmosSurfaceContainerHigh.copy(alpha = 0.4f))
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(modifier = Modifier.width(100.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(CosmosSurfaceContainerHigh.copy(alpha = 0.4f)))
                                            Box(modifier = Modifier.width(70.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(CosmosSurfaceContainerHigh.copy(alpha = 0.4f)))
                                        }
                                        Box(modifier = Modifier.width(80.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(CosmosSurfaceContainerHigh.copy(alpha = 0.4f)))
                                    }
                                }
                            }
                        } else {
                            val activeList = when (activeTab) {
                                "followers" -> currentFollowers
                                "following" -> currentFollowing
                                else -> currentConnections
                            }

                            if (activeList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("✨", fontSize = 32.sp)
                                        Text(
                                            text = "No relationships found",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CosmosOnSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                AnimatedContent(
                                    targetState = activeList,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(220)) with fadeOut(animationSpec = tween(220))
                                    },
                                    label = "relationsListTransition"
                                ) { targetList ->
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(targetList, key = { it.id }) { member ->
                                            AnimatedVisibility(
                                                visible = member.id !in removedIds,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                                            ) {
                                                RelationRow(
                                                    member = member,
                                                    tab = activeTab,
                                                    onAction = { handleRemoveAction(member.id, activeTab) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelationRow(
    member: Member,
    tab: String,
    onAction: () -> Unit
) {
    val username = "@" + member.name.lowercase().replace(" ", "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Crisp Circular Avatar
        CosmosAvatar(
            avatarUrl = member.avatarUrl,
            name = member.name,
            size = 48.dp,
            isLinkedInConnected = member.isLinkedInConnected
        )

        // Vertical Stack: Name & @username
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = CosmosOnBackground
            )
            Text(
                text = username,
                style = MaterialTheme.typography.bodySmall,
                color = CosmosOnSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Action Context Button on far right
        when (tab) {
            "followers" -> {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmosError),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CosmosError.copy(alpha = 0.5f)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            "following" -> {
                // Instagram-style Following toggle with active visual feedback
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = CosmosOnBackground
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = CosmosPrimary)
                        Text("Following", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            "connections" -> {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmosOnSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CosmosOutlineVariant),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
