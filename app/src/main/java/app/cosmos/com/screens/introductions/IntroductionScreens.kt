package app.cosmos.com.screens.introductions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cosmos.com.data.model.IntroStatus
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*

@Composable
fun RequestWarmIntroScreen(
    memberId: String,
    onBack: () -> Unit,
    onSent: () -> Unit,
    introViewModel: app.cosmos.com.ui.viewmodel.IntroViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    profileViewModel: app.cosmos.com.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    chatViewModel: app.cosmos.com.ui.viewmodel.ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(memberId) {
        profileViewModel.loadProfile(memberId)
    }

    val memberState by profileViewModel.selectedMember.collectAsState()
    val connectionsState by chatViewModel.connections.collectAsState()
    
    if (memberState == null) {
        CosmosAmbientBackground {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CosmosPrimary)
            }
        }
        return
    }

    val member = memberState!!
    val connectors = connectionsState.map { it.member }.filter { it.id != member.id }
    
    var introMessage by remember { mutableStateOf("") }
    var selectedConnectorId by remember(connectors) { mutableStateOf(connectors.firstOrNull()?.id ?: "") }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Request Warm Intro", onBack = onBack)

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // Target member card
                CosmosGlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, modifier = Modifier, size = 56.dp, isLinkedInConnected = member.isLinkedInConnected)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground)
                            Text(member.headline, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 2)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Who should connect you?", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 12.dp))

                if (connectors.isEmpty()) {
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Text(
                            text = "You don't have any connections yet who can introduce you. Swipe right on other members to connect first!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosOnSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    connectors.forEach { connector ->
                        val isSelected = selectedConnectorId == connector.id
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) CosmosPrimary.copy(alpha = 0.1f) else CosmosSurfaceContainerLow)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CosmosAvatar(avatarUrl = connector.avatarUrl, name = connector.name, modifier = Modifier, size = 40.dp, isLinkedInConnected = connector.isLinkedInConnected)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(connector.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                    Text("Knows both of you", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedConnectorId = connector.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = CosmosPrimary)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Your introduction message", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 8.dp))
                Text("This message will be sent to your connector explaining why you want to meet ${member.name}.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = introMessage,
                    onValueChange = { introMessage = it },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    placeholder = { Text("Hi, I'd love to meet ${member.name} because...", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary, unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground, unfocusedTextColor = CosmosOnBackground,
                        cursorColor = CosmosPrimary, focusedContainerColor = CosmosSurfaceContainerLow,
                        unfocusedContainerColor = CosmosSurfaceContainerLow
                    ),
                    maxLines = 6
                )
                Spacer(Modifier.height(100.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().background(CosmosBackground).navigationBarsPadding().padding(20.dp)) {
                CosmosButton(
                    text = "Send Introduction Request",
                    onClick = {
                        introViewModel.requestWarmIntro(
                            targetId = member.id,
                            connectorId = selectedConnectorId,
                            message = introMessage,
                            onSuccess = onSent
                        )
                    },
                    icon = Icons.Default.Send,
                    enabled = introMessage.isNotBlank() && selectedConnectorId.isNotEmpty()
                )
            }
        }
    }
}

@Composable
fun ReviewIntroRequestScreen(
    requestId: String,
    onBack: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: () -> Unit,
    introViewModel: app.cosmos.com.ui.viewmodel.IntroViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(requestId) {
        introViewModel.loadIntroRequest(requestId)
    }

    val requestState by introViewModel.selectedRequest.collectAsState()
    val request = requestState

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Introduction Request", onBack = onBack)

            if (request != null) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(16.dp))

                    CosmosGlassCard {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                                CosmosAvatar(avatarUrl = request.requester.avatarUrl, name = request.requester.name, modifier = Modifier, size = 64.dp, isLinkedInConnected = request.requester.isLinkedInConnected)
                                CosmosAvatar(avatarUrl = request.target.avatarUrl, name = request.target.name, modifier = Modifier, size = 64.dp, isLinkedInConnected = request.target.isLinkedInConnected)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("${request.requester.name} wants to meet ${request.target.name}", style = MaterialTheme.typography.titleLarge, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                            Text("via your connection", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Requester profile
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Text("The Requester", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.padding(bottom = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CosmosAvatar(avatarUrl = request.requester.avatarUrl, name = request.requester.name, modifier = Modifier, size = 48.dp, isLinkedInConnected = request.requester.isLinkedInConnected)
                            Column {
                                Text(request.requester.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                Text(request.requester.headline, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Their message:", style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        Text(request.message, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Trust info
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, "Verified", tint = CosmosSuccess, modifier = Modifier.size(24.dp))
                            Column {
                                Text("${request.requester.name} is LinkedIn verified", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                Text("${request.requester.mutualConnectionsCount} mutual connections with you", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("If you accept, we'll send a mutual introduction email to both parties.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)

                    Spacer(Modifier.height(100.dp))
                }

                Column(modifier = Modifier.fillMaxWidth().background(CosmosBackground).navigationBarsPadding().padding(20.dp)) {
                    CosmosButton(
                        text = "Accept & Introduce",
                        onClick = {
                            introViewModel.respondToRequest(requestId, IntroStatus.ACCEPTED) {
                                onAccept("intro_${requestId}")
                            }
                        },
                        icon = Icons.Default.Check
                    )
                    Spacer(Modifier.height(8.dp))
                    CosmosOutlinedButton(
                        text = "Decline",
                        onClick = {
                            introViewModel.respondToRequest(requestId, IntroStatus.DECLINED) {
                                onDecline()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CosmosPrimary)
                }
            }
        }
    }
}

@Composable
fun ThreeWayIntroductionScreen(
    introId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    introViewModel: app.cosmos.com.ui.viewmodel.IntroViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val requestId = introId.substringAfter("intro_")
    LaunchedEffect(requestId) {
        introViewModel.loadIntroRequest(requestId)
    }

    val requestState by introViewModel.selectedRequest.collectAsState()
    val request = requestState

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Introduction Made!", onBack = onBack)

            if (request != null) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(32.dp))
                    Text("🤝", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text("Three-Way Introduction", style = MaterialTheme.typography.headlineMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                    Text("All three parties have been notified.", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                    Spacer(Modifier.height(32.dp))

                    listOf(
                        Triple(request.connector, "The Connector", CosmosTertiary),
                        Triple(request.requester, "The Requester", CosmosPrimary),
                        Triple(request.target, "The Target", CosmosSecondary)
                    ).forEach { (m, role, color) ->
                        CosmosGlassCard(modifier = Modifier.padding(bottom = 10.dp), showTopGradientBorder = false) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CosmosAvatar(avatarUrl = m.avatarUrl, name = m.name, modifier = Modifier, size = 48.dp, isLinkedInConnected = m.isLinkedInConnected)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                    Text(role, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                                Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(role.split(" ").first(), style = MaterialTheme.typography.labelSmall, color = color)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, null, tint = CosmosPrimary, modifier = Modifier.size(16.dp))
                            Text("An introduction thread has been created in Conversations for all three parties.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CosmosPrimary)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().background(CosmosBackground).navigationBarsPadding().padding(20.dp)) {
                CosmosButton(text = "Done", onClick = onDone)
            }
        }
    }
}
