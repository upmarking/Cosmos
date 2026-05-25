package com.cosmos.app.screens.connect

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.EndorsedSkill
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.SampleData
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundersCircleFeedScreen(
    onProfileTap: (String) -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Founders Circle", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(SampleData.sampleMembers) { member ->
                    FounderFeedCard(member = member, onTap = { onProfileTap(member.id) })
                }
            }
        }
    }
}

@Composable
fun FounderFeedCard(member: Member, onTap: () -> Unit) {
    CosmosGlassCard(
        modifier = Modifier.clickable(onClick = onTap)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CosmosAvatar(
                avatarUrl = member.avatarUrl,
                name = member.name,
                size = 56.dp,
                isLinkedInConnected = member.isLinkedInConnected
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                Text(member.headline, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    member.tags.take(3).forEach { CosmosTagChip(text = "#$it") }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${member.mutualConnectionsCount}", style = MaterialTheme.typography.titleMedium, color = CosmosPrimary, fontWeight = FontWeight.Bold)
                Text("mutual", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
            }
        }
        if (member.goalStatement.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(member.goalStatement, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileScreen(
    memberId: String,
    onBack: () -> Unit,
    onEndorse: () -> Unit,
    onWarmIntro: () -> Unit,
    onStartChat: () -> Unit,
    onNavigate: (String) -> Unit,
    profileViewModel: com.cosmos.app.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(memberId) {
        profileViewModel.loadProfile(memberId)
    }

    val memberState by profileViewModel.selectedMember.collectAsState()
    val member = memberState ?: SampleData.sampleMembers.find { it.id == memberId } ?: SampleData.sampleMember
    var isConnected by remember { mutableStateOf(false) }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Share, "Share", tint = CosmosOnBackground)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, "More", tint = CosmosOnBackground)
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Hero section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CosmosAvatar(
                            avatarUrl = member.avatarUrl,
                            name = member.name,
                            size = 96.dp,
                            isLinkedInConnected = member.isLinkedInConnected,
                            membershipTierColor = CosmosPrimary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(member.name, style = MaterialTheme.typography.headlineMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                        Text(member.headline, style = MaterialTheme.typography.bodyLarge, color = CosmosOnSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.LocationOn, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text(member.location, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.People, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("${member.mutualConnectionsCount} mutual", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Membership badge
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CosmosPrimary.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("✦ ${member.membershipTier.label}", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary)
                        }

                        // Action row
                        Spacer(Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!isConnected) {
                                CosmosButton(
                                    text = "Connect",
                                    onClick = { isConnected = true },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.PersonAdd
                                )
                            } else {
                                CosmosOutlinedButton(
                                    text = "Message",
                                    onClick = onStartChat,
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Chat
                                )
                            }
                            CosmosOutlinedButton(
                                text = "Intro",
                                onClick = onWarmIntro,
                                modifier = Modifier.wrapContentWidth(),
                                icon = Icons.Default.Link
                            )
                        }
                    }
                }

                // About section
                item {
                    Spacer(Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CosmosSectionHeader("About")
                        Spacer(Modifier.height(8.dp))
                        CosmosGlassCard(showTopGradientBorder = false) {
                            Text(member.bio, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        }
                    }
                }

                // Goal statement
                item {
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CosmosSectionHeader("Looking for")
                        Spacer(Modifier.height(8.dp))
                        CosmosGlassCard(showTopGradientBorder = false) {
                            Text(member.goalStatement, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                        }
                    }
                }

                // Tags
                item {
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CosmosSectionHeader("Interests")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        ) {
                            member.tags.forEach { CosmosTagChip(text = "#$it") }
                        }
                    }
                }

                // Endorsed skills
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CosmosSectionHeader("Endorsed Skills", actionText = "Endorse", onAction = onEndorse)
                        Spacer(Modifier.height(8.dp))
                        member.endorsedSkills.forEach { skill ->
                            EndorsedSkillRow(skill = skill)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // Activity stats
                item {
                    Spacer(Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CosmosSectionHeader("Activity")
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CosmosStatCard("Connections", "${member.connectionsCount}", modifier = Modifier.weight(1f))
                            CosmosStatCard("Events", "${member.eventsAttended}", modifier = Modifier.weight(1f), accent = CosmosSecondary)
                            CosmosStatCard("Follow-ups", "${member.followUpsCompleted}", modifier = Modifier.weight(1f), accent = CosmosTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EndorsedSkillRow(skill: EndorsedSkill) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(skill.name, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val starCount = if (skill.count > 0) (skill.count / 5).coerceIn(1, 5) else 1
            repeat(starCount) {
                Icon(Icons.Default.Star, null, tint = CosmosPrimary, modifier = Modifier.size(14.dp))
            }
            Text("${skill.count}", style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun EndorseExpertiseScreen(
    memberId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    profileViewModel: com.cosmos.app.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(memberId) {
        profileViewModel.loadProfile(memberId)
    }

    val memberState by profileViewModel.selectedMember.collectAsState()
    val member = memberState ?: SampleData.sampleMembers.find { it.id == memberId } ?: SampleData.sampleMember
    val allSkills = listOf("Communication", "Product Thinking", "Fundraising", "Design", "Sales", "Leadership", "Hiring", "Strategy", "Operations", "Technical Ability", "Marketing", "Finance", "Legal", "Public Speaking", "Negotiation")
    val endorsed = remember { mutableStateSetOf<String>() }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Endorse Expertise", onBack = onBack)

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, size = 56.dp, isLinkedInConnected = member.isLinkedInConnected)
                    Column {
                        Text(member.name, style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground)
                        Text("Tap a skill to endorse it", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Skills to Endorse", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allSkills.forEach { skill ->
                        val isEndorsed = endorsed.contains(skill)
                        CosmosTagChip(
                            text = if (isEndorsed) "✓ $skill" else skill,
                            backgroundColor = if (isEndorsed) CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                            textColor = if (isEndorsed) CosmosPrimary else CosmosOnSurfaceVariant,
                            onClick = {
                                if (isEndorsed) endorsed.remove(skill) else endorsed.add(skill)
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().background(CosmosBackground).navigationBarsPadding().padding(20.dp)) {
                CosmosButton(
                    text = if (endorsed.isEmpty()) "Select skills to endorse" else "Endorse ${endorsed.size} Skill${if (endorsed.size != 1) "s" else ""}",
                    onClick = {
                        endorsed.forEach { skill ->
                            profileViewModel.endorseSkill(memberId, skill)
                        }
                        onDone()
                    },
                    enabled = endorsed.isNotEmpty()
                )
            }
        }
    }
}
