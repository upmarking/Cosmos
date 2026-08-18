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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.NetworkEvent
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.AuthViewModel
import app.cosmos.com.ui.viewmodel.EventViewModel
import app.cosmos.com.ui.viewmodel.PricingFilter
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListScreen(
    onEventTap: (String) -> Unit,
    onPostEventTap: () -> Unit,
    onNavigate: (String) -> Unit,
    eventViewModel: EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val events by eventViewModel.events.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val selectedTypes by eventViewModel.selectedEventTypes.collectAsState()
    val pricingFilter by eventViewModel.pricingFilter.collectAsState()
    val showRegisteredOnly by eventViewModel.showRegisteredOnly.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    val isFilterActive = selectedTypes.isNotEmpty() ||
            pricingFilter != PricingFilter.ALL ||
            showRegisteredOnly

    val displayEvents = remember(events, selectedTypes, pricingFilter, showRegisteredOnly) {
        eventViewModel.filteredEvents
    }

    CosmosAmbientBackground {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CosmosGlassTopBar(
                    pageTitle = "Organize",
                    extraActions = {
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

@Composable
private fun buildFilterSummary(
    types: Set<app.cosmos.com.data.model.EventType>,
    pricing: PricingFilter,
    registeredOnly: Boolean
): String {
    val parts = mutableListOf<String>()
    if (types.isNotEmpty()) {
        parts.add(types.joinToString(", ") { it.label })
    }
    if (pricing != PricingFilter.ALL) {
        parts.add(pricing.label)
    }
    if (registeredOnly) {
        parts.add("Registered")
    }
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventFilterSheetContent(
    selectedTypes: Set<app.cosmos.com.data.model.EventType>,
    pricingFilter: PricingFilter,
    showRegisteredOnly: Boolean,
    onToggleType: (app.cosmos.com.data.model.EventType) -> Unit,
    onSetPricing: (PricingFilter) -> Unit,
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
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CosmosOnSurfaceVariant.copy(alpha = 0.3f))
        )

        Spacer(Modifier.height(16.dp))

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

        Text(
            "Event Type",
            style = MaterialTheme.typography.labelLarge,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

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
            PricingFilter.values().forEach { filter ->
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

        CosmosButton(
            text = "Apply Filters",
            onClick = onApply,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
    }
}

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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = CosmosOnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

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
