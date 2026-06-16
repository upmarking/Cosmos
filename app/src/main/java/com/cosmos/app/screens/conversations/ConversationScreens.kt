package com.cosmos.app.screens.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.*
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import androidx.compose.runtime.collectAsState
import com.cosmos.app.navigation.Screen

@Composable
fun ConversationsListScreen(
    onChatTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    chatViewModel: com.cosmos.app.ui.viewmodel.ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val connections by chatViewModel.connections.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosGlassTopBar(
                pageTitle = "Chats",
                extraActions = {
                    GlassIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "New Chat",
                        onClick = {}
                    )
                }
            )

            // Search
            var searchQuery by remember { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search conversations...", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
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

            // Filter labels
            var selectedFilter by remember { mutableStateOf("All") }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Follow Up", "Unread").forEach { filter ->
                    val isSelected = filter == selectedFilter
                    CosmosTagChip(
                        text = filter,
                        backgroundColor = if (isSelected) CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                        textColor = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                val filtered = connections.filter {
                    val matchesSearch = searchQuery.isEmpty() || it.member.name.contains(searchQuery, ignoreCase = true)
                    val matchesFilter = when (selectedFilter) {
                        "Follow Up" -> it.status == ConnectionStatus.FOLLOW_UP_NEEDED || it.labels.contains("Follow Up")
                        "Unread" -> it.unreadCount > 0
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight(0.7f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Outlined.ChatBubbleOutline,
                                    null,
                                    tint = CosmosPrimary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No Conversations Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CosmosOnBackground,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Curate matches in Connect or register for speed matchmaking events to start networking.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CosmosOnSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                CosmosButton(
                                    text = "Find Matches",
                                    onClick = { onNavigate(Screen.Connect.route) },
                                    modifier = Modifier.width(180.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(filtered) { connection ->
                        ConversationListItem(connection = connection, onTap = { onChatTap(connection.id) })
                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationListItem(connection: Connection, onTap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CosmosAvatar(
            avatarUrl = connection.member.avatarUrl,
            name = connection.member.name,
            modifier = Modifier,
            size = 52.dp,
            isLinkedInConnected = connection.member.isLinkedInConnected
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(connection.member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                Text(connection.lastMessageTime, style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
            }
            Text(connection.lastMessage, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                connection.labels.forEach { label ->
                    CosmosTagChip(text = label, backgroundColor = CosmosSurfaceContainerHigh)
                }
            }
        }
        if (connection.unreadCount > 0) {
            Box(
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(CosmosPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text("${connection.unreadCount}", style = MaterialTheme.typography.labelSmall, color = CosmosBackground)
            }
        }
    }
}

@Composable
fun RelationshipCrmChatScreen(
    connectionId: String,
    onBack: () -> Unit,
    onProfileTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    chatViewModel: com.cosmos.app.ui.viewmodel.ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(connectionId) {
        chatViewModel.selectConnection(connectionId)
    }

    val activeConnectionState by chatViewModel.activeConnection.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    
    if (activeConnectionState == null) {
        CosmosAmbientBackground {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CosmosPrimary)
            }
        }
        return
    }

    val connection = activeConnectionState!!
    val member = connection.member

    var messageText by remember { mutableStateOf("") }
    var showSummary by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var newGoalText by remember(connection.privateGoal) { mutableStateOf(connection.privateGoal) }
    
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // Chat header
            Row(
                modifier = Modifier.fillMaxWidth().background(CosmosBackground).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = CosmosOnBackground)
                }
                Box(modifier = Modifier.clickable { onProfileTap(member.id) }) {
                    CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, modifier = Modifier, size = 44.dp, isLinkedInConnected = member.isLinkedInConnected)
                }
                Column(modifier = Modifier.weight(1f).clickable { onProfileTap(member.id) }) {
                    Text(member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                    Text(member.headline, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { showSummary = !showSummary }) {
                    Icon(Icons.Default.AutoAwesome, "AI Summary", tint = CosmosPrimary)
                }
                IconButton(onClick = { showGoalDialog = true }) {
                    Icon(Icons.Default.TrackChanges, "Private Goal", tint = CosmosTertiary)
                }
            }

            // Labels bar
            Row(
                modifier = Modifier.fillMaxWidth().background(CosmosSurfaceContainerLow).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Label, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(16.dp))
                if (connection.labels.isEmpty()) {
                    Text("No labels set", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                } else {
                    connection.labels.forEach { label ->
                        CosmosTagChip(text = label, backgroundColor = CosmosPrimary.copy(alpha = 0.15f), textColor = CosmosPrimary)
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.Add, 
                    "Add label", 
                    tint = CosmosOnSurfaceVariant, 
                    modifier = Modifier.size(16.dp).clickable {
                        chatViewModel.addLabel(connectionId, connection.labels + "Potential Partner")
                    }
                )
            }

            // AI Summary panel (collapsible)
            if (showSummary) {
                CosmosGlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = CosmosPrimary, modifier = Modifier.size(16.dp))
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("AI Relationship Summary", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.padding(bottom = 4.dp))
                                Text("Regenerate", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary, modifier = Modifier.clickable {
                                    val transcript = messages.filter { it.type == MessageType.TEXT }.joinToString("\n") { "${it.senderId}: ${it.text}" }
                                    chatViewModel.generateMeetingAiSummary(connectionId, transcript)
                                })
                            }
                            Text("Private goal: ${connection.privateGoal.ifBlank { "Not set yet" }}", style = MaterialTheme.typography.bodySmall, color = CosmosOnBackground)
                            Text("Next steps: Follow up on partnership details.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                        }
                    }
                }
            }

            // Messages
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CosmosAvatar(
                            avatarUrl = member.avatarUrl,
                            name = member.name,
                            size = 64.dp,
                            isLinkedInConnected = member.isLinkedInConnected
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Match established! 🎉",
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Say hello to ${member.name} and start building together.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        when (msg.type) {
                            MessageType.AI_SUMMARY -> AiSummaryBubble(text = msg.text)
                            else -> ChatBubble(message = msg)
                        }
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier.fillMaxWidth().background(CosmosSurfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp).imePadding().navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary, unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground, unfocusedTextColor = CosmosOnBackground,
                        cursorColor = CosmosPrimary, focusedContainerColor = CosmosSurfaceContainer,
                        unfocusedContainerColor = CosmosSurfaceContainer
                    ),
                    maxLines = 4
                )
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (messageText.isNotBlank()) Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd)) else Brush.linearGradient(listOf(CosmosSurfaceContainerHigh, CosmosSurfaceContainerHigh)))
                        .clickable(enabled = messageText.isNotBlank()) { 
                            chatViewModel.sendMessage(connectionId, messageText)
                            messageText = "" 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Send, "Send", tint = if (messageText.isNotBlank()) CosmosBackground else CosmosOnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    // Private Goal Edit Dialog
    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Set Private Goal", color = CosmosOnBackground) },
            text = {
                Column {
                    Text("This goal is only visible to you. The other member cannot see it.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newGoalText,
                        onValueChange = { newGoalText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Ask for intro to Marcus Williams", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    chatViewModel.updateGoal(connectionId, newGoalText)
                    showGoalDialog = false
                }) {
                    Text("Save", color = CosmosPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            containerColor = CosmosSurfaceContainerLow
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.isOwn) 16.dp else 4.dp,
                        bottomEnd = if (message.isOwn) 4.dp else 16.dp
                    )
                )
                .background(if (message.isOwn) Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd)) else Brush.linearGradient(listOf(CosmosSurfaceContainerHigh, CosmosSurfaceContainerHigh)))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = if (message.isOwn) androidx.compose.ui.graphics.Color.White else CosmosOnBackground)
                Spacer(Modifier.height(2.dp))
                Text(message.timestamp, style = MaterialTheme.typography.labelSmall, color = (if (message.isOwn) androidx.compose.ui.graphics.Color.White else CosmosOnSurfaceVariant).copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun AiSummaryBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CosmosPrimary.copy(alpha = 0.1f)).border(1.dp, CosmosPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.AutoAwesome, null, tint = CosmosPrimary, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = CosmosOnBackground)
        }
    }
}
