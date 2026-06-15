package com.cosmos.app.screens.connect

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.Member
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryDeckScreen(
    onProfileTap: (String) -> Unit,
    onNavigateToFeed: () -> Unit,
    onNavigate: (String) -> Unit,
    discoveryViewModel: com.cosmos.app.ui.viewmodel.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    profileViewModel: com.cosmos.app.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    connectionViewModel: com.cosmos.app.ui.viewmodel.ConnectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val members by discoveryViewModel.deck.collectAsState()
    val connectionsLimitCount by discoveryViewModel.connectionsLimitCount.collectAsState()
    val notifications by profileViewModel.notifications.collectAsState()
    val unreadNotificationCount = notifications.count { !it.isRead }
    val incomingRequestCount by connectionViewModel.incomingCount.collectAsState()
    
    var selectedFilter by remember { mutableStateOf("All") }
    
    val filteredMembers = remember(members, selectedFilter) {
        when (selectedFilter) {
            "Founders" -> members.filter { it.isFounder() }
            "Investors" -> members.filter { it.isInvestor() }
            "Operators" -> members.filter { it.isOperator() }
            else -> members
        }
    }
    
    var currentIndex by remember { mutableStateOf(0) }
    var swipeOffset by remember { mutableStateOf(0f) }
    var requestSent by remember { mutableStateOf(false) }
    var requestedMember by remember { mutableStateOf<Member?>(null) }

    LaunchedEffect(filteredMembers) {
        currentIndex = 0
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("COSMOS", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, fontWeight = FontWeight.Bold)
                    Text("Connect", style = MaterialTheme.typography.titleLarge, color = CosmosOnBackground)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onNavigate(Screen.SearchProfiles.route) }) {
                        Icon(Icons.Default.Search, "Search", tint = CosmosOnBackground)
                    }
                    IconButton(onClick = { onNavigate(Screen.Notifications.route) }) {
                        if (unreadNotificationCount > 0) {
                            BadgedBox(badge = { Badge(containerColor = CosmosError) { Text("$unreadNotificationCount") } }) {
                                Icon(Icons.Outlined.Notifications, "Notifications", tint = CosmosOnBackground)
                            }
                        } else {
                            Icon(Icons.Outlined.Notifications, "Notifications", tint = CosmosOnBackground)
                        }
                    }
                    // Pending requests badge
                    IconButton(onClick = { onNavigate(Screen.ConnectionRequests.route) }) {
                        if (incomingRequestCount > 0) {
                            BadgedBox(badge = { Badge(containerColor = CosmosPrimary) { Text("$incomingRequestCount") } }) {
                                Icon(Icons.Outlined.PersonAdd, "Requests", tint = CosmosOnBackground)
                            }
                        } else {
                            Icon(Icons.Outlined.PersonAdd, "Requests", tint = CosmosOnBackground)
                        }
                    }
                    IconButton(onClick = onNavigateToFeed) {
                        Icon(Icons.Outlined.People, "Feed", tint = CosmosOnBackground)
                    }
                }
            }

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Founders", "Investors", "Operators").forEach { filter ->
                    val isSelected = filter == selectedFilter
                    CosmosTagChip(
                        text = filter,
                        backgroundColor = if (isSelected) CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                        textColor = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            // Monthly connection limit indicator
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$connectionsLimitCount of 10 connections this month", style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant)
                val progress = (connectionsLimitCount.toFloat() / 10f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(100.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = CosmosPrimary,
                    trackColor = CosmosSurfaceContainerHigh
                )
            }

            Spacer(Modifier.height(16.dp))

            // Swipe card area
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentIndex < filteredMembers.size) {
                    // Background card (stack effect — visually behind front card)
                    if (currentIndex + 1 < filteredMembers.size) {
                        MemberSwipeCard(
                            member = filteredMembers[currentIndex + 1],
                            onTap = {},
                            modifier = Modifier
                                .offset(y = 12.dp)
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = 0.93f,
                                    scaleY = 0.93f
                                )
                                .alpha(0.5f),
                            swipeOffset = 0f,
                            isBackground = true
                        )
                    }
                    // Front card
                    MemberSwipeCard(
                        member = filteredMembers[currentIndex],
                        onTap = { onProfileTap(filteredMembers[currentIndex].id) },
                        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val currentMember = filteredMembers[currentIndex]
                                    if (swipeOffset > 150) {
                                        val currentMember = filteredMembers[currentIndex]
                                        connectionViewModel.sendRequest(
                                            receiverId = currentMember.id,
                                            onSuccess = {
                                                requestedMember = currentMember
                                                requestSent = true
                                            }
                                        )
                                        // Also record skip swipe so they don't appear again in deck
                                        discoveryViewModel.swipeRight(currentMember.id) { _ -> }
                                        currentIndex++
                                    } else if (swipeOffset < -150) {
                                        discoveryViewModel.swipeLeft(currentMember.id)
                                        currentIndex++
                                    }
                                    swipeOffset = 0f
                                }
                            ) { _, dragAmount -> swipeOffset += dragAmount }
                        },
                        swipeOffset = swipeOffset
                    )
                } else {
                    // Empty state
                    CosmosGlassCard {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("✨", style = MaterialTheme.typography.displayLarge)
                            Spacer(Modifier.height(16.dp))
                            Text("You're all caught up!", style = MaterialTheme.typography.titleLarge, color = CosmosOnBackground)
                            Text("New curated matches arrive weekly. Check back soon.", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        }
                    }
                }
            }

            // Action buttons
            if (currentIndex < filteredMembers.size) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip button
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp))
                            .background(CosmosSurfaceContainerHigh)
                            .border(1.dp, CosmosOutlineVariant, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            if (currentIndex < filteredMembers.size) {
                                discoveryViewModel.swipeLeft(filteredMembers[currentIndex].id)
                                currentIndex++
                            }
                        }) {
                            Icon(Icons.Default.Close, "Skip", tint = CosmosError, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Profile button (center, smaller)
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                            .background(CosmosSurfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { if (currentIndex < filteredMembers.size) onProfileTap(filteredMembers[currentIndex].id) }) {
                            Icon(Icons.Default.Person, "View Profile", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Connect button
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp))
                            .background(Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            if (currentIndex < filteredMembers.size) {
                                val currentMember = filteredMembers[currentIndex]
                                connectionViewModel.sendRequest(
                                    receiverId = currentMember.id,
                                    onSuccess = {
                                        requestedMember = currentMember
                                        requestSent = true
                                    }
                                )
                                discoveryViewModel.swipeRight(currentMember.id) { _ -> }
                                currentIndex++
                            }
                        }) {
                            Icon(Icons.Default.PersonAdd, "Connect", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }

    // Request sent confirmation overlay
    if (requestSent && requestedMember != null) {
        RequestSentOverlay(
            member = requestedMember!!,
            onViewRequests = {
                requestSent = false
                onNavigate(Screen.ConnectionRequests.route)
            },
            onContinueDiscovering = { requestSent = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberSwipeCard(
    member: Member,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    swipeOffset: Float = 0f,
    isBackground: Boolean = false
) {
    val rotationDeg = swipeOffset / 20f
    val likeAlpha = (swipeOffset / 200f).coerceIn(0f, 1f)
    val nopeAlpha = (-swipeOffset / 200f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .rotate(rotationDeg)
            .offset(x = (swipeOffset / 3).dp)
    ) {
        CosmosGlassCard(showTopGradientBorder = true) {
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = if (isBackground) 0f else 1f
                }
            ) {
                // LIKE / NOPE overlay text
            if (likeAlpha > 0.1f) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .background(Color(0xFF4CAF6F).copy(alpha = likeAlpha))
                )
            }

            // Avatar + Name row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CosmosAvatar(
                    avatarUrl = member.avatarUrl,
                    name = member.name,
                    size = 72.dp,
                    isLinkedInConnected = member.isLinkedInConnected,
                    membershipTierColor = CosmosPrimary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleLarge, color = CosmosOnBackground)
                    Text(member.headline, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocationOn, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(member.location, style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tags
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                member.tags.take(4).forEach { tag ->
                    CosmosTagChip(text = "#$tag")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Goal statement
            Text("Looking for", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary)
            Text(member.goalStatement, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(16.dp))

            // Footer info
            Divider(color = CosmosOutlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.People, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text("${member.mutualConnectionsCount} mutual", style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, null, tint = CosmosTertiary, modifier = Modifier.size(16.dp))
                    Text(member.membershipTier.label, style = MaterialTheme.typography.labelMedium, color = CosmosTertiary)
                }
            }

            // Why recommended info
            Spacer(Modifier.height(12.dp))
            CosmosGlassCard(showTopGradientBorder = false) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, "Why recommended", tint = CosmosPrimary, modifier = Modifier.size(16.dp))
                    Text(
                        "Recommended because: shared AI & SaaS interests, ${member.mutualConnectionsCount} mutual connections",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant
                    )
                }
            }
            }
        }
    }
}

// Removed broken scale() extension — using graphicsLayer instead

@Composable
fun RequestSentOverlay(
    member: Member,
    onViewRequests: () -> Unit,
    onContinueDiscovering: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(CosmosBackground.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF6F), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Request Sent!", style = MaterialTheme.typography.headlineMedium, color = CosmosPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            CosmosAvatar(
                avatarUrl = member.avatarUrl,
                name = member.name,
                size = 56.dp,
                isLinkedInConnected = member.isLinkedInConnected
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your connection request has been sent to ${member.name}. They'll be notified and can accept your request.",
                style = MaterialTheme.typography.bodyMedium,
                color = CosmosOnSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            CosmosButton(text = "View Sent Requests", onClick = onViewRequests, icon = Icons.Default.People)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onContinueDiscovering) {
                Text("Continue Discovering", color = CosmosOnSurfaceVariant)
            }
        }
    }
}

private fun Member.isFounder(): Boolean {
    val type = primaryUserType.lowercase()
    val roleLower = role.lowercase()
    val headlineLower = headline.lowercase()
    val tagsLower = tags.map { it.lowercase() }
    return type.contains("founder") || 
           roleLower.contains("founder") || 
           roleLower.contains("ceo") || 
           headlineLower.contains("founder") || 
           headlineLower.contains("ceo") || 
           tagsLower.any { it.contains("founder") }
}

private fun Member.isInvestor(): Boolean {
    val type = primaryUserType.lowercase()
    val roleLower = role.lowercase()
    val headlineLower = headline.lowercase()
    val tagsLower = tags.map { it.lowercase() }
    return type.contains("investor") || 
           roleLower.contains("investor") || 
           roleLower.contains("partner") || 
           roleLower.contains("venture") || 
           headlineLower.contains("investor") || 
           headlineLower.contains("partner") || 
           headlineLower.contains("venture") || 
           tagsLower.any { it.contains("investor") }
}

private fun Member.isOperator(): Boolean {
    val type = primaryUserType.lowercase()
    val roleLower = role.lowercase()
    val headlineLower = headline.lowercase()
    val tagsLower = tags.map { it.lowercase() }
    return type.contains("operator") || 
           type.contains("professional") || 
           type.contains("freelancer") || 
           type.contains("creator") || 
           type.contains("service provider") || 
           roleLower.contains("operator") || 
           roleLower.contains("product") || 
           roleLower.contains("engineer") || 
           roleLower.contains("manager") || 
           roleLower.contains("head") || 
           roleLower.contains("director") || 
           roleLower.contains("vp") || 
           headlineLower.contains("operator") || 
           headlineLower.contains("product") || 
           headlineLower.contains("engineer") || 
           headlineLower.contains("manager") || 
           headlineLower.contains("head") || 
           headlineLower.contains("director") || 
           headlineLower.contains("vp") || 
           tagsLower.any { it.contains("operator") || it.contains("product") || it.contains("growth") || it.contains("engineer") } ||
           (!isFounder() && !isInvestor() && (role.isNotEmpty() || tags.isNotEmpty() || headline.isNotEmpty()))
}
