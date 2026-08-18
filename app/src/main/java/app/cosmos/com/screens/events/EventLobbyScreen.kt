package app.cosmos.com.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.AuthViewModel
import app.cosmos.com.ui.viewmodel.EventViewModel
import coil.compose.AsyncImage

@Composable
fun EventLobbyScreen(
    eventId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    eventViewModel: EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(eventId) {
        eventViewModel.selectEvent(eventId)
        eventViewModel.loadEventParticipants(eventId)
        eventViewModel.loadEventRounds(eventId)
    }

    val eventState by eventViewModel.activeEvent.collectAsState()
    val participants by eventViewModel.eventParticipants.collectAsState()
    val eventRounds by eventViewModel.eventRounds.collectAsState()
    val currentUserState by authViewModel.currentUser.collectAsState()
    val currentUserId = currentUserState?.id

    if (eventState == null) {
        CosmosAmbientBackground {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CosmosPrimary)
            }
        }
        return
    }

    val event = eventState!!
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Participants", "Schedule", "My Meetings")

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            val isGradientCover = event.coverUrl.startsWith("gradient:") || event.coverUrl.isEmpty()
            val coverGradient = if (event.coverUrl.startsWith("gradient:")) EventGradient.fromId(event.coverUrl) else EventGradient.COSMOS_GLOW

            // Hero
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGradientCover) {
                    Box(modifier = Modifier.matchParentSize().background(coverGradient.brush))
                } else {
                    AsyncImage(
                        model = event.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = CosmosBackground)
                        }
                        if (event.isPaid) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(CosmosBackground.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(event.price, style = MaterialTheme.typography.titleSmall, color = CosmosBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    CosmosTagChip(text = event.type.label, backgroundColor = CosmosBackground.copy(alpha = 0.25f), textColor = CosmosBackground)
                    Spacer(Modifier.height(8.dp))
                    Text(event.title, style = MaterialTheme.typography.headlineMedium, color = CosmosBackground, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("📅 ${event.date}", style = MaterialTheme.typography.bodySmall, color = CosmosBackground.copy(alpha = 0.85f))
                        Text("📍 ${event.location}", style = MaterialTheme.typography.bodySmall, color = CosmosBackground.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(16.dp))
                    // Progress
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { event.participantCount.toFloat() / event.maxParticipants },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = CosmosBackground,
                            trackColor = CosmosBackground.copy(alpha = 0.3f)
                        )
                        Text("${event.participantCount}/${event.maxParticipants}", style = MaterialTheme.typography.labelMedium, color = CosmosBackground)
                    }
                    Spacer(Modifier.height(20.dp))
                    CosmosButton(
                        text = if (event.isRegistered) "✓ Registered" else "Register Now",
                        onClick = { eventViewModel.register(eventId) },
                        enabled = !event.isRegistered
                    )
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CosmosBackground,
                contentColor = CosmosPrimary,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                        selectedContentColor = CosmosPrimary,
                        unselectedContentColor = CosmosOnSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Overview
                        item {
                            CosmosGlassCard(showTopGradientBorder = false) {
                                Text("About This Event", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 8.dp))
                                Text(event.description, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text("Format", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.padding(bottom = 4.dp))
                                val formatText = if (eventRounds.isNotEmpty()) {
                                    "${eventRounds.size} rounds of ${eventRounds.first().duration}-minute networking with AI-matched participants."
                                } else {
                                    "Multiple networking rounds with AI-matched participants."
                                }
                                Text(formatText, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                if (event.isPaid) {
                                    Spacer(Modifier.height(12.dp))
                                    Text("Refund Policy", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.padding(bottom = 4.dp))
                                    Text("Better rated conversations → better refund. Rate your meetings after the event.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                            }
                        }
                    }
                    1 -> {
                        val isCreator = event.createdBy == currentUserId
                        if (!isCreator) {
                            item {
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Text("Only the event organizer can view the participant list.", color = CosmosOnSurfaceVariant)
                                }
                            }
                        } else if (participants.isEmpty()) {
                            item {
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Text("No participants registered yet.", color = CosmosOnSurfaceVariant)
                                }
                            }
                        } else {
                            items(participants) { member ->
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, modifier = Modifier, size = 44.dp, isLinkedInConnected = member.isLinkedInConnected)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                            Text(member.headline, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 1)
                                        }
                                        CosmosTagChip(text = member.tags.firstOrNull() ?: "")
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        item {
                            val scheduleItems = mutableListOf<Pair<String, String>>()
                            var offset = 0
                            
                            scheduleItems.add(addMinutesToTimeString(event.time, offset) to "Registration & Welcome")
                            offset += 15
                            
                            if (eventRounds.isNotEmpty()) {
                                eventRounds.forEach { round ->
                                    val roundTime = addMinutesToTimeString(event.time, offset)
                                    scheduleItems.add(roundTime to "${round.title} (${round.duration} min)")
                                    offset += round.duration
                                }
                            } else {
                                val defaultRounds = listOf(
                                    "Round 1 — AI Matching" to 20,
                                    "Round 2 — Open Swap" to 20,
                                    "Round 3 — Industry Focus" to 20
                                )
                                defaultRounds.forEach { (title, duration) ->
                                    scheduleItems.add(addMinutesToTimeString(event.time, offset) to title)
                                    offset += duration
                                }
                            }
                            
                            scheduleItems.add(addMinutesToTimeString(event.time, offset) to "Open Networking")
                            offset += 60
                            scheduleItems.add(addMinutesToTimeString(event.time, offset) to "Feedback & Wrap-up")
                            
                            Column {
                                scheduleItems.forEach { (time, activity) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(time, style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.width(80.dp))
                                        Text(activity, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                                    }
                                    Divider(color = CosmosOutlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                    3 -> {
                        item {
                            if (!event.isRegistered) {
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text("🔒", style = MaterialTheme.typography.displayLarge)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Register to see your schedule", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                        Text("After registration, your AI-matched meeting schedule will appear here.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                    }
                                }
                            } else {
                                val roundsWithOtherParticipant = eventRounds.mapNotNull { round ->
                                    val hasCurrentUser = round.participants.any { it.id == currentUserId }
                                    if (hasCurrentUser) {
                                        val other = round.participants.firstOrNull { it.id != currentUserId }
                                        if (other != null) round to other else null
                                    } else {
                                        null
                                    }
                                }

                                if (roundsWithOtherParticipant.isEmpty()) {
                                    CosmosGlassCard(showTopGradientBorder = false) {
                                        Text("No meetings scheduled yet.", color = CosmosOnSurfaceVariant, modifier = Modifier.fillMaxWidth())
                                    }
                                } else {
                                    roundsWithOtherParticipant.forEachIndexed { index, (round, member) ->
                                        val roundStartTime = getRoundStartTime(event.time, eventRounds, index)
                                        CosmosGlassCard(showTopGradientBorder = false) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text("Round ${index + 1}", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.width(64.dp))
                                                CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, size = 40.dp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                                    Text("${round.duration} min · $roundStartTime", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
