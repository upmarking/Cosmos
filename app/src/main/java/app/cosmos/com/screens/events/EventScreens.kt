package app.cosmos.com.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cosmos.com.data.model.NetworkEvent
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import androidx.compose.runtime.collectAsState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListScreen(
    onEventTap: (String) -> Unit,
    onPostEventTap: () -> Unit,
    onNavigate: (String) -> Unit,
    eventViewModel: app.cosmos.com.ui.viewmodel.EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val events by eventViewModel.events.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val selectedTypes by eventViewModel.selectedEventTypes.collectAsState()
    val pricingFilter by eventViewModel.pricingFilter.collectAsState()
    val showRegisteredOnly by eventViewModel.showRegisteredOnly.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    val isFilterActive = selectedTypes.isNotEmpty() ||
            pricingFilter != app.cosmos.com.ui.viewmodel.PricingFilter.ALL ||
            showRegisteredOnly

    // Derive filtered events reactively
    val displayEvents = remember(events, selectedTypes, pricingFilter, showRegisteredOnly) {
        eventViewModel.filteredEvents
    }

    CosmosAmbientBackground {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CosmosGlassTopBar(
                    pageTitle = "Organize",
                    extraActions = {
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
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // ── Your Events Section ──────────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Events",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = CosmosOnBackground
                            )
                            Text(
                                text = "View All",
                                style = MaterialTheme.typography.labelMedium,
                                color = CosmosOnSurfaceVariant,
                                modifier = Modifier.clickable { /* no-op */ }
                            )
                        }
                    }

                    val registeredEvents = events.filter { it.isRegistered }
                    if (registeredEvents.isEmpty()) {
                        item {
                            YourEventsEmptyCard()
                        }
                    } else {
                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(registeredEvents) { event ->
                                    YourEventCard(event = event, onTap = { onEventTap(event.id) })
                                }
                            }
                        }
                    }

                    // ── Picked for You / Explorer Section ─────────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 24.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "Picked for You",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = CosmosOnBackground
                            )
                            Text(
                                text = "Around the World",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CosmosOnSurfaceVariant
                            )
                        }
                    }

                    if (displayEvents.isEmpty()) {
                        item {
                            ExploreEventsEmptyState(
                                isFilterActive = isFilterActive,
                                onClearFilters = { eventViewModel.resetFilters() }
                            )
                        }
                    } else {
                        // Date grouping
                        val groups = mutableMapOf<java.util.Date, MutableList<NetworkEvent>>()
                        for (event in displayEvents) {
                            val dateVal = parseAndroidEventDate(event.date) ?: continue
                            val midnight = truncateToMidnight(dateVal)
                            if (!groups.containsKey(midnight)) {
                                groups[midnight] = mutableListOf()
                            }
                            groups[midnight]?.add(event)
                        }
                        val sortedGroups = groups.entries.sortedBy { it.key }

                        sortedGroups.forEach { entry ->
                            val headerLabel = getAndroidDayHeaderLabel(entry.key)
                            item {
                                Text(
                                    text = headerLabel,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = CosmosOnSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                            items(entry.value) { event ->
                                LumaEventRow(event = event, onTap = { onEventTap(event.id) })
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }

            if (currentUser != null) {
                FloatingActionButton(
                    onClick = onPostEventTap,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .padding(bottom = 80.dp),
                    containerColor = CosmosPrimary,
                    contentColor = CosmosBackground,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Create Event", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
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
    types: Set<app.cosmos.com.data.model.EventType>,
    pricing: app.cosmos.com.ui.viewmodel.PricingFilter,
    registeredOnly: Boolean
): String {
    val parts = mutableListOf<String>()
    if (types.isNotEmpty()) {
        parts.add(types.joinToString(", ") { it.label })
    }
    if (pricing != app.cosmos.com.ui.viewmodel.PricingFilter.ALL) {
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
    selectedTypes: Set<app.cosmos.com.data.model.EventType>,
    pricingFilter: app.cosmos.com.ui.viewmodel.PricingFilter,
    showRegisteredOnly: Boolean,
    onToggleType: (app.cosmos.com.data.model.EventType) -> Unit,
    onSetPricing: (app.cosmos.com.ui.viewmodel.PricingFilter) -> Unit,
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
        val allTypes = app.cosmos.com.data.model.EventType.values()
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
            app.cosmos.com.ui.viewmodel.PricingFilter.values().forEach { filter ->
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
    val isGradientCover = event.coverUrl.startsWith("gradient:") || event.coverUrl.isEmpty()
    val coverGradient = if (event.coverUrl.startsWith("gradient:")) EventGradient.fromId(event.coverUrl) else EventGradient.COSMOS_GLOW

    val backgroundModifier = if (event.coverUrl.isNotEmpty() && !isGradientCover) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onTap)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(coverGradient.brush)
            .clickable(onClick = onTap)
    }

    Box(
        modifier = backgroundModifier
    ) {
        if (event.coverUrl.isNotEmpty() && !isGradientCover) {
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
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        val textColor = Color.White
        val subTextColor = Color.White.copy(alpha = 0.8f)
        val tagBgColor = Color.White.copy(alpha = 0.2f)
        val tagTextColor = Color.White
        val joinedContainerBg = Color.White.copy(alpha = 0.15f)
        val joinedTextColor = Color.White

        Column(modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tagBgColor).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(event.type.label, style = MaterialTheme.typography.labelSmall, color = tagTextColor)
                }
                if (event.isPaid) {
                    Text(event.price, style = MaterialTheme.typography.titleSmall, color = textColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(event.title, style = MaterialTheme.typography.headlineSmall, color = textColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(event.description, style = MaterialTheme.typography.bodySmall, color = subTextColor, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("📅 ${event.date}", style = MaterialTheme.typography.bodySmall, color = textColor)
                    Text("📍 ${event.location}", style = MaterialTheme.typography.bodySmall, color = textColor)
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(joinedContainerBg).padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("${event.participantCount}/${event.maxParticipants} joined", style = MaterialTheme.typography.labelMedium, color = joinedTextColor)
                }
            }
        }
    }
}

@Composable
fun EventListCard(event: NetworkEvent, onTap: () -> Unit) {
    CosmosGlassCard(modifier = Modifier.clickable(onClick = onTap), showTopGradientBorder = false) {
        Column {
            val isGradientCover = event.coverUrl.startsWith("gradient:") || event.coverUrl.isEmpty()
            val coverGradient = if (event.coverUrl.startsWith("gradient:")) EventGradient.fromId(event.coverUrl) else EventGradient.COSMOS_GLOW

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (isGradientCover) {
                    Box(modifier = Modifier.fillMaxSize().background(coverGradient.brush))
                } else {
                    AsyncImage(
                        model = event.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
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
}

@Composable
fun EventLobbyScreen(
    eventId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    eventViewModel: app.cosmos.com.ui.viewmodel.EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEventScreen(
    onBack: () -> Unit,
    onEventPosted: () -> Unit,
    eventViewModel: app.cosmos.com.ui.viewmodel.EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageZoom by remember { mutableStateOf(1f) }
    var imagePanFraction by remember { mutableStateOf(0f) }
    var selectedGradient by remember { mutableStateOf(EventGradient.COSMOS_GLOW) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> 
            selectedImageUri = uri 
            imageZoom = 1f
            imagePanFraction = 0f
        }
    )

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var locationSuggestions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(location) {
        if (location.length > 2 && !location.contains("zoom", ignoreCase = true) && !location.contains("meet.google", ignoreCase = true) && !location.contains("http", ignoreCase = true)) {
            kotlinx.coroutines.delay(500)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val encoded = java.net.URLEncoder.encode(location, "UTF-8")
                    val url = java.net.URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=5&email=contact@cosmos.app")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "CosmosApp/1.0 (contact@cosmos.app)")
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(response)
                    val suggestions = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        suggestions.add(jsonArray.getJSONObject(i).getString("display_name"))
                    }
                    locationSuggestions = suggestions
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        } else {
            locationSuggestions = emptyList()
        }
    }
    var maxParticipants by remember { mutableStateOf("50") }
    var isPaid by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    
    val isCreating by eventViewModel.isCreatingEvent.collectAsState()

    var showAiDialog by remember { mutableStateOf(false) }
    var aiPromptInput by remember { mutableStateOf("") }
    var isGeneratingDescription by remember { mutableStateOf(false) }

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
                    Column {
                        Text(
                            text = "Event Cover Image",
                            style = MaterialTheme.typography.labelMedium,
                            color = CosmosOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        if (selectedImageUri != null) {
                            Column {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CosmosSurfaceContainerHigh)
                                        .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
                                ) {
                                    val containerHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
                                    AsyncImage(
                                        model = selectedImageUri,
                                        contentDescription = "Cover Image Preview",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = imageZoom,
                                                scaleY = imageZoom,
                                                translationY = imagePanFraction * containerHeightPx
                                            )
                                    )
                                    IconButton(
                                        onClick = { 
                                            selectedImageUri = null
                                            imageZoom = 1f
                                            imagePanFraction = 0f
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove cover image",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Zoom",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CosmosOnSurfaceVariant
                                            )
                                            Text(
                                                "${(imageZoom * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CosmosPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Slider(
                                            value = imageZoom,
                                            onValueChange = { imageZoom = it },
                                            valueRange = 1f..3f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = CosmosPrimary,
                                                activeTrackColor = CosmosPrimary,
                                                inactiveTrackColor = CosmosSurfaceContainerHigh
                                            )
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Reposition (Vertical Offset)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CosmosOnSurfaceVariant
                                            )
                                            Text(
                                                "${(imagePanFraction * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CosmosPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Slider(
                                            value = imagePanFraction,
                                            onValueChange = { imagePanFraction = it },
                                            valueRange = -0.5f..0.5f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = CosmosPrimary,
                                                activeTrackColor = CosmosPrimary,
                                                inactiveTrackColor = CosmosSurfaceContainerHigh
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CosmosSurfaceContainerLow)
                                    .border(
                                        width = 1.dp,
                                        color = CosmosOutlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = CosmosPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Add Event Cover Image",
                                        color = CosmosOnSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            text = "Or Select Default Cover Theme",
                            style = MaterialTheme.typography.labelMedium,
                            color = CosmosOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EventGradient.values().forEach { gradient ->
                                val isSelected = selectedGradient == gradient && selectedImageUri == null
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(2f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(gradient.brush)
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelected) CosmosPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedGradient = gradient
                                            selectedImageUri = null
                                        }
                                )
                            }
                        }
                    }
                }

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
                        singleLine = false,
                        headerAction = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmosPrimary.copy(alpha = 0.15f))
                                    .clickable { showAiDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Generate",
                                    tint = CosmosPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "AI Generate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CosmosPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        EventPickerField(
                            label = "Date",
                            value = date,
                            placeholder = "Select Date",
                            icon = Icons.Default.Event,
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        EventPickerField(
                            label = "Time",
                            value = time,
                            placeholder = "Select Time",
                            icon = Icons.Default.Schedule,
                            onClick = { showTimePicker = true },
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
                if (locationSuggestions.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CosmosOutlineVariant, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CosmosSurfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                locationSuggestions.forEach { suggestion ->
                                    Text(
                                        text = suggestion,
                                        color = CosmosOnBackground,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                location = suggestion
                                                locationSuggestions = emptyList()
                                            }
                                            .padding(8.dp)
                                    )
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        color = CosmosOutlineVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
                if (location.isNotBlank() && !location.contains("zoom", ignoreCase = true) && !location.contains("meet.google", ignoreCase = true) && !location.contains("http", ignoreCase = true)) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, CosmosOutlineVariant, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CosmosSurfaceContainerLow)
                        ) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { context ->
                                    android.webkit.WebView(context).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        webViewClient = android.webkit.WebViewClient()
                                        settings.javaScriptEnabled = true
                                    }
                                },
                                update = { webView ->
                                    try {
                                        val encodedLoc = java.net.URLEncoder.encode(location, "UTF-8")
                                        val mapUrl = "https://maps.google.com/maps?q=$encodedLoc&t=&z=13&ie=UTF8&iwloc=&output=embed"
                                        webView.loadUrl(mapUrl)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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
                            val imageBytes = selectedImageUri?.let { uri ->
                                cropEventBitmap(context, uri, imageZoom, imagePanFraction)
                            }
                            val event = app.cosmos.com.data.model.NetworkEvent(
                                id = "",
                                title = title,
                                description = description,
                                date = date,
                                time = time,
                                location = location,
                                type = app.cosmos.com.data.model.EventType.OPEN_NETWORKING,
                                participantCount = 0,
                                maxParticipants = maxParticipants.toIntOrNull() ?: 50,
                                isPaid = isPaid,
                                price = price,
                                coverUrl = if (selectedImageUri != null) "" else selectedGradient.id,
                                tags = listOf("Networking"),
                                createdBy = "",
                                createdAt = 0L
                            )
                            eventViewModel.createEventWithImage(
                                event = event,
                                imageBytes = imageBytes,
                                onSuccess = onEventPosted,
                                onError = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank() && !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                            utcCal.timeInMillis = millis
                            
                            val localCal = java.util.Calendar.getInstance()
                            localCal.set(utcCal.get(java.util.Calendar.YEAR), utcCal.get(java.util.Calendar.MONTH), utcCal.get(java.util.Calendar.DAY_OF_MONTH))
                            
                            val format = SimpleDateFormat("MMM d, yyyy", Locale.US)
                            date = format.format(localCal.time)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", color = CosmosPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color(0xFF16191F),
                titleContentColor = CosmosOnBackground,
                headlineContentColor = CosmosOnBackground,
                weekdayContentColor = CosmosOnSurfaceVariant,
                subheadContentColor = CosmosOnSurfaceVariant,
                navigationContentColor = CosmosOnBackground,
                yearContentColor = CosmosOnSurfaceVariant,
                selectedYearContentColor = Color.White,
                selectedYearContainerColor = CosmosPrimaryContainer,
                dayContentColor = CosmosOnBackground,
                selectedDayContentColor = Color.White,
                selectedDayContainerColor = CosmosGradientStart,
                todayContentColor = CosmosPrimary,
                todayDateBorderColor = CosmosPrimary
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                        val format = SimpleDateFormat("h:mm a", Locale.US)
                        time = format.format(cal.time)
                        showTimePicker = false
                    }
                ) {
                    Text("Select", color = CosmosPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            title = {
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = CosmosSurfaceContainerHigh,
                            clockDialSelectedContentColor = Color.White,
                            clockDialUnselectedContentColor = CosmosOnSurfaceVariant,
                            selectorColor = CosmosPrimary,
                            periodSelectorBorderColor = CosmosOutlineVariant,
                            periodSelectorSelectedContainerColor = CosmosPrimaryContainer,
                            periodSelectorUnselectedContainerColor = CosmosSurfaceContainerLow,
                            periodSelectorSelectedContentColor = Color.White,
                            periodSelectorUnselectedContentColor = CosmosOnSurfaceVariant,
                            timeSelectorSelectedContainerColor = CosmosPrimaryContainer,
                            timeSelectorUnselectedContainerColor = CosmosSurfaceContainerLow,
                            timeSelectorSelectedContentColor = Color.White,
                            timeSelectorUnselectedContentColor = CosmosOnSurfaceVariant
                        )
                    )
                }
            },
            containerColor = Color(0xFF16191F),
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { if (!isGeneratingDescription) showAiDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CosmosPrimary
                    )
                    Text(
                        text = "AI Description Generator",
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Specify key topics, target audience, or keywords to guide the AI, or leave blank to generate based on Title & Location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aiPromptInput,
                        onValueChange = { aiPromptInput = it },
                        placeholder = { Text("e.g. networking, startup pitch tips, VC speakers", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
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
                        singleLine = false,
                        minLines = 2
                    )
                    if (isGeneratingDescription) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CosmosPrimary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Drafting description with AI...",
                                style = MaterialTheme.typography.bodySmall,
                                color = CosmosPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isGeneratingDescription = true
                        eventViewModel.generateEventDescription(
                            title = title.ifBlank { "Exclusive Meetup" },
                            location = location.ifBlank { "Virtual" },
                            details = aiPromptInput,
                            onSuccess = { generatedText ->
                                description = generatedText
                                isGeneratingDescription = false
                                showAiDialog = false
                            },
                            onError = { errorMsg ->
                                isGeneratingDescription = false
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isGeneratingDescription,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
                ) {
                    Text("Generate", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAiDialog = false },
                    enabled = !isGeneratingDescription
                ) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            containerColor = Color(0xFF16191F),
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun EventPickerField(
    label: String,
    value: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CosmosSurfaceContainerLow)
                .border(1.dp, CosmosOutlineVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = value,
                        color = CosmosOnBackground,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CosmosPrimary,
                    modifier = Modifier.size(20.dp)
                )
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
    singleLine: Boolean = true,
    headerAction: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = CosmosOnSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            if (headerAction != null) {
                headerAction()
            }
        }
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

fun addMinutesToTimeString(timeStr: String, minutesToAdd: Int): String {
    val regex = """(\d{1,2}):(\d{2})\s*(AM|PM)(?:\s+(\w+))?""".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.find(timeStr.trim())
    if (match != null) {
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val amPm = match.groupValues[3].uppercase()
        val tz = match.groupValues[4]
        
        if (amPm == "PM" && hour < 12) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0
        
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
        calendar.set(java.util.Calendar.MINUTE, minute)
        calendar.add(java.util.Calendar.MINUTE, minutesToAdd)
        
        val newHour24 = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val newMinute = calendar.get(java.util.Calendar.MINUTE)
        
        val newAmPm = if (newHour24 >= 12) "PM" else "AM"
        var newHour12 = newHour24 % 12
        if (newHour12 == 0) newHour12 = 12
        
        val timeFormatted = String.format(java.util.Locale.US, "%d:%02d %s", newHour12, newMinute, newAmPm)
        return if (!tz.isNullOrEmpty()) "$timeFormatted $tz" else timeFormatted
    }
    return timeStr
}

fun getRoundStartTime(eventTime: String, rounds: List<app.cosmos.com.data.model.EventRound>, roundIndex: Int): String {
    var offset = 15 // 15 mins for welcome session
    for (i in 0 until roundIndex) {
        offset += rounds.getOrNull(i)?.duration ?: 15
    }
    return addMinutesToTimeString(eventTime, offset)
}

fun cropEventBitmap(context: android.content.Context, uri: Uri, zoom: Float, panFraction: Float): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val sourceBitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return null
        
        val W_orig = sourceBitmap.width.toFloat()
        val H_orig = sourceBitmap.height.toFloat()
        
        val W_canvas = 800f
        val H_canvas = 400f
        
        val R_img = W_orig / H_orig
        val R_canvas = 2.0f
        
        val S_cover = if (R_img > R_canvas) {
            H_canvas / H_orig
        } else {
            W_canvas / W_orig
        }
        
        val W_scaled = W_orig * S_cover
        val H_scaled = H_orig * S_cover
        
        val X_draw = (W_canvas - W_scaled) / 2f
        val Y_draw = (H_canvas - H_scaled) / 2f
        
        val croppedBitmap = android.graphics.Bitmap.createBitmap(800, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(croppedBitmap)
        
        val matrix = android.graphics.Matrix()
        matrix.postScale(S_cover, S_cover)
        matrix.postTranslate(X_draw, Y_draw)
        
        val panYCanvas = panFraction * H_canvas
        matrix.postTranslate(0f, panYCanvas)
        matrix.postScale(zoom, zoom, W_canvas / 2f, H_canvas / 2f)
        
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(sourceBitmap, matrix, paint)
        
        val outputStream = java.io.ByteArrayOutputStream()
        croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        val croppedBytes = outputStream.toByteArray()
        
        sourceBitmap.recycle()
        croppedBitmap.recycle()
        
        croppedBytes
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

enum class EventGradient(val id: String, val label: String, val brush: Brush) {
    COSMOS_GLOW(
        "gradient:cosmos-glow",
        "Cosmos Glow",
        Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF581C87)))
    ),
    SUNSET_AURORA(
        "gradient:sunset-aurora",
        "Sunset Aurora",
        Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF701A75), Color(0xFFF43F5E)))
    ),
    CYBER_NEON(
        "gradient:cyber-neon",
        "Cyber Neon",
        Brush.linearGradient(listOf(Color(0xFF020617), Color(0xFF0F766E), Color(0xFF06B6D4)))
    ),
    DEEP_SPACE(
        "gradient:deep-space",
        "Deep Space",
        Brush.linearGradient(listOf(Color(0xFF030712), Color(0xFF1E1B4B), Color(0xFFDB2777)))
    ),
    EMERALD_MATRIX(
        "gradient:emerald-matrix",
        "Emerald Matrix",
        Brush.linearGradient(listOf(Color(0xFF022C22), Color(0xFF065F46), Color(0xFF10B981)))
    );

    companion object {
        fun fromId(id: String): EventGradient {
            return values().firstOrNull { it.id == id } ?: COSMOS_GLOW
        }
    }
}

fun parseAndroidEventDate(dateStr: String): java.util.Date? {
    if (dateStr.isBlank()) return null
    val cleanDate = dateStr
        .replace("Today, ", "")
        .replace("Tomorrow, ", "")
        .replace("Next ", "")
        .replace("Monday, ", "")
        .replace("Tuesday, ", "")
        .replace("Wednesday, ", "")
        .replace("Thursday, ", "")
        .replace("Friday, ", "")
        .replace("Saturday, ", "")
        .replace("Sunday, ", "")
        .trim()
    val formats = listOf(
        SimpleDateFormat("MMM d, yyyy", Locale.US),
        SimpleDateFormat("MMMM d, yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )
    for (format in formats) {
        try {
            val parsed = format.parse(cleanDate)
            if (parsed != null) return parsed
        } catch (e: Exception) {
            // ignore
        }
    }
    return null
}

fun truncateToMidnight(date: java.util.Date): java.util.Date {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

fun getAndroidDayHeaderLabel(date: java.util.Date): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val tomorrow = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val eventMidnight = truncateToMidnight(date)

    val dayFormat = SimpleDateFormat("EEEE", Locale.US)
    val monthFormat = SimpleDateFormat("MMMM d", Locale.US)
    val dayOfWeek = dayFormat.format(date)

    return when (eventMidnight.time) {
        today.time -> "Today / $dayOfWeek"
        tomorrow.time -> "Tomorrow / $dayOfWeek"
        else -> "${monthFormat.format(date)} / $dayOfWeek"
    }
}

@Composable
fun LumaEventRow(
    event: NetworkEvent,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGradientCover = event.coverUrl.startsWith("gradient:") || event.coverUrl.isEmpty()
    val coverGradient = if (event.coverUrl.startsWith("gradient:")) EventGradient.fromId(event.coverUrl) else EventGradient.COSMOS_GLOW

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail on the left (80dp x 80dp rounded square)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
        ) {
            if (isGradientCover) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(coverGradient.brush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📅",
                        fontSize = 24.sp
                    )
                }
            } else {
                AsyncImage(
                    model = event.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }

        // Details on the right
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Host Badge Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mini avatar placeholder
                val initial = event.title.firstOrNull()?.toString()?.uppercase() ?: "C"
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(CosmosGradientStart, CosmosGradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Cosmos Host",
                    style = MaterialTheme.typography.labelSmall,
                    color = CosmosOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Event Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = CosmosOnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Time Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = CosmosOnSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = event.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Location Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = CosmosOnSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun YourEventCard(
    event: NetworkEvent,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGradientCover = event.coverUrl.startsWith("gradient:") || event.coverUrl.isEmpty()
    val coverGradient = if (event.coverUrl.startsWith("gradient:")) EventGradient.fromId(event.coverUrl) else EventGradient.COSMOS_GLOW

    CosmosGlassCard(
        modifier = modifier
            .width(220.dp)
            .clickable(onClick = onTap),
        showTopGradientBorder = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Thumbnail / Cover image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (isGradientCover) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(coverGradient.brush),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📅", fontSize = 20.sp)
                    }
                } else {
                    AsyncImage(
                        model = event.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }

            // Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = CosmosOnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Date / Time
            Text(
                text = "${event.date} · ${event.time}",
                style = MaterialTheme.typography.bodySmall,
                color = CosmosPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun YourEventsEmptyCard(
    modifier: Modifier = Modifier
) {
    CosmosGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        showTopGradientBorder = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CosmosSurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = CosmosOnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text info on the right
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "No Upcoming Events",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = CosmosOnBackground
                )
                Text(
                    text = "Events you are hosting or going to will show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExploreEventsEmptyState(
    isFilterActive: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    CosmosGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        showTopGradientBorder = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
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
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        .clickable { onClearFilters() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Clear Filters", color = CosmosBackground, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

