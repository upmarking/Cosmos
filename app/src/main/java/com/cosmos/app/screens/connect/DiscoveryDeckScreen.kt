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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.SampleData
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryDeckScreen(
    onProfileTap: (String) -> Unit,
    onNavigateToFeed: () -> Unit,
    onNavigate: (String) -> Unit,
    discoveryViewModel: com.cosmos.app.ui.viewmodel.DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val members by discoveryViewModel.deck.collectAsState()
    val connectionsLimitCount by discoveryViewModel.connectionsLimitCount.collectAsState()
    
    var currentIndex by remember { mutableStateOf(0) }
    var swipeOffset by remember { mutableStateOf(0f) }
    var matchMade by remember { mutableStateOf(false) }
    var matchedMember by remember { mutableStateOf<Member?>(null) }

    LaunchedEffect(members) {
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
                    IconButton(onClick = { onNavigate(Screen.Notifications.route) }) {
                        BadgedBox(badge = { Badge(containerColor = CosmosError) { Text("2") } }) {
                            Icon(Icons.Outlined.Notifications, "Notifications", tint = CosmosOnBackground)
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
                    CosmosTagChip(
                        text = filter,
                        backgroundColor = if (filter == "All") CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                        textColor = if (filter == "All") CosmosPrimary else CosmosOnSurfaceVariant
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
                if (currentIndex < members.size) {
                    // Background cards (stack effect)
                    if (currentIndex + 1 < members.size) {
                        MemberSwipeCard(
                            member = members[currentIndex + 1],
                            onTap = {},
                            modifier = Modifier.offset(y = 16.dp).fillMaxWidth().scale(0.95f),
                            swipeOffset = 0f
                        )
                    }
                    // Front card
                    MemberSwipeCard(
                        member = members[currentIndex],
                        onTap = { onProfileTap(members[currentIndex].id) },
                        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val currentMember = members[currentIndex]
                                    if (swipeOffset > 150) {
                                        discoveryViewModel.swipeRight(currentMember.id) { member ->
                                            matchedMember = member
                                            matchMade = true
                                        }
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
            if (currentIndex < members.size) {
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
                            if (currentIndex < members.size) {
                                discoveryViewModel.swipeLeft(members[currentIndex].id)
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
                        IconButton(onClick = { if (currentIndex < members.size) onProfileTap(members[currentIndex].id) }) {
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
                            if (currentIndex < members.size) {
                                val currentMember = members[currentIndex]
                                discoveryViewModel.swipeRight(currentMember.id) { member ->
                                    matchedMember = member
                                    matchMade = true
                                }
                                currentIndex++
                            }
                        }) {
                            Icon(Icons.Default.Favorite, "Connect", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }

    // Match celebration overlay
    if (matchMade && matchedMember != null) {
        MatchCelebrationOverlay(
            member = matchedMember!!,
            onStartChat = {
                matchMade = false
                onNavigate(Screen.CrmChat.createRoute(matchedMember!!.id))
            },
            onContinueDiscovering = { matchMade = false }
        )
    }
}

@Composable
fun MemberSwipeCard(
    member: Member,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    swipeOffset: Float = 0f
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

@Composable
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.wrapContentSize(Alignment.Center)
)

@Composable
fun MatchCelebrationOverlay(
    member: Member,
    onStartChat: () -> Unit,
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
            Text("🎉", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(16.dp))
            Text("It's a Match!", style = MaterialTheme.typography.headlineLarge, color = CosmosPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "You and ${member.name} are both interested in connecting.",
                style = MaterialTheme.typography.bodyLarge,
                color = CosmosOnSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            CosmosButton(text = "Start Conversation", onClick = onStartChat, icon = Icons.Default.Chat)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onContinueDiscovering) {
                Text("Continue Discovering", color = CosmosOnSurfaceVariant)
            }
        }
    }
}
