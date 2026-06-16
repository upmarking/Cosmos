package com.cosmos.app.screens.connect

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    connectionViewModel: com.cosmos.app.ui.viewmodel.ConnectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    searchViewModel: com.cosmos.app.ui.viewmodel.SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val members by discoveryViewModel.deck.collectAsState()
    val connectionsLimitCount by discoveryViewModel.connectionsLimitCount.collectAsState()
    val notifications by profileViewModel.notifications.collectAsState()
    val unreadNotificationCount = notifications.count { !it.isRead }
    val incomingRequestCount by connectionViewModel.incomingCount.collectAsState()
    val currentUser by discoveryViewModel.currentUser.collectAsState(initial = null)
    val monthlyLimit = currentUser?.monthlyConnectionLimit ?: 10

    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()

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
    var showLimitExceededDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    LaunchedEffect(filteredMembers) {
        currentIndex = 0
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosGlassTopBar(
                pageTitle = "Discover",
                notificationCount = unreadNotificationCount,
                requestCount     = incomingRequestCount,
                onSearchClick       = { showSearchBar = true },
                onNotificationsClick = { onNavigate(Screen.Notifications.route) },
                onRequestsClick     = { onNavigate(Screen.ConnectionRequests.route) },
                isSearchActive       = showSearchBar,
                searchQuery          = searchQuery,
                onSearchQueryChange  = { searchViewModel.updateQuery(it) },
                onSearchClose        = {
                    showSearchBar = false
                    searchViewModel.clearSearch()
                },
                searchPlaceholder    = "Search by name, headline, tags...",
                extraActions = {
                    if (!showSearchBar) {
                        GlassIconButton(
                            icon = Icons.Outlined.People,
                            contentDescription = "Feed",
                            onClick = onNavigateToFeed
                        )
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Main Deck Content
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Glassmorphic filter chip rail ─────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF16191F).copy(alpha = 0.72f))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("All", "Founders", "Investors", "Operators").forEach { filter ->
                            val isSelected = filter == selectedFilter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected)
                                            Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd))
                                        else
                                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .clickable { selectedFilter = filter }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else CosmosOnSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // ── Aurora connection limit bar ───────────────────────────────────
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$connectionsLimitCount / 10 connections",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant
                        )
                        val progress = (connectionsLimitCount.toFloat() / 10f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.width(120.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                                .background(CosmosSurfaceContainerHigh)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(CosmosGradientStart, CosmosGradientEnd, CosmosPrimary)
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

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
                                                if (connectionsLimitCount >= monthlyLimit) {
                                                    showLimitExceededDialog = true
                                                    swipeOffset = 0f
                                                } else {
                                                    connectionViewModel.sendRequest(
                                                        receiverId = currentMember.id,
                                                        onSuccess = {
                                                            requestedMember = currentMember
                                                            requestSent = true
                                                        }
                                                    )
                                                    // Also record swipe so they don't appear again in deck
                                                    discoveryViewModel.swipeRight(currentMember.id) { _ -> }
                                                    currentIndex++
                                                }
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
                                        if (connectionsLimitCount >= monthlyLimit) {
                                            showLimitExceededDialog = true
                                        } else {
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
                                    }
                                }) {
                                    Icon(Icons.Default.PersonAdd, "Connect", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(96.dp))
                }

                // Inline Search Results Overlay
                if (showSearchBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CosmosBackground)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = CosmosPrimary
                            )
                        } else if (searchQuery.trim().length >= 2 && searchResults.isEmpty()) {
                            Text(
                                text = "No results found for \"$searchQuery\"",
                                color = CosmosOnSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (searchResults.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(searchResults) { member ->
                                    SearchResultCard(member = member, onTap = { onProfileTap(member.id) })
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = CosmosOnSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Type to search for members...",
                                    color = CosmosOnSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
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

    if (showLimitExceededDialog) {
        AlertDialog(
            onDismissRequest = { showLimitExceededDialog = false },
            title = {
                Text("Monthly Limit Reached", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("You have reached your monthly limit of $monthlyLimit connections. Upgrade to a premium membership to unlock more connections and gain exclusive features.", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
            },
            confirmButton = {
                TextButton(onClick = {
                    showLimitExceededDialog = false
                    onNavigate(Screen.MembershipTiers.route)
                }) {
                    Text("Upgrade Tier", color = CosmosPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitExceededDialog = false }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            containerColor = CosmosSurfaceContainerLow
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
