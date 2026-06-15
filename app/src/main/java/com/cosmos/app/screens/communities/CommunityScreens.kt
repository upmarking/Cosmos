package com.cosmos.app.screens.communities

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Feed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosmos.app.data.model.Circle
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.components.CosmosAmbientBackground
import com.cosmos.app.ui.components.CosmosAvatar
import com.cosmos.app.ui.components.CosmosButton
import com.cosmos.app.ui.components.CosmosEmptyState
import com.cosmos.app.ui.components.CosmosErrorState
import com.cosmos.app.ui.components.CosmosGlassCard
import com.cosmos.app.ui.components.CosmosLoadingShimmer
import com.cosmos.app.ui.components.CosmosSectionHeader
import com.cosmos.app.ui.components.CosmosTagChip
import com.cosmos.app.ui.components.CosmosTopBar
import com.cosmos.app.ui.theme.CosmosBackground
import com.cosmos.app.ui.theme.CosmosError
import com.cosmos.app.ui.theme.CosmosGradientEnd
import com.cosmos.app.ui.theme.CosmosGradientStart
import com.cosmos.app.ui.theme.CosmosOnBackground
import com.cosmos.app.ui.theme.CosmosOnSurfaceVariant
import com.cosmos.app.ui.theme.CosmosOutlineVariant
import com.cosmos.app.ui.theme.CosmosPrimary
import com.cosmos.app.ui.theme.CosmosSuccess
import com.cosmos.app.ui.theme.CosmosSurfaceContainerHigh
import com.cosmos.app.ui.theme.CosmosSurfaceContainerLow
import com.cosmos.app.ui.viewmodel.CommunityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityHubScreen(
    onExplore: () -> Unit,
    onCircleTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    communityViewModel: CommunityViewModel = viewModel()
) {
    val circles by communityViewModel.circles.collectAsState()
    val currentUser by communityViewModel.currentUser.collectAsState(initial = null)
    val isLoading by communityViewModel.isLoading.collectAsState()
    val isRefreshing by communityViewModel.isRefreshing.collectAsState()
    val errorMessage by communityViewModel.errorMessage.collectAsState()
    val canCreateCircle = currentUser?.isOrganizer == true
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            communityViewModel.clearError()
        }
    }

    CosmosAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = CosmosError.copy(alpha = 0.9f),
                        contentColor = CosmosOnBackground,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            floatingActionButton = {
                if (canCreateCircle) {
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = CosmosPrimary,
                        contentColor = CosmosBackground,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, "Create Circle")
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).systemBarsPadding()) {
                CosmosTopBar(
                    title = "My Circles",
                    actions = {
                        IconButton(onClick = onExplore) {
                            Icon(Icons.Default.Explore, "Explore", tint = CosmosOnBackground)
                        }
                    }
                )

                when {
                    isLoading && circles.isEmpty() -> {
                        // Show shimmer while initial load
                        CosmosLoadingShimmer(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            lines = 6
                        )
                    }
                    errorMessage != null && circles.isEmpty() -> {
                        // Show error state with retry
                        CosmosErrorState(
                            message = errorMessage ?: "Failed to load circles",
                            onRetry = { communityViewModel.refreshCircles() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    !isLoading && circles.isEmpty() -> {
                        // Show empty state
                        CosmosEmptyState(
                            icon = Icons.Default.Hub,
                            title = "No Circles Yet",
                            subtitle = "Explore and join circles to connect with like-minded professionals.",
                            actionLabel = "Explore Circles",
                            onAction = onExplore,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { communityViewModel.refreshCircles() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Joined circles
                                val joinedCircles = circles.filter { it.isJoined }
                                if (joinedCircles.isNotEmpty()) {
                                    items(joinedCircles) { circle ->
                                        CircleCard(circle = circle, onTap = { onCircleTap(circle.id) })
                                    }
                                }

                                val suggestedCircles = circles.filter { !it.isJoined }
                                if (suggestedCircles.isNotEmpty()) {
                                    item {
                                        Spacer(Modifier.height(8.dp))
                                        CosmosSectionHeader("Suggested for you", actionText = "Browse all", onAction = onExplore)
                                    }

                                    items(suggestedCircles) { circle ->
                                        CircleCard(circle = circle, onTap = {
                                            if (circle.isJoined) {
                                                onCircleTap(circle.id)
                                            } else {
                                                onNavigate(Screen.CircleMembers.createRoute(circle.id))
                                            }
                                        })
                                    }
                                }

                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreateCircleDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, desc, theme, tags, isPrivate ->
                    communityViewModel.createCircle(name, desc, theme, tags, isPrivate) {
                        showCreateDialog = false
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCircleDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, theme: String, tags: List<String>, isPrivate: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("") }
    var tagsString by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Create New Circle",
                style = MaterialTheme.typography.titleLarge,
                color = CosmosOnBackground,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Circle Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary,
                        unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground,
                        unfocusedTextColor = CosmosOnBackground
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary,
                        unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground,
                        unfocusedTextColor = CosmosOnBackground
                    )
                )

                OutlinedTextField(
                    value = theme,
                    onValueChange = { theme = it },
                    label = { Text("Theme / Category (e.g. Design)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary,
                        unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground,
                        unfocusedTextColor = CosmosOnBackground
                    )
                )

                OutlinedTextField(
                    value = tagsString,
                    onValueChange = { tagsString = it },
                    label = { Text("Tags (comma separated, e.g. UI, UX)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary,
                        unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground,
                        unfocusedTextColor = CosmosOnBackground
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        colors = CheckboxDefaults.colors(checkedColor = CosmosPrimary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Make Private", style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                        Text(
                            "Only approved members can view posts and members.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tagsList = tagsString.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onCreate(name, description, theme, tagsList, isPrivate)
                },
                enabled = name.isNotBlank() && description.isNotBlank() && theme.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CosmosPrimary)
            }
        },
        containerColor = CosmosSurfaceContainerHigh,
        shape = RoundedCornerShape(20.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CircleCard(circle: Circle, onTap: () -> Unit) {
    CosmosGlassCard(modifier = Modifier.clickable(onClick = onTap), showTopGradientBorder = false) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Circle icon
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(CosmosPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Hub, "Circle", tint = CosmosPrimary, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(circle.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                    if (circle.isPrivate) {
                        Icon(Icons.Default.Lock, "Private", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
                Text(circle.description, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.People, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text("${circle.memberCount} members", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                    }
                    if (circle.isJoined) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(CosmosSuccess.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("Joined", style = MaterialTheme.typography.labelSmall, color = CosmosSuccess)
                        }
                    }
                }
            }
        }
        if (circle.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                circle.tags.take(3).forEach { CosmosTagChip(text = "#$it") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreCirclesScreen(
    onBack: () -> Unit,
    onCircleTap: (String) -> Unit,
    communityViewModel: CommunityViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val circles by communityViewModel.circles.collectAsState()
    val isLoading by communityViewModel.isLoading.collectAsState()
    val isRefreshing by communityViewModel.isRefreshing.collectAsState()

    // Derive dynamic categories from circle themes
    val dynamicCategories = remember(circles) {
        val themes = circles.mapNotNull { it.theme.takeIf { t -> t.isNotBlank() } }
            .distinct()
            .sortedBy { it }
        listOf("All") + themes
    }

    // Reset selected category if it's no longer available
    LaunchedEffect(dynamicCategories) {
        if (selectedCategory !in dynamicCategories) {
            selectedCategory = "All"
        }
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Explore Circles", onBack = onBack)

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search circles...", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = CosmosOnSurfaceVariant) },
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
                singleLine = true
            )

            // Dynamic category chips from real data
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dynamicCategories) { cat ->
                    val isSelected = cat == selectedCategory
                    CosmosTagChip(
                        text = cat,
                        backgroundColor = if (isSelected) CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                        textColor = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                isLoading && circles.isEmpty() -> {
                    CosmosLoadingShimmer(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        lines = 6
                    )
                }
                else -> {
                    val filtered = circles.filter {
                        val matchesSearch = searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                        val matchesCategory = selectedCategory == "All" || it.theme.contains(selectedCategory, ignoreCase = true) || it.tags.any { t -> t.contains(selectedCategory, ignoreCase = true) }
                        matchesSearch && matchesCategory
                    }

                    if (filtered.isEmpty()) {
                        CosmosEmptyState(
                            icon = Icons.Default.Search,
                            title = "No Circles Found",
                            subtitle = if (searchQuery.isNotEmpty()) "Try a different search term" else "No circles in this category yet",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { communityViewModel.refreshCircles() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filtered) { circle ->
                                    ExploreCircleCard(
                                        circle = circle, 
                                        onTap = { onCircleTap(circle.id) },
                                        onJoin = { communityViewModel.joinCircle(circle.id) }
                                    )
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreCircleCard(circle: Circle, onTap: () -> Unit, onJoin: () -> Unit) {
    CosmosGlassCard(modifier = Modifier.clickable(onClick = onTap), showTopGradientBorder = false) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(circle.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                    if (circle.isPrivate) Icon(Icons.Default.Lock, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(12.dp))
                }
                Text(circle.description, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(6.dp))
                Text("${circle.memberCount} members · Led by ${circle.adminName}", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
            }
            if (!circle.isJoined) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd)))
                        .clickable(onClick = onJoin)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(if (circle.isPrivate) "Request" else "Join", style = MaterialTheme.typography.labelMedium, color = CosmosBackground)
                }
            } else {
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CosmosSuccess.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Joined", style = MaterialTheme.typography.labelMedium, color = CosmosSuccess)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleMembersScreen(
    circleId: String,
    onBack: () -> Unit,
    onMemberTap: (String) -> Unit,
    onFeedTap: () -> Unit,
    communityViewModel: CommunityViewModel = viewModel()
) {
    LaunchedEffect(circleId) {
        communityViewModel.loadCircleMembers(circleId)
        communityViewModel.loadCircles()
    }

    val circles by communityViewModel.circles.collectAsState()
    val circleMembers by communityViewModel.circleMembers.collectAsState()
    val isMembersLoading by communityViewModel.isMembersLoading.collectAsState()
    
    val circle = circles.find { it.id == circleId } ?: Circle(id = circleId, name = "Loading...", description = "", coverUrl = "", memberCount = 0, theme = "", tags = emptyList(), isJoined = false, isPrivate = false, adminName = "")

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = circle.name,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onFeedTap) {
                        Icon(Icons.AutoMirrored.Filled.Feed, "Feed", tint = CosmosOnBackground)
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Text(circle.description, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("${circle.memberCount} members", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary)
                        
                        Spacer(Modifier.height(16.dp))
                        if (!circle.isJoined) {
                            CosmosButton(
                                text = if (circle.isPrivate) {
                                    if (circle.isPending) "Request Pending" else "Request Access"
                                } else {
                                    "Join Circle"
                                },
                                onClick = {
                                    communityViewModel.joinCircle(circle.id)
                                },
                                enabled = !circle.isPending,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedButton(
                                onClick = {
                                    communityViewModel.leaveCircle(circle.id)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmosError),
                                border = BorderStroke(1.dp, CosmosError.copy(alpha = 0.5f))
                            ) {
                                Text("Leave Circle")
                            }
                        }
                    }
                }

                if (circle.isPrivate && !circle.isJoined) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Private Circle",
                                    tint = CosmosOnSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "Private Space",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CosmosOnBackground,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Only approved members of this circle can view members and feed posts.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CosmosOnSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    item { CosmosSectionHeader("Members") }

                    if (isMembersLoading && circleMembers.isEmpty()) {
                        item {
                            CosmosLoadingShimmer(
                                modifier = Modifier.fillMaxWidth(),
                                lines = 4
                            )
                        }
                    } else if (circleMembers.isEmpty()) {
                        item {
                            CosmosEmptyState(
                                icon = Icons.Default.People,
                                title = "No Members Yet",
                                subtitle = "Be the first to join this circle!",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        items(circleMembers) { member ->
                            CosmosGlassCard(modifier = Modifier.clickable { onMemberTap(member.id) }, showTopGradientBorder = false) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, size = 48.dp, isLinkedInConnected = member.isLinkedInConnected)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                        Text(member.headline, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 1)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = CosmosOnSurfaceVariant)
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
fun PrivateCircleFeedScreen(
    circleId: String,
    onBack: () -> Unit,
    onMembersTap: () -> Unit,
    onNavigate: (String) -> Unit,
    communityViewModel: CommunityViewModel = viewModel()
) {
    LaunchedEffect(circleId) {
        communityViewModel.selectCircle(circleId)
    }

    val circleState by communityViewModel.circles.collectAsState()
    val circle = circleState.find { it.id == circleId } ?: Circle(id = circleId, name = "Loading...", description = "", coverUrl = "", memberCount = 0, theme = "", tags = emptyList(), isJoined = false, isPrivate = false, adminName = "")
    var postText by remember { mutableStateOf("") }
    val posts by communityViewModel.feedPosts.collectAsState()
    val likedPostIds by communityViewModel.likedPostIds.collectAsState()
    val isPostsLoading by communityViewModel.isPostsLoading.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = circle.name,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onMembersTap) {
                        Icon(Icons.Default.People, "Members", tint = CosmosOnBackground)
                    }
                }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!circle.isJoined) {
                    if (circle.isPrivate) {
                        item {
                            Spacer(Modifier.height(48.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Private Circle",
                                        tint = CosmosOnSurfaceVariant,
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Text(
                                        text = "Private Feed",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CosmosOnBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Request access to join this community and participate in discussions.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CosmosOnSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    CosmosButton(
                                        text = if (circle.isPending) "Request Pending" else "Request Access",
                                        onClick = {
                                            communityViewModel.joinCircle(circle.id)
                                        },
                                        enabled = !circle.isPending,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Public circle, not joined: show join banner instead of post composer
                        item {
                            CosmosGlassCard(showTopGradientBorder = false) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Join this Circle",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CosmosOnBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "You are viewing this public circle in read-only mode.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CosmosOnSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    CosmosButton(
                                        text = "Join Circle",
                                        onClick = {
                                            communityViewModel.joinCircle(circle.id)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Show posts list (read-only for non-members)
                        if (isPostsLoading && posts.isEmpty()) {
                            item {
                                CosmosLoadingShimmer(modifier = Modifier.fillMaxWidth(), lines = 3)
                            }
                        } else if (posts.isEmpty()) {
                            item {
                                CosmosEmptyState(
                                    icon = Icons.AutoMirrored.Filled.Feed,
                                    title = "No Posts Yet",
                                    subtitle = "Join this circle to be the first to share an update!",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            items(posts) { post ->
                                val isLiked = likedPostIds.contains(post.id)
                                CircleFeedPost(
                                    author = post.author, 
                                    avatarUrl = post.avatarUrl,
                                    content = post.content, 
                                    time = post.timeString, 
                                    likesCount = post.likesCount,
                                    repliesCount = post.repliesCount,
                                    isLiked = isLiked,
                                    onLikeTap = {
                                        if (isLiked) communityViewModel.unlikePost(circleId, post.id)
                                        else communityViewModel.likePost(circleId, post.id)
                                    },
                                    onAuthorTap = {
                                        if (post.authorId.isNotEmpty()) {
                                            onNavigate(Screen.MemberProfile.createRoute(post.authorId))
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Joined circle: show post composer and posts list
                    item {
                        CosmosGlassCard(showTopGradientBorder = false) {
                            OutlinedTextField(
                                value = postText,
                                onValueChange = { postText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Share something with ${circle.name}...", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CosmosPrimary, 
                                    unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.3f),
                                    focusedTextColor = CosmosOnBackground, 
                                    unfocusedTextColor = CosmosOnBackground,
                                    cursorColor = CosmosPrimary, 
                                    focusedContainerColor = CosmosSurfaceContainerLow,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                maxLines = 3
                            )
                            if (postText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    CosmosButton(
                                        text = "Post", 
                                        onClick = { 
                                            communityViewModel.postUpdate(circleId, postText)
                                            postText = "" 
                                        }, 
                                        modifier = Modifier.wrapContentWidth()
                                    )
                                }
                            }
                        }
                    }

                    if (isPostsLoading && posts.isEmpty()) {
                        item {
                            CosmosLoadingShimmer(modifier = Modifier.fillMaxWidth(), lines = 3)
                        }
                    } else if (posts.isEmpty()) {
                        item {
                            CosmosEmptyState(
                                icon = Icons.AutoMirrored.Filled.Feed,
                                title = "No Posts Yet",
                                subtitle = "Be the first to share an update!",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        items(posts) { post ->
                            val isLiked = likedPostIds.contains(post.id)
                            CircleFeedPost(
                                author = post.author, 
                                avatarUrl = post.avatarUrl,
                                content = post.content, 
                                time = post.timeString, 
                                likesCount = post.likesCount,
                                repliesCount = post.repliesCount,
                                isLiked = isLiked,
                                onLikeTap = {
                                    if (isLiked) communityViewModel.unlikePost(circleId, post.id)
                                    else communityViewModel.likePost(circleId, post.id)
                                },
                                onAuthorTap = {
                                    if (post.authorId.isNotEmpty()) {
                                        onNavigate(Screen.MemberProfile.createRoute(post.authorId))
                                    }
                                }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun CircleFeedPost(
    author: String,
    avatarUrl: String,
    content: String,
    time: String,
    likesCount: Int,
    repliesCount: Int,
    isLiked: Boolean = false,
    onLikeTap: () -> Unit = {},
    onAuthorTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val likeIconColor by animateColorAsState(
        targetValue = if (isLiked) CosmosError else CosmosOnSurfaceVariant,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "likeColor"
    )
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.0f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "likeScale"
    )

    CosmosGlassCard(modifier = modifier, showTopGradientBorder = false) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CosmosAvatar(avatarUrl = avatarUrl, name = author, size = 40.dp, modifier = Modifier.clickable(onClick = onAuthorTap))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(author, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAuthorTap))
                    Text(time, style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(content, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onLikeTap)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = likeIconColor,
                            modifier = Modifier.size(16.dp).scale(likeScale)
                        )
                        Text("$likesCount", style = MaterialTheme.typography.labelSmall, color = likeIconColor)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, "Reply", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("$repliesCount", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Share, "Share", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("Share", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                    }
                }
            }
        }
    }
}
