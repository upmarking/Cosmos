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

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalLayoutApi::class)
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
                        CosmosAvatar(
                            avatarUrl = me.avatarUrl,
                            name = me.name,
                            modifier = Modifier,
                            size = 88.dp,
                            isLinkedInConnected = me.isLinkedInConnected,
                            membershipTierColor = CosmosPrimary
                        )
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
                        CosmosStatCard(label = "Intros Made", value = "6", modifier = Modifier.weight(1f), accent = CosmosGradientStart)
                        CosmosStatCard(label = "Goals Hit", value = "3", modifier = Modifier.weight(1f), accent = CosmosSuccess)
                        CosmosStatCard(label = "Circles", value = "2", modifier = Modifier.weight(1f), accent = CosmosSecondary)
                    }
                }
            }

            // Top endorsed skills
            item {
                Spacer(Modifier.height(16.dp))
                CosmosSectionHeader(title = "Top Endorsements")
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
                CosmosSectionHeader(title = "Quick Actions")
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple(Icons.Default.Edit, "Edit Profile", Screen.EditProfile.route),
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
                    item { CosmosSectionHeader(title = "New") }
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
                    item { CosmosSectionHeader(title = "Earlier") }
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
    onEditProfileTap: () -> Unit = {},
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val settingsSections = listOf(
        "Account" to listOf("Edit Profile", "Change Password", "Connected Accounts", "LinkedIn Connection"),
        "Notifications" to listOf("New Matches", "Messages", "Event Invitations", "Event Reminders", "AI Summaries", "Follow-up Reminders", "Warm Intro Requests", "Community Announcements", "Endorsements"),
        "Privacy" to listOf("Profile Visibility", "Show LinkedIn Connection", "Allow Warm Intro Requests", "Show Mutual Connections", "Data & Analytics"),
        "Networking" to listOf("Monthly Connection Limit", "Matching Preferences", "Availability Preferences", "Blocked Users"),
        "Danger Zone" to listOf("Sign Out", "Pause Account", "Delete Account")
    )

    val currentUser by authViewModel.currentUser.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Settings & Privacy", onBack = onBack)

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                settingsSections.forEach { (section, settings) ->
                    item { CosmosSectionHeader(title = section) }
                    settings.forEach { setting ->
                        item {
                            val isDanger = section == "Danger Zone"
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (setting == "Sign Out") {
                                        authViewModel.signOut {
                                            onSignOut()
                                        }
                                    } else if (setting == "Edit Profile") {
                                        onEditProfileTap()
                                    } else if (setting == "LinkedIn Connection") {
                                        currentUser?.let { member ->
                                            val updated = member.copy(isLinkedInConnected = !member.isLinkedInConnected)
                                            authViewModel.saveOnboarding(member = updated, imageBytes = null, onSuccess = {})
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
                                } else if (setting == "LinkedIn Connection") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val isConnected = currentUser?.isLinkedInConnected == true
                                        Text(
                                            text = if (isConnected) "Connected" else "Not Connected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isConnected) CosmosPrimary else CosmosOnSurfaceVariant
                                        )
                                        Icon(Icons.Default.ChevronRight, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var headline by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedUserType by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var currentAvatarUrl by remember { mutableStateOf("") }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            selectedImageBitmap = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedImageBitmap = bitmap
            selectedImageUri = null
        }
    }

    val userTypes = listOf(
        "Founder", "Co-Founder", "Startup Operator", "Investor", "Student",
        "Mentor", "Tech Professional", "Marketing Professional", "Finance Professional",
        "Legal Professional", "Healthcare Professional", "Business Professional",
        "Creator", "Freelancer", "Service Provider", "Community Member"
    )

    // Prefill form values once when currentUserState becomes available
    var hasPrefilled by remember { mutableStateOf(false) }
    LaunchedEffect(currentUserState) {
        currentUserState?.let { member ->
            if (!hasPrefilled) {
                name = member.name
                headline = member.headline
                role = member.role
                company = member.company
                location = member.location
                selectedUserType = member.primaryUserType
                bio = member.bio
                currentAvatarUrl = member.avatarUrl
                hasPrefilled = true
            }
        }
    }

    var localError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authViewModel.authError.collect { error ->
            localError = error
        }
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Edit Profile", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // Profile photo edit
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(CosmosSurfaceContainerHigh)
                            .border(2.dp, CosmosOutlineVariant, CircleShape)
                            .clickable { showPhotoOptions = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null || selectedImageBitmap != null) {
                            AsyncImage(
                                model = selectedImageUri ?: selectedImageBitmap,
                                contentDescription = "Selected photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (currentAvatarUrl.isNotEmpty()) {
                            val model: Any = if (currentAvatarUrl.startsWith("data:image")) {
                                try {
                                    val base64Data = currentAvatarUrl.substringAfter(",")
                                    android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                } catch (e: Exception) {
                                    currentAvatarUrl
                                }
                            } else {
                                currentAvatarUrl
                            }
                            AsyncImage(
                                model = model,
                                contentDescription = "Current photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = "Add photo",
                                    tint = CosmosOnSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    "Photo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CosmosOnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (showPhotoOptions) {
                    AlertDialog(
                        onDismissRequest = { showPhotoOptions = false },
                        title = { Text("Select Profile Photo", color = CosmosOnBackground) },
                        text = { Text("Choose a photo from your gallery or take a new one.", color = CosmosOnSurfaceVariant) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPhotoOptions = false
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Text("Gallery", color = CosmosPrimary)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPhotoOptions = false
                                    cameraLauncher.launch(null)
                                }
                            ) {
                                Text("Camera", color = CosmosPrimary)
                            }
                        },
                        containerColor = CosmosSurfaceContainerHigh
                    )
                }

                Spacer(Modifier.height(24.dp))

                // User type selector
                Text(
                    "I am a...",
                    style = MaterialTheme.typography.titleSmall,
                    color = CosmosOnBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    userTypes.forEach { type ->
                        CosmosTagChip(
                            text = type,
                            backgroundColor = if (selectedUserType == type) CosmosPrimary.copy(alpha = 0.2f)
                                             else CosmosSurfaceContainerHigh,
                            textColor = if (selectedUserType == type) CosmosPrimary else CosmosOnSurfaceVariant,
                            onClick = { selectedUserType = type }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Input fields
                EditProfileTextField(label = "Full Name *", value = name, onValueChange = { name = it }, placeholder = "Alexandra Chen")
                EditProfileTextField(label = "Professional Headline", value = headline, onValueChange = { headline = it }, placeholder = "Founder & CEO at NexusAI")
                EditProfileTextField(label = "Current Role", value = role, onValueChange = { role = it }, placeholder = "CEO")
                EditProfileTextField(label = "Company", value = company, onValueChange = { company = it }, placeholder = "NexusAI")
                EditProfileTextField(label = "Location", value = location, onValueChange = { location = it }, placeholder = "San Francisco, CA")
                EditProfileTextField(label = "Bio", value = bio, onValueChange = { bio = it }, placeholder = "Tell other members about yourself...", singleLine = false)

                if (localError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(localError, color = CosmosError, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(100.dp))
            }

            // Bottom Save Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmosBackground)
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(Alignment.Center))
                } else {
                    CosmosButton(
                        text = "Save Changes",
                        onClick = {
                            if (name.isNotBlank() && selectedUserType.isNotBlank()) {
                                val imageBytes = when {
                                    selectedImageUri != null -> {
                                        try {
                                            context.contentResolver.openInputStream(selectedImageUri!!)?.use { it.readBytes() }
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    selectedImageBitmap != null -> {
                                        try {
                                            val stream = ByteArrayOutputStream()
                                            selectedImageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                            stream.toByteArray()
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    else -> null
                                }

                                val updatedMember = currentUserState?.copy(
                                    name = name,
                                    headline = headline.ifBlank { "$selectedUserType at $company" },
                                    role = role,
                                    company = company,
                                    location = location,
                                    primaryUserType = selectedUserType,
                                    bio = bio
                                ) ?: Member(
                                    id = "",
                                    name = name,
                                    headline = headline.ifBlank { "$selectedUserType at $company" },
                                    role = role,
                                    company = company,
                                    location = location,
                                    primaryUserType = selectedUserType,
                                    bio = bio,
                                    avatarUrl = currentAvatarUrl
                                )

                                authViewModel.saveOnboarding(
                                    member = updatedMember,
                                    onSuccess = onBack,
                                    imageBytes = imageBytes
                                )
                            } else {
                                localError = "Please fill in all required (*) fields"
                            }
                        },
                        enabled = name.isNotBlank() && selectedUserType.isNotBlank()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
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
            maxLines = if (singleLine) 1 else 5
        )
    }
}

