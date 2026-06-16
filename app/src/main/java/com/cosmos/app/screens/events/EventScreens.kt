package com.cosmos.app.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.NetworkEvent
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListScreen(
    onEventTap: (String) -> Unit,
    onPostEventTap: () -> Unit,
    onNavigate: (String) -> Unit,
    eventViewModel: com.cosmos.app.ui.viewmodel.EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val events by eventViewModel.events.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val selectedTypes by eventViewModel.selectedEventTypes.collectAsState()
    val pricingFilter by eventViewModel.pricingFilter.collectAsState()
    val showRegisteredOnly by eventViewModel.showRegisteredOnly.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    val isFilterActive = selectedTypes.isNotEmpty() ||
            pricingFilter != com.cosmos.app.ui.viewmodel.PricingFilter.ALL ||
            showRegisteredOnly

    // Derive filtered events reactively
    val displayEvents = remember(events, selectedTypes, pricingFilter, showRegisteredOnly) {
        eventViewModel.filteredEvents
    }

    CosmosAmbientBackground {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CosmosGlassTopBar(
                    pageTitle = "Events",
                    extraActions = {
                        if (currentUser?.isOrganizer == true) {
                            GlassIconButton(
                                icon = Icons.Default.Add,
                                contentDescription = "Post Event",
                                onClick = onPostEventTap
                            )
                        }
                        // Filter button with active-filter indicator dot
                        Box {
                            GlassIconButton(
                                icon = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                onClick = { showFilterSheet = true }
                            )
                            if (isFilterActive) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(10.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(CosmosPrimary, CosmosGradientStart)
                                            )
                                        )
                                )
                            }
                        }
                    }
                )

                // Active filters summary chip row
                if (isFilterActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = CosmosPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = buildFilterSummary(selectedTypes, pricingFilter, showRegisteredOnly),
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CosmosError.copy(alpha = 0.15f))
                                .clickable { eventViewModel.resetFilters() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall, color = CosmosError)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val featuredEvent = displayEvents.firstOrNull()
                    if (featuredEvent != null) {
                        item {
                            // Featured event
                            Text("Featured", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 8.dp))
                            FeaturedEventCard(event = featuredEvent, onTap = { onEventTap(featuredEvent.id) })
                        }
                    }

                    item {
                        Text("Upcoming", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    }

                    if (displayEvents.isEmpty()) {
                        item {
                            CosmosGlassCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        if (isFilterActive) "🔍" else "📅",
                                        style = MaterialTheme.typography.displaySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (isFilterActive) "No events match your filters"
                                        else "No upcoming events found. Check back later!",
                                        color = CosmosOnSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (isFilterActive) {
                                        Spacer(Modifier.height(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(CosmosGradientStart, CosmosGradientEnd)
                                                    )
                                                )
                                                .clickable { eventViewModel.resetFilters() }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text("Clear Filters", color = CosmosBackground, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(displayEvents) { event ->
                            EventListCard(event = event, onTap = { onEventTap(event.id) })
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            
            if (currentUser?.isOrganizer == true) {
                FloatingActionButton(
                    onClick = onPostEventTap,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .padding(bottom = 80.dp),
                    containerColor = CosmosPrimary,
                    contentColor = CosmosBackground
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Post Event")
                }
            }
        }
    }

    // ── Filter Bottom Sheet ──────────────────────────────────────────────────
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = Color(0xFF16191F),
            contentColor = CosmosOnBackground,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            EventFilterSheetContent(
                selectedTypes = selectedTypes,
                pricingFilter = pricingFilter,
                showRegisteredOnly = showRegisteredOnly,
                onToggleType = { eventViewModel.toggleEventType(it) },
                onSetPricing = { eventViewModel.setPricingFilter(it) },
                onToggleRegistered = { eventViewModel.toggleRegisteredOnly() },
                onReset = { eventViewModel.resetFilters() },
                onApply = { showFilterSheet = false }
            )
        }
    }
}

/** Builds a concise human-readable summary of active filters */
@Composable
private fun buildFilterSummary(
    types: Set<com.cosmos.app.data.model.EventType>,
    pricing: com.cosmos.app.ui.viewmodel.PricingFilter,
    registeredOnly: Boolean
): String {
    val parts = mutableListOf<String>()
    if (types.isNotEmpty()) {
        parts.add(types.joinToString(", ") { it.label })
    }
    if (pricing != com.cosmos.app.ui.viewmodel.PricingFilter.ALL) {
        parts.add(pricing.label)
    }
    if (registeredOnly) {
        parts.add("Registered")
    }
    return parts.joinToString(" · ")
}

/** Premium glassmorphic filter sheet content */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventFilterSheetContent(
    selectedTypes: Set<com.cosmos.app.data.model.EventType>,
    pricingFilter: com.cosmos.app.ui.viewmodel.PricingFilter,
    showRegisteredOnly: Boolean,
    onToggleType: (com.cosmos.app.data.model.EventType) -> Unit,
    onSetPricing: (com.cosmos.app.ui.viewmodel.PricingFilter) -> Unit,
    onToggleRegistered: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CosmosOnSurfaceVariant.copy(alpha = 0.3f))
        )

        Spacer(Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = CosmosPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Filter Events",
                    style = MaterialTheme.typography.titleLarge,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Reset", style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Event Type Section ───────────────────────────────────────────────
        Text(
            "Event Type",
            style = MaterialTheme.typography.labelLarge,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Chips as a flow row
        val allTypes = com.cosmos.app.data.model.EventType.values()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            allTypes.forEach { type ->
                val isSelected = type in selectedTypes
                FilterChip(
                    label = type.label,
                    isSelected = isSelected,
                    onClick = { onToggleType(type) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Pricing Section ──────────────────────────────────────────────────
        Text(
            "Pricing",
            style = MaterialTheme.typography.labelLarge,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.cosmos.app.ui.viewmodel.PricingFilter.values().forEach { filter ->
                val isSelected = pricingFilter == filter
                FilterChip(
                    label = filter.label,
                    isSelected = isSelected,
                    onClick = { onSetPricing(filter) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Registered Only ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CosmosGlass)
                .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleRegistered)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Registered Only", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                Text("Show only events you've joined", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
            }
            Switch(
                checked = showRegisteredOnly,
                onCheckedChange = { onToggleRegistered() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CosmosPrimary,
                    checkedTrackColor = CosmosPrimary.copy(alpha = 0.4f),
                    uncheckedThumbColor = CosmosOnSurfaceVariant,
                    uncheckedTrackColor = CosmosSurfaceContainerHigh
                )
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Apply Button ─────────────────────────────────────────────────────
        CosmosButton(
            text = "Apply Filters",
            onClick = onApply,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
    }
}

/** Reusable filter chip with glassmorphic selected state */
@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) Brush.linearGradient(
                    listOf(CosmosGradientStart.copy(alpha = 0.85f), CosmosGradientEnd.copy(alpha = 0.85f))
                )
                else Brush.linearGradient(
                    listOf(CosmosSurfaceContainerHigh, CosmosSurfaceContainerHigh)
                )
            )
            .border(
                width = 1.dp,
                color = if (isSelected) CosmosPrimary.copy(alpha = 0.5f) else CosmosOutlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else CosmosOnSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun FeaturedEventCard(event: NetworkEvent, onTap: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(CosmosGradientStart.copy(alpha = 0.8f), CosmosGradientEnd.copy(alpha = 0.8f))))
            .clickable(onClick = onTap)
            .padding(20.dp)
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(CosmosBackground.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(event.type.label, style = MaterialTheme.typography.labelSmall, color = CosmosBackground)
                }
                if (event.isPaid) {
                    Text(event.price, style = MaterialTheme.typography.titleSmall, color = CosmosBackground, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(event.title, style = MaterialTheme.typography.headlineSmall, color = CosmosBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(event.description, style = MaterialTheme.typography.bodySmall, color = CosmosBackground.copy(alpha = 0.8f), maxLines = 2)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("📅 ${event.date}", style = MaterialTheme.typography.bodySmall, color = CosmosBackground)
                    Text("📍 ${event.location}", style = MaterialTheme.typography.bodySmall, color = CosmosBackground)
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CosmosBackground).padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("${event.participantCount}/${event.maxParticipants} joined", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary)
                }
            }
        }
    }
}

@Composable
fun EventListCard(event: NetworkEvent, onTap: () -> Unit) {
    CosmosGlassCard(modifier = Modifier.clickable(onClick = onTap), showTopGradientBorder = false) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Date badge
            val cleanDate = event.date
                .replace("Tomorrow, ", "")
                .replace("Today, ", "")
                .replace("Next ", "")
                .replace("Monday, ", "")
                .replace("Tuesday, ", "")
                .replace("Wednesday, ", "")
                .replace("Thursday, ", "")
                .replace("Friday, ", "")
                .replace("Saturday, ", "")
                .replace("Sunday, ", "")
                .trim()
            val dateParts = cleanDate.split(" ")
            val month = dateParts.getOrNull(0)?.take(3)?.uppercase() ?: "JUN"
            val day = dateParts.getOrNull(1)?.trimEnd(',')?.trim() ?: "15"

            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(CosmosSurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(month, style = MaterialTheme.typography.labelSmall, color = CosmosPrimary)
                    Text(day, style = MaterialTheme.typography.titleLarge, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, maxLines = 2, modifier = Modifier.weight(1f))
                    if (event.isPaid) {
                        Text(event.price, style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text(event.time, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                Text(event.location, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CosmosTagChip(text = event.type.label)
                    Text("${event.participantCount} joined", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun EventLobbyScreen(
    eventId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    eventViewModel: com.cosmos.app.ui.viewmodel.EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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
            // Hero
            Box(
                modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CosmosGradientStart.copy(alpha = 0.6f), CosmosGradientEnd.copy(alpha = 0.6f))))
            ) {
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
                                Text("Multiple 15-minute networking rounds with AI-matched participants.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                if (event.isPaid) {
                                    Spacer(Modifier.height(12.dp))
                                    Text("Refund Policy", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.padding(bottom = 4.dp))
                                    Text("Better rated conversations → better refund. Rate your meetings after the event.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                            }
                        }
                    }
                    1 -> {
                        if (participants.isEmpty()) {
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
                            listOf("6:00 PM" to "Registration & Welcome", "6:30 PM" to "Round 1 — AI Matching", "6:50 PM" to "Round 2 — Open Swap", "7:10 PM" to "Round 3 — Industry Focus", "7:30 PM" to "Open Networking", "8:30 PM" to "Feedback & Wrap-up").forEach { (time, activity) ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(time, style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.width(56.dp))
                                    Text(activity, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                                }
                                Divider(color = CosmosOutlineVariant.copy(alpha = 0.2f))
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
                                        CosmosGlassCard(showTopGradientBorder = false) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text("Round ${index + 1}", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.width(64.dp))
                                                CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, size = 40.dp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                                    Text("${round.duration} min · ${if (index == 0) "6:30 PM" else "6:50 PM"}", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEventScreen(
    onBack: () -> Unit,
    onEventPosted: () -> Unit,
    eventViewModel: com.cosmos.app.ui.viewmodel.EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("50") }
    var isPaid by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }
    
    val isCreating by eventViewModel.isCreatingEvent.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = "Post Event",
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    EventTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Event Title",
                        placeholder = "e.g., Founders Meetup",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    EventTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description",
                        placeholder = "What is this event about?",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        EventTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = "Date",
                            placeholder = "e.g., Oct 25",
                            modifier = Modifier.weight(1f)
                        )
                        EventTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = "Time",
                            placeholder = "e.g., 6:00 PM",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    EventTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = "Location",
                        placeholder = "e.g., San Francisco, CA or Zoom",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    EventTextField(
                        value = maxParticipants,
                        onValueChange = { maxParticipants = it.filter { char -> char.isDigit() } },
                        label = "Max Participants",
                        placeholder = "e.g., 50",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Paid Event", color = CosmosOnBackground, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isPaid,
                            onCheckedChange = { isPaid = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CosmosPrimary, checkedTrackColor = CosmosPrimary.copy(alpha = 0.5f))
                        )
                    }
                }
                if (isPaid) {
                    item {
                        EventTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = "Price",
                            placeholder = "e.g., $25",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    CosmosButton(
                        text = if (isCreating) "Posting..." else "Post Event",
                        onClick = {
                            val event = com.cosmos.app.data.model.NetworkEvent(
                                id = "",
                                title = title,
                                description = description,
                                date = date,
                                time = time,
                                location = location,
                                type = com.cosmos.app.data.model.EventType.OPEN_NETWORKING,
                                participantCount = 0,
                                maxParticipants = maxParticipants.toIntOrNull() ?: 50,
                                isPaid = isPaid,
                                price = price,
                                coverUrl = "",
                                tags = listOf("Networking"),
                                createdBy = "",
                                createdAt = 0L
                            )
                            eventViewModel.createEvent(
                                event = event,
                                onSuccess = onEventPosted,
                                onError = { /* handle error */ }
                            )
                        },
                        enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank() && !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CosmosPrimary,
                unfocusedBorderColor = CosmosOutlineVariant,
                focusedTextColor = CosmosOnBackground,
                unfocusedTextColor = CosmosOnBackground,
                cursorColor = CosmosPrimary,
                focusedContainerColor = CosmosSurfaceContainerLow,
                unfocusedContainerColor = CosmosSurfaceContainerLow
            ),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3
        )
    }
}
