package com.cosmos.app.screens.profile

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.*
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*

@Composable
fun NetworkingDashboardScreen(
    onMembershipTap: () -> Unit,
    onSettingsTap: () -> Unit,
    onNotificationsTap: () -> Unit,
    onNavigate: (String) -> Unit,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    profileViewModel: com.cosmos.app.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    val me = currentUserState ?: SampleData.sampleMember

    val notifications by profileViewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    CosmosAmbientBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Profile hero
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Top actions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("COSMOS", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 12.dp))
                        Row {
                            IconButton(onClick = onNotificationsTap) {
                                if (unreadCount > 0) {
                                    BadgedBox(badge = { Badge(containerColor = CosmosError) { Text("$unreadCount") } }) {
                                        Icon(Icons.Outlined.Notifications, "Notifications", tint = CosmosOnBackground)
                                    }
                                } else {
                                    Icon(Icons.Outlined.Notifications, "Notifications", tint = CosmosOnBackground)
                                }
                            }
                            IconButton(onClick = onSettingsTap) {
                                Icon(Icons.Outlined.Settings, "Settings", tint = CosmosOnBackground)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(32.dp))
                        CosmosAvatar(avatarUrl = me.avatarUrl, name = me.name, size = 88.dp, isLinkedInConnected = me.isLinkedInConnected, membershipTierColor = CosmosPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(me.name, style = MaterialTheme.typography.headlineSmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                        Text(me.headline, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        // Membership badge
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(CosmosGradientStart.copy(alpha = 0.3f), CosmosGradientEnd.copy(alpha = 0.3f)))).border(1.dp, CosmosPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).clickable(onClick = onMembershipTap).padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Star, null, tint = CosmosPrimary, modifier = Modifier.size(14.dp))
                                Text("${me.membershipTier.label} Member", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary)
                            }
                        }
                    }
                }
            }

            // Monthly progress
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    CosmosSectionHeader("Monthly Progress")
                    Spacer(Modifier.height(8.dp))
                    CosmosGlassCard {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text("Connections this month", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                            Text("${me.connectionsCount} of 10", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, fontWeight = FontWeight.Bold)
                        }
                        val progress = (me.connectionsCount.toFloat() / 10f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = CosmosPrimary,
                            trackColor = CosmosSurfaceContainerHigh
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${10 - me.connectionsCount} more curated introductions available this month.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                    }
                }
            }

            // Stats grid
            item {
                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    CosmosSectionHeader("Networking Stats")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CosmosStatCard("Connections", "${me.connectionsCount}", modifier = Modifier.weight(1f))
                        CosmosStatCard("Events", "${me.eventsAttended}", modifier = Modifier.weight(1f), accent = CosmosSecondary)
                        CosmosStatCard("Follow-ups", "${me.followUpsCompleted}", modifier = Modifier.weight(1f), accent = CosmosTertiary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CosmosStatCard("Intros Made", "6", modifier = Modifier.weight(1f), accent = CosmosGradientStart)
                        CosmosStatCard("Goals Hit", "3", modifier = Modifier.weight(1f), accent = CosmosSuccess)
                        CosmosStatCard("Circles", "2", modifier = Modifier.weight(1f), accent = CosmosSecondary)
                    }
                }
            }

            // Top endorsed skills
            item {
                Spacer(Modifier.height(16.dp))
                CosmosSectionHeader("Top Endorsements", modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
                if (me.endorsedSkills.isEmpty()) {
                    Text("No skill endorsements yet.", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    me.endorsedSkills.take(3).forEach { skill ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(skill.name, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { skill.count.toFloat() / 50f },
                                    modifier = Modifier.width(80.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = CosmosPrimary,
                                    trackColor = CosmosSurfaceContainerHigh
                                )
                                Text("${skill.count}", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant, modifier = Modifier.width(28.dp))
                            }
                        }
                    }
                }
            }

            // Quick actions
            item {
                Spacer(Modifier.height(16.dp))
                CosmosSectionHeader("Quick Actions", modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple(Icons.Default.Edit, "Edit Profile", ""),
                    Triple(Icons.Default.Star, "Membership & Tiers", Screen.MembershipTiers.route),
                    Triple(Icons.Default.Settings, "Settings & Privacy", Screen.Settings.route),
                    Triple(Icons.Default.HelpOutline, "Help & Support", "")
                ).forEach { (icon, label, route) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { if (route.isNotEmpty()) onNavigate(route) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(icon, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(22.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    Divider(modifier = Modifier.padding(horizontal = 20.dp), color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun MembershipTiersScreen(
    onBack: () -> Unit,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val tiers = listOf(
        Triple("Explorer", "Free", "Up to 3 connections/month, basic discovery"),
        Triple("Member", "$29/mo", "10 connections/month, events, AI summaries"),
        Triple("Inner Circle", "$99/mo", "Unlimited connections, priority matching, exclusive events"),
        Triple("Founder", "By Invite", "Full platform access, featured profile, dedicated advisor")
    )
    val tierColors = listOf(CosmosOutline, CosmosSecondary, CosmosPrimary, CosmosTertiary)
    val tierIcons = listOf(Icons.Default.Explore, Icons.Default.Star, Icons.Default.WorkspacePremium, Icons.Default.Diamond)

    val currentUserState by authViewModel.currentUser.collectAsState()
    val currentTier = currentUserState?.membershipTier ?: MembershipTier.EXPLORER

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Membership Tiers", onBack = onBack)

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Choose your level", style = MaterialTheme.typography.headlineMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                    Text("Upgrade to unlock more connections and exclusive features.", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                }
                tiers.forEachIndexed { index, (name, price, description) ->
                    item {
                        val isCurrentTier = when (index) {
                            0 -> currentTier == MembershipTier.EXPLORER
                            1 -> currentTier == MembershipTier.MEMBER
                            2 -> currentTier == MembershipTier.INNER_CIRCLE
                            3 -> currentTier == MembershipTier.FOUNDER
                            else -> false
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (isCurrentTier) Brush.linearGradient(listOf(CosmosGradientStart.copy(alpha = 0.2f), CosmosGradientEnd.copy(alpha = 0.2f))) else Brush.linearGradient(listOf(CosmosGlass, CosmosGlass)))
                                .border(width = if (isCurrentTier) 1.5.dp else 1.dp, color = if (isCurrentTier) CosmosPrimary.copy(alpha = 0.6f) else CosmosGlassBorder, shape = RoundedCornerShape(16.dp))
                                .padding(20.dp)
                        ) {
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(tierIcons[index], null, tint = tierColors[index], modifier = Modifier.size(24.dp))
                                        Text(name, style = MaterialTheme.typography.titleLarge, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(price, style = MaterialTheme.typography.titleMedium, color = tierColors[index], fontWeight = FontWeight.Bold)
                                        if (isCurrentTier) {
                                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(CosmosPrimary.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                                Text("Current", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(description, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                                if (!isCurrentTier && index > (if (currentTier == MembershipTier.EXPLORER) 0 else if (currentTier == MembershipTier.MEMBER) 1 else if (currentTier == MembershipTier.INNER_CIRCLE) 2 else 3)) {
                                    Spacer(Modifier.height(12.dp))
                                    CosmosOutlinedButton(text = "Upgrade to $name", onClick = {}, modifier = Modifier.fillMaxWidth())
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

@Composable
fun NotificationsCenterScreen(
    onBack: () -> Unit,
    onIntroRequest: (String) -> Unit,
    onChatTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    profileViewModel: com.cosmos.app.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val notifications by profileViewModel.notifications.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = "Notifications",
                onBack = onBack,
                actions = {
                    TextButton(onClick = {
                        notifications.forEach { notif ->
                            profileViewModel.markNotificationAsRead(notif.id)
                        }
                    }) {
                        Text("Mark all read", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary)
                    }
                }
            )

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                val unread = notifications.filter { !it.isRead }
                val read = notifications.filter { it.isRead }

                if (unread.isNotEmpty()) {
                    item { CosmosSectionHeader("New", modifier = Modifier.padding(horizontal = 16.dp)) }
                    items(unread) { notif ->
                        NotificationItem(notification = notif, onTap = {
                            profileViewModel.markNotificationAsRead(notif.id)
                            when (notif.type) {
                                NotificationType.WARM_INTRO_REQUEST -> onIntroRequest(notif.actionId)
                                NotificationType.MESSAGE -> onChatTap(notif.actionId)
                                NotificationType.NEW_MATCH -> onChatTap(notif.actionId)
                                else -> {}
                            }
                        })
                    }
                }

                if (read.isNotEmpty()) {
                    item { CosmosSectionHeader("Earlier", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    items(read) { notif ->
                        NotificationItem(notification = notif, onTap = {
                            when (notif.type) {
                                NotificationType.WARM_INTRO_REQUEST -> onIntroRequest(notif.actionId)
                                NotificationType.MESSAGE -> onChatTap(notif.actionId)
                                NotificationType.NEW_MATCH -> onChatTap(notif.actionId)
                                else -> {}
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(notification: Notification, onTap: () -> Unit) {
    val icon = when (notification.type) {
        NotificationType.NEW_MATCH -> Icons.Default.Favorite
        NotificationType.MESSAGE -> Icons.Default.Chat
        NotificationType.WARM_INTRO_REQUEST -> Icons.Default.Link
        NotificationType.ENDORSEMENT_RECEIVED -> Icons.Default.Star
        NotificationType.AI_SUMMARY_READY -> Icons.Default.AutoAwesome
        NotificationType.EVENT_INVITATION -> Icons.Default.Event
        else -> Icons.Default.Notifications
    }
    val iconColor = when (notification.type) {
        NotificationType.NEW_MATCH -> Color(0xFFE91E63)
        NotificationType.ENDORSEMENT_RECEIVED -> CosmosTertiary
        NotificationType.AI_SUMMARY_READY -> CosmosPrimary
        else -> CosmosSecondary
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 12.dp).background(if (!notification.isRead) CosmosPrimary.copy(alpha = 0.04f) else Color.Transparent),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(notification.title, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal)
                Text(notification.timestamp, style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
            }
            Text(notification.body, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 2)
        }
        if (!notification.isRead) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(CosmosPrimary).align(Alignment.Top))
        }
    }
    Divider(modifier = Modifier.padding(horizontal = 16.dp), color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
}

@Composable
fun SettingsPrivacyScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit = {},
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val settingsSections = listOf(
        "Account" to listOf("Edit Profile", "Change Password", "Connected Accounts", "LinkedIn Connection"),
        "Notifications" to listOf("New Matches", "Messages", "Event Invitations", "Event Reminders", "AI Summaries", "Follow-up Reminders", "Warm Intro Requests", "Community Announcements", "Endorsements"),
        "Privacy" to listOf("Profile Visibility", "Show LinkedIn Connection", "Allow Warm Intro Requests", "Show Mutual Connections", "Data & Analytics"),
        "Networking" to listOf("Monthly Connection Limit", "Matching Preferences", "Availability Preferences", "Blocked Users"),
        "Danger Zone" to listOf("Sign Out", "Pause Account", "Delete Account")
    )

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Settings & Privacy", onBack = onBack)

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                settingsSections.forEach { (section, settings) ->
                    item { CosmosSectionHeader(section, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    settings.forEach { setting ->
                        item {
                            val isDanger = section == "Danger Zone"
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (setting == "Sign Out") {
                                        authViewModel.signOut {
                                            onSignOut()
                                        }
                                    }
                                }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(setting, style = MaterialTheme.typography.bodyMedium, color = if (isDanger) CosmosError else CosmosOnBackground)
                                if (section == "Notifications") {
                                    Switch(
                                        checked = true, onCheckedChange = {},
                                        colors = SwitchDefaults.colors(checkedTrackColor = CosmosPrimary, uncheckedTrackColor = CosmosSurfaceContainerHigh)
                                    )
                                } else {
                                    Icon(Icons.Default.ChevronRight, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                            Divider(modifier = Modifier.padding(horizontal = 20.dp), color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
