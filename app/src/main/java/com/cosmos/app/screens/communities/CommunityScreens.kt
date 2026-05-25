package com.cosmos.app.screens.communities

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.Circle
import com.cosmos.app.data.model.SampleData
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import androidx.compose.runtime.collectAsState

@Composable
fun CommunityHubScreen(
    onExplore: () -> Unit,
    onCircleTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    communityViewModel: com.cosmos.app.ui.viewmodel.CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val circles by communityViewModel.circles.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = "My Circles",
                actions = {
                    IconButton(onClick = onExplore) {
                        Icon(Icons.Default.Explore, "Explore", tint = CosmosOnBackground)
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Joined circles
                val joinedCircles = circles.filter { it.isJoined }
                items(joinedCircles) { circle ->
                    CircleCard(circle = circle, onTap = { onCircleTap(circle.id) })
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    CosmosSectionHeader("Suggested for you", actionText = "Browse all", onAction = onExplore)
                }

                val suggestedCircles = circles.filter { !it.isJoined }
                items(suggestedCircles) { circle ->
                    CircleCard(circle = circle, onTap = { onCircleTap(circle.id) })
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

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

@Composable
fun ExploreCirclesScreen(
    onBack: () -> Unit,
    onCircleTap: (String) -> Unit,
    communityViewModel: com.cosmos.app.ui.viewmodel.CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val circles by communityViewModel.circles.collectAsState()

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
                    focusedBorderColor = CosmosPrimary, unfocusedBorderColor = CosmosOutlineVariant,
                    focusedTextColor = CosmosOnBackground, unfocusedTextColor = CosmosOnBackground,
                    cursorColor = CosmosPrimary, focusedContainerColor = CosmosSurfaceContainerLow,
                    unfocusedContainerColor = CosmosSurfaceContainerLow
                ),
                singleLine = true
            )

            // Category chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Tech", "Finance", "Founders", "Investors").forEach { cat ->
                    CosmosTagChip(
                        text = cat,
                        backgroundColor = if (cat == "All") CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                        textColor = if (cat == "All") CosmosPrimary else CosmosOnSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val filtered = circles.filter {
                    searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
                }
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

@Composable
fun CircleMembersScreen(
    circleId: String,
    onBack: () -> Unit,
    onMemberTap: (String) -> Unit,
    onFeedTap: () -> Unit
) {
    val circle = SampleData.sampleCircles.find { it.id == circleId } ?: SampleData.sampleCircles.first()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = circle.name,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onFeedTap) {
                        Icon(Icons.Default.Feed, "Feed", tint = CosmosOnBackground)
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
                    }
                }

                item { CosmosSectionHeader("Members") }

                items(SampleData.sampleMembers) { member ->
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

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun PrivateCircleFeedScreen(
    circleId: String,
    onBack: () -> Unit,
    onMembersTap: () -> Unit,
    onNavigate: (String) -> Unit,
    communityViewModel: com.cosmos.app.ui.viewmodel.CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(circleId) {
        communityViewModel.selectCircle(circleId)
    }

    val circleState by communityViewModel.circles.collectAsState()
    val circle = circleState.find { it.id == circleId } ?: SampleData.sampleCircles.first()
    var postText by remember { mutableStateOf("") }
    val posts by communityViewModel.feedPosts.collectAsState()

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
                // Post composer
                item {
                    CosmosGlassCard(showTopGradientBorder = false) {
                        OutlinedTextField(
                            value = postText,
                            onValueChange = { postText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Share something with ${circle.name}...", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary, unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.3f),
                                focusedTextColor = CosmosOnBackground, unfocusedTextColor = CosmosOnBackground,
                                cursorColor = CosmosPrimary, focusedContainerColor = CosmosSurfaceContainerLow,
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

                if (posts.isEmpty()) {
                    item {
                        CosmosGlassCard(showTopGradientBorder = false) {
                            Text("No posts yet. Be the first to share an update!", color = CosmosOnSurfaceVariant, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    items(posts) { post ->
                        CircleFeedPost(
                            author = post.author, 
                            content = post.content, 
                            time = post.timeString, 
                            onAuthorTap = {
                                SampleData.sampleMembers.find { it.name == post.author }?.let { onNavigate(com.cosmos.app.navigation.Screen.MemberProfile.createRoute(it.id)) }
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun CircleFeedPost(author: String, content: String, time: String, onAuthorTap: () -> Unit) {
    CosmosGlassCard(showTopGradientBorder = false) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CosmosAvatar(avatarUrl = "", name = author, size = 40.dp, modifier = Modifier.clickable(onClick = onAuthorTap))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(author, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAuthorTap))
                    Text(time, style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(content, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.FavoriteBorder, "Like", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("12", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, "Reply", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("3", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
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
