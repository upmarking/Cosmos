package app.cosmos.com.screens.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.SendAndArchive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cosmos.com.data.model.ConnectionRequest
import app.cosmos.com.navigation.Screen
import app.cosmos.com.ui.components.CosmosAmbientBackground
import app.cosmos.com.ui.components.CosmosAvatar
import app.cosmos.com.ui.components.CosmosButton
import app.cosmos.com.ui.components.CosmosGlassCard
import app.cosmos.com.ui.components.CosmosOutlinedButton
import app.cosmos.com.ui.components.CosmosTopBar
import app.cosmos.com.ui.theme.CosmosBackground
import app.cosmos.com.ui.theme.CosmosGradientEnd
import app.cosmos.com.ui.theme.CosmosGradientStart
import app.cosmos.com.ui.theme.CosmosOnBackground
import app.cosmos.com.ui.theme.CosmosOnSurfaceVariant
import app.cosmos.com.ui.theme.CosmosOutlineVariant
import app.cosmos.com.ui.theme.CosmosPrimary
import app.cosmos.com.ui.theme.CosmosSurfaceContainerHigh
import app.cosmos.com.ui.viewmodel.ConnectionViewModel

@Composable
fun ConnectionRequestsScreen(
    onBack: () -> Unit,
    onProfileTap: (String) -> Unit,
    onChatTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    connectionViewModel: ConnectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val incoming by connectionViewModel.incomingRequests.collectAsState()
    val outgoing by connectionViewModel.outgoingRequests.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var matchedMember by remember { mutableStateOf<app.cosmos.com.data.model.Member?>(null) }
    val tabs = listOf("Received (${incoming.size})", "Sent (${outgoing.size})")

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Connection Requests", onBack = onBack)

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CosmosBackground,
                contentColor = CosmosPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CosmosPrimary,
                            height = 2.dp
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) CosmosPrimary else CosmosOnSurfaceVariant,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> IncomingRequestsList(
                    requests = incoming,
                    onProfileTap = onProfileTap,
                    onAccept = { requestId ->
                        connectionViewModel.acceptRequest(requestId) { sender ->
                            matchedMember = sender
                        }
                    },
                    onDecline = { requestId ->
                        connectionViewModel.declineRequest(requestId)
                    },
                    onNavigate = onNavigate
                )
                1 -> OutgoingRequestsList(
                    requests = outgoing,
                    onProfileTap = onProfileTap,
                    onWithdraw = { requestId ->
                        connectionViewModel.withdrawRequest(requestId)
                    },
                    onNavigate = onNavigate
                )
            }

            matchedMember?.let { member ->
                ConnectionEstablishedOverlay(
                    matchedMember = member,
                    onSendMessage = { connectionId ->
                        matchedMember = null
                        onChatTap(connectionId)
                    },
                    onDismiss = { matchedMember = null }
                )
            }
        }
    }
}

@Composable
private fun IncomingRequestsList(
    requests: List<ConnectionRequest>,
    onProfileTap: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyRequestsState(
            icon = Icons.Outlined.PersonAdd,
            title = "No Pending Requests",
            subtitle = "When someone sends you a connection request, it will appear here.",
            ctaText = "Discover People",
            onCta = { onNavigate(Screen.Connect.route) }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(requests, key = { it.id }) { request ->
                IncomingRequestCard(
                    request = request,
                    onProfileTap = { onProfileTap(request.senderId) },
                    onAccept = { onAccept(request.id) },
                    onDecline = { onDecline(request.id) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun OutgoingRequestsList(
    requests: List<ConnectionRequest>,
    onProfileTap: (String) -> Unit,
    onWithdraw: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyRequestsState(
            icon = Icons.AutoMirrored.Outlined.SendAndArchive,
            title = "No Sent Requests",
            subtitle = "Your pending connection requests will appear here.",
            ctaText = "Find Connections",
            onCta = { onNavigate(Screen.Connect.route) }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(requests, key = { it.id }) { request ->
                OutgoingRequestCard(
                    request = request,
                    onProfileTap = { onProfileTap(request.receiverId) },
                    onWithdraw = { onWithdraw(request.id) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun IncomingRequestCard(
    request: ConnectionRequest,
    onProfileTap: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    CosmosGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onProfileTap),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CosmosAvatar(
                    avatarUrl = request.senderAvatarUrl,
                    name = request.senderName,
                    size = 52.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        request.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        request.senderHeadline,
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Personal message
            if (request.message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CosmosSurfaceContainerHigh.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.FormatQuote, null, tint = CosmosPrimary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        Text(
                            request.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnBackground,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Action buttons
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Accept button — gradient
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd)))
                        .clickable(onClick = onAccept),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = CosmosBackground, modifier = Modifier.size(18.dp))
                        Text("Accept", style = MaterialTheme.typography.labelLarge, color = CosmosBackground, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Decline button — outlined
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CosmosSurfaceContainerHigh.copy(alpha = 0.3f))
                        .clickable(onClick = onDecline),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text("Decline", style = MaterialTheme.typography.labelLarge, color = CosmosOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutgoingRequestCard(
    request: ConnectionRequest,
    onProfileTap: () -> Unit,
    onWithdraw: () -> Unit
) {
    CosmosGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onProfileTap),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CosmosAvatar(
                    avatarUrl = request.receiverAvatarUrl,
                    name = request.receiverName,
                    size = 52.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        request.receiverName,
                        style = MaterialTheme.typography.titleSmall,
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        request.receiverHeadline,
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmosPrimary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Pending", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary)
                }
            }

            if (request.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "\"${request.message}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))
            CosmosOutlinedButton(
                text = "Withdraw Request",
                onClick = onWithdraw,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.AutoMirrored.Filled.Undo
            )
        }
    }
}

@Composable
fun ConnectionEstablishedOverlay(
    matchedMember: app.cosmos.com.data.model.Member,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(app.cosmos.com.ui.theme.CosmosBackground.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CosmosPrimary.copy(alpha = 0.15f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF6F),
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Connection Established! 🎉",
                style = MaterialTheme.typography.headlineMedium,
                color = CosmosPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            CosmosAvatar(
                avatarUrl = matchedMember.avatarUrl,
                name = matchedMember.name,
                size = 80.dp,
                isLinkedInConnected = matchedMember.isLinkedInConnected
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "You are now connected with ${matchedMember.name}. Start a conversation now!",
                style = MaterialTheme.typography.bodyMedium,
                color = CosmosOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(32.dp))

            CosmosButton(
                text = "Say Hello",
                onClick = {
                    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val otherUid = matchedMember.id
                    val connectionId = if (currentUid < otherUid) "${currentUid}_${otherUid}" else "${otherUid}_${currentUid}"
                    onSendMessage(connectionId)
                },
                icon = Icons.Default.Chat,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = CosmosOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyRequestsState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = CosmosPrimary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = CosmosOnBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = CosmosOnSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            CosmosButton(
                text = ctaText,
                onClick = onCta,
                modifier = Modifier.width(200.dp)
            )
        }
    }
}
