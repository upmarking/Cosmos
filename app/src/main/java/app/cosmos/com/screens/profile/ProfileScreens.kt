package app.cosmos.com.screens.profile

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
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.*
import app.cosmos.com.navigation.Screen
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.vector.ImageVector
import app.cosmos.com.data.ValidationUtils
import kotlinx.coroutines.launch

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
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    profileViewModel: app.cosmos.com.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    communityViewModel: app.cosmos.com.ui.viewmodel.CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    if (currentUserState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmosBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = CosmosPrimary)
        }
        return
    }
    val me = currentUserState!!

    val notifications by profileViewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    val circles by communityViewModel.circles.collectAsState()
    val joinedCirclesCount = circles.count { it.isJoined }

    LaunchedEffect(Unit) {
        communityViewModel.loadCircles()
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Premium sticky top bar ────────────────────────────────────────
            CosmosGlassTopBar(
                pageTitle = "Settings",
                notificationCount = unreadCount,
                onNotificationsClick = onNotificationsTap,
                extraActions = {
                    GlassIconButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        onClick = onSettingsTap
                    )
                }
            )

            // ── Scrollable content ────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Profile hero
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                            CosmosStatCard("Followers", "${me.followersCount}", modifier = Modifier.weight(1f))
                            CosmosStatCard("Following", "${me.followingCount}", modifier = Modifier.weight(1f), accent = CosmosSecondary)
                            CosmosStatCard("Connections", "${me.connectionsCount}", modifier = Modifier.weight(1f), accent = CosmosTertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CosmosStatCard(label = "Events", value = "${me.eventsAttended}", modifier = Modifier.weight(1f), accent = CosmosGradientStart)
                            CosmosStatCard(label = "Follow-ups", value = "${me.followUpsCompleted}", modifier = Modifier.weight(1f), accent = CosmosSuccess)
                            CosmosStatCard(label = "Orbits", value = "$joinedCirclesCount", modifier = Modifier.weight(1f), accent = CosmosSecondary)
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
                        Triple(Icons.Default.Settings, "Control Center", Screen.Settings.route),
                        Triple(Icons.Default.HelpOutline, "Help & Support", Screen.HelpSupport.route)
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
}

@Composable
fun MembershipTiersScreen(
    onBack: () -> Unit,
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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

    // State for checkout dialog
    var selectedTierToUpgrade by remember { mutableStateOf<String?>(null) }
    var upgradePrice by remember { mutableStateOf(0.0) }
    val coroutineScope = rememberCoroutineScope()

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
                                    CosmosOutlinedButton(
                                        text = "Upgrade to $name",
                                        onClick = {
                                            selectedTierToUpgrade = name
                                            upgradePrice = if (name == "Member") 29.0 else 99.0
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Render Checkout dialog
    if (selectedTierToUpgrade != null) {
        CheckoutDialog(
            tierName = selectedTierToUpgrade!!,
            priceInUsd = upgradePrice,
            onDismiss = { selectedTierToUpgrade = null },
            onPaymentSuccess = { txId, methodUsed ->
                val newTier = when (selectedTierToUpgrade) {
                    "Member" -> MembershipTier.MEMBER
                    "Inner Circle" -> MembershipTier.INNER_CIRCLE
                    else -> MembershipTier.EXPLORER
                }
                currentUserState?.let { user ->
                    val updatedUser = user.copy(
                        membershipTier = newTier,
                        monthlyConnectionLimit = if (newTier == MembershipTier.INNER_CIRCLE) 999 else 10
                    )
                    authViewModel.updateProfile(updatedUser) {
                        coroutineScope.launch {
                            app.cosmos.com.data.repository.ServiceLocator.notificationRepository.createNotification(
                                userId = user.id,
                                type = app.cosmos.com.data.model.NotificationType.COMMUNITY_ANNOUNCEMENT,
                                title = "Membership Upgraded! 🎉",
                                body = "Welcome to the $selectedTierToUpgrade tier. Payment confirmed via $methodUsed (ID: $txId).",
                                actionId = txId
                            )
                        }
                    }
                }
                selectedTierToUpgrade = null
            }
        )
    }
}

@Composable
fun NotificationsCenterScreen(
    onBack: () -> Unit,
    onIntroRequest: (String) -> Unit,
    onChatTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    profileViewModel: app.cosmos.com.ui.viewmodel.ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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
                                NotificationType.CONNECTION_REQUEST -> onNavigate(Screen.ConnectionRequests.route)
                                NotificationType.CONNECTION_ACCEPTED -> onChatTap(notif.actionId)
                                NotificationType.COMMUNITY_ANNOUNCEMENT -> {
                                    if (notif.actionId.isNotEmpty()) {
                                        onNavigate(Screen.OrbitMembers.createRoute(notif.actionId))
                                    } else {
                                        onNavigate(Screen.Communities.route)
                                    }
                                }
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
                                NotificationType.CONNECTION_REQUEST -> onNavigate(Screen.ConnectionRequests.route)
                                NotificationType.CONNECTION_ACCEPTED -> onChatTap(notif.actionId)
                                NotificationType.COMMUNITY_ANNOUNCEMENT -> {
                                    if (notif.actionId.isNotEmpty()) {
                                        onNavigate(Screen.OrbitMembers.createRoute(notif.actionId))
                                    } else {
                                        onNavigate(Screen.Communities.route)
                                    }
                                }
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
        NotificationType.CONNECTION_REQUEST -> Icons.Default.PersonAdd
        NotificationType.CONNECTION_ACCEPTED -> Icons.Default.Handshake
        else -> Icons.Default.Notifications
    }
    val iconColor = when (notification.type) {
        NotificationType.NEW_MATCH -> Color(0xFFE91E63)
        NotificationType.ENDORSEMENT_RECEIVED -> CosmosTertiary
        NotificationType.AI_SUMMARY_READY -> CosmosPrimary
        NotificationType.CONNECTION_REQUEST -> Color(0xFF2196F3)
        NotificationType.CONNECTION_ACCEPTED -> Color(0xFF4CAF50)
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
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val currentUserState by authViewModel.currentUser.collectAsState()

    if (currentUserState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmosBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = CosmosPrimary)
        }
        return
    }
    val user = currentUserState!!

    // Dialog state
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showConnectedAccountsDialog by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showMatchingPrefsDialog by remember { mutableStateOf(false) }
    var showAvailabilityDialog by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var showPauseAccountConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var isConnectingLinkedIn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val triggerConnectLinkedIn: () -> Unit = {
        isConnectingLinkedIn = true
        coroutineScope.launch {
            kotlinx.coroutines.delay(1500)
            isConnectingLinkedIn = false
            val updated = user.copy(
                isLinkedInConnected = true,
                name = user.name.ifBlank { "Alexandra Chen" },
                headline = user.headline.ifBlank { "Founder & CEO at NexusAI" },
                role = user.role.ifBlank { "CEO" },
                company = user.company.ifBlank { "NexusAI" },
                location = user.location.ifBlank { "San Francisco, CA" }
            )
            authViewModel.updateProfile(updated) {
                android.widget.Toast.makeText(context, "LinkedIn connected!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Change Password inputs
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var changePasswordError by remember { mutableStateOf("") }

    // Availability Preferences inputs
    var availabilityText by remember { mutableStateOf(user.availabilityPreferences) }
    LaunchedEffect(user.availabilityPreferences) {
        availabilityText = user.availabilityPreferences
    }

    // Blocked User list and input
    var blockedInputId by remember { mutableStateOf("") }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Control Center", onBack = onBack)

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)) {
                // ── Account Section ──────────────────────────────────────────
                item {
                    Text(
                        text = "ACCOUNT",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CosmosPrimary,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                item {
                    CosmosSettingsCard {
                        SettingsItem(
                            title = "Edit Profile",
                            icon = Icons.Outlined.Person,
                            onClick = onEditProfileTap
                        )
                        SettingsItem(
                            title = "Change Password",
                            icon = Icons.Outlined.Lock,
                            onClick = { showChangePasswordDialog = true }
                        )
                        SettingsItem(
                            title = "Connected Accounts",
                            icon = Icons.Outlined.Link,
                            onClick = { showConnectedAccountsDialog = true }
                        )
                        SettingsItemWithTrailingText(
                            title = "LinkedIn Connection",
                            icon = Icons.Default.Handshake,
                            valueText = if (user.isLinkedInConnected) "Connected" else "Not Connected",
                            onClick = {
                                if (user.isLinkedInConnected) {
                                    showDisconnectConfirm = true
                                } else {
                                    triggerConnectLinkedIn()
                                }
                            },
                            showDivider = false
                        )
                    }
                }

                // ── Notifications Section ────────────────────────────────────
                item {
                    Text(
                        text = "NOTIFICATIONS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CosmosPrimary,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                item {
                    CosmosSettingsCard {
                        SettingsItemWithSwitch(
                            title = "New Matches",
                            icon = Icons.Outlined.Favorite,
                            checked = user.notificationNewMatches,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationNewMatches = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Messages",
                            icon = Icons.Outlined.Chat,
                            checked = user.notificationMessages,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationMessages = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Event Invitations",
                            icon = Icons.Outlined.Event,
                            checked = user.notificationEventInvitations,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationEventInvitations = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Event Reminders",
                            icon = Icons.Outlined.NotificationsActive,
                            checked = user.notificationEventReminders,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationEventReminders = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "AI Summaries",
                            icon = Icons.Default.AutoAwesome,
                            checked = user.notificationAiSummaries,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationAiSummaries = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Follow-up Reminders",
                            icon = Icons.Outlined.Notifications,
                            checked = user.notificationFollowUpReminders,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationFollowUpReminders = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Warm Intro Requests",
                            icon = Icons.Outlined.PersonAdd,
                            checked = user.notificationWarmIntroRequests,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationWarmIntroRequests = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Community Announcements",
                            icon = Icons.Outlined.Campaign,
                            checked = user.notificationCommunityAnnouncements,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationCommunityAnnouncements = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Endorsements",
                            icon = Icons.Outlined.Star,
                            checked = user.notificationEndorsements,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(notificationEndorsements = newVal)) },
                            showDivider = false
                        )
                    }
                }

                // ── Privacy Section ──────────────────────────────────────────
                item {
                    Text(
                        text = "PRIVACY",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CosmosPrimary,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                item {
                    CosmosSettingsCard {
                        SettingsItemWithSwitch(
                            title = "Profile Visibility",
                            icon = Icons.Outlined.Visibility,
                            checked = user.privacyProfileVisibility,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(privacyProfileVisibility = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Show LinkedIn Connection",
                            icon = Icons.Outlined.AccountCircle,
                            checked = user.privacyShowLinkedIn,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(privacyShowLinkedIn = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Allow Warm Intro Requests",
                            icon = Icons.Outlined.Link,
                            checked = user.privacyAllowWarmIntros,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(privacyAllowWarmIntros = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Show Mutual Connections",
                            icon = Icons.Outlined.People,
                            checked = user.privacyShowMutualConnections,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(privacyShowMutualConnections = newVal)) }
                        )
                        SettingsItemWithSwitch(
                            title = "Data & Analytics",
                            icon = Icons.Outlined.Analytics,
                            checked = user.privacyDataAnalytics,
                            onCheckedChange = { newVal -> authViewModel.updateProfile(user.copy(privacyDataAnalytics = newVal)) },
                            showDivider = false
                        )
                    }
                }

                // ── Networking Section ───────────────────────────────────────
                item {
                    Text(
                        text = "NETWORKING",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CosmosPrimary,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                item {
                    CosmosSettingsCard {
                        SettingsItemWithTrailingText(
                            title = "Monthly Connection Limit",
                            icon = Icons.Outlined.TrendingUp,
                            valueText = if (user.monthlyConnectionLimit > 100) "Unlimited" else "${user.monthlyConnectionLimit}",
                            onClick = { showLimitDialog = true }
                        )
                        SettingsItem(
                            title = "Matching Preferences",
                            icon = Icons.Outlined.Tune,
                            onClick = { showMatchingPrefsDialog = true }
                        )
                        SettingsItem(
                            title = "Availability Preferences",
                            icon = Icons.Outlined.Schedule,
                            onClick = { showAvailabilityDialog = true }
                        )
                        SettingsItem(
                            title = "Blocked Users",
                            icon = Icons.Outlined.Block,
                            onClick = { showBlockedUsersDialog = true },
                            showDivider = false
                        )
                    }
                }

                // ── Danger Zone Section ──────────────────────────────────────
                item {
                    Text(
                        text = "DANGER ZONE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CosmosError,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                item {
                    CosmosSettingsCard {
                        SettingsItemDanger(
                            title = "Sign Out",
                            icon = Icons.Outlined.ExitToApp,
                            onClick = {
                                authViewModel.signOut {
                                    onSignOut()
                                }
                            }
                        )
                        SettingsItemDanger(
                            title = "Pause Account",
                            icon = Icons.Outlined.PauseCircle,
                            onClick = { showPauseAccountConfirm = true }
                        )
                        SettingsItemDanger(
                            title = "Delete Account",
                            icon = Icons.Outlined.Delete,
                            onClick = { showDeleteAccountConfirm = true },
                            showDivider = false
                        )
                    }
                }
            }

        // ── Dialogs ──────────────────────────────────────────────────────────

        // 1. Change Password Dialog
        if (showChangePasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    showChangePasswordDialog = false
                    currentPassword = ""
                    newPassword = ""
                    confirmNewPassword = ""
                    changePasswordError = ""
                },
                title = { Text("Change Password", color = CosmosOnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter details to update your password.", color = CosmosOnSurfaceVariant)
                        
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            placeholder = { Text("Current Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant,
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground
                            )
                        )
                        
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            placeholder = { Text("New Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant,
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground
                            )
                        )
                        
                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = { confirmNewPassword = it },
                            placeholder = { Text("Confirm New Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant,
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground
                            )
                        )
                        
                        if (changePasswordError.isNotEmpty()) {
                            Text(changePasswordError, color = CosmosError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (currentPassword.isBlank()) {
                                changePasswordError = "Current password is required"
                            } else {
                                val matchValidation = ValidationUtils.validatePasswordMatch(newPassword, confirmNewPassword)
                                if (!matchValidation.isValid) {
                                    changePasswordError = matchValidation.errorMessage ?: "Passwords do not match"
                                } else {
                                    val passwordValidation = ValidationUtils.validatePassword(newPassword)
                                    if (!passwordValidation.isValid) {
                                        changePasswordError = passwordValidation.errorMessage ?: "Invalid password"
                                    } else {
                                        authViewModel.updatePassword(
                                            currentPassword = currentPassword,
                                            newPassword = newPassword,
                                            onSuccess = {
                                                showChangePasswordDialog = false
                                                currentPassword = ""
                                                newPassword = ""
                                                confirmNewPassword = ""
                                                changePasswordError = ""
                                                android.widget.Toast.makeText(context, "Password updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                changePasswordError = err
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Update", color = CosmosPrimary)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showChangePasswordDialog = false
                            currentPassword = ""
                            newPassword = ""
                            confirmNewPassword = ""
                            changePasswordError = ""
                        }
                    ) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        // 2. Connected Accounts Dialog
        if (showConnectedAccountsDialog) {
            AlertDialog(
                onDismissRequest = { showConnectedAccountsDialog = false },
                title = { Text("Connected Accounts", color = CosmosOnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Link your professional profiles to enrich your matches.", color = CosmosOnSurfaceVariant)
                        
                        // LinkedIn (Integrated)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(CosmosLinkedIn), contentAlignment = Alignment.Center) {
                                    Text("in", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Column {
                                    Text("LinkedIn", color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                                    Text(if (user.isLinkedInConnected) "Imported headline & skills" else "Not connected", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = user.isLinkedInConnected,
                                onCheckedChange = { newVal ->
                                    if (newVal) {
                                        triggerConnectLinkedIn()
                                    } else {
                                        showDisconnectConfirm = true
                                    }
                                }
                            )
                        }

                        // Google (Mock)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(CosmosError.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBox, null, tint = CosmosError, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text("Google", color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                                    Text("Sync calendar events", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                            }
                            TextButton(onClick = {
                                android.widget.Toast.makeText(context, "Google Calendar integrated!", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Connect", color = CosmosPrimary)
                            }
                        }

                        // GitHub (Mock)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(CosmosOutline.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Code, null, tint = CosmosOnBackground, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text("GitHub", color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                                    Text("Show developer contributions", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                            }
                            TextButton(onClick = {
                                android.widget.Toast.makeText(context, "GitHub Account linked!", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Link", color = CosmosPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showConnectedAccountsDialog = false }) {
                        Text("Done", color = CosmosPrimary)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        // 3. Connection Limit Dialog
        if (showLimitDialog) {
            val limits = listOf(3, 5, 10, 20, 50, 999)
            AlertDialog(
                onDismissRequest = { showLimitDialog = false },
                title = { Text("Monthly Connection Limit", color = CosmosOnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Control the maximum number of new introductions you accept per month to maintain connection quality.", color = CosmosOnSurfaceVariant)
                        limits.forEach { lim ->
                            val label = if (lim == 999) "Unlimited" else "$lim connections"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updated = user.copy(monthlyConnectionLimit = lim)
                                        authViewModel.updateProfile(updated) {
                                            showLimitDialog = false
                                            android.widget.Toast.makeText(context, "Monthly limit set to $label", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = CosmosOnBackground, fontWeight = if (user.monthlyConnectionLimit == lim) FontWeight.Bold else FontWeight.Normal)
                                if (user.monthlyConnectionLimit == lim) {
                                    Icon(Icons.Default.Check, null, tint = CosmosPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Divider(color = CosmosOutlineVariant.copy(alpha = 0.1f), thickness = 0.5.dp)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLimitDialog = false }) {
                        Text("Close", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        // 4. Matching Preferences Dialog
        if (showMatchingPrefsDialog) {
            val availableTags = listOf("AI/ML", "B2B SaaS", "ClimateTech", "Biotech", "VC", "Design", "UI/UX", "Product Management", "Growth", "Scaling", "Tech", "Fundraising")
            val selectedMatching = remember { mutableStateListOf<String>().apply { addAll(user.matchingPreferences) } }
            AlertDialog(
                onDismissRequest = { showMatchingPrefsDialog = false },
                title = { Text("Matching Preferences", color = CosmosOnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("We will prioritize suggesting profiles with tags selected below.", color = CosmosOnSurfaceVariant)
                        Box(modifier = Modifier.height(260.dp)) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(availableTags) { tag ->
                                    val isChecked = selectedMatching.contains(tag)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isChecked) selectedMatching.remove(tag) else selectedMatching.add(tag)
                                            }
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(tag, color = CosmosOnBackground)
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                if (isChecked) selectedMatching.remove(tag) else selectedMatching.add(tag)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = CosmosPrimary)
                                        )
                                    }
                                    Divider(color = CosmosOutlineVariant.copy(alpha = 0.1f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updated = user.copy(matchingPreferences = selectedMatching.toList())
                            authViewModel.updateProfile(updated) {
                                showMatchingPrefsDialog = false
                                android.widget.Toast.makeText(context, "Matching preferences updated!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save", color = CosmosPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMatchingPrefsDialog = false }) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        // 5. Availability Preferences Dialog
        if (showAvailabilityDialog) {
            AlertDialog(
                onDismissRequest = { showAvailabilityDialog = false },
                title = { Text("Availability Preferences", color = CosmosOnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Set your preferred days, times, and meeting format (Virtual/In-Person).", color = CosmosOnSurfaceVariant)
                        OutlinedTextField(
                            value = availabilityText,
                            onValueChange = { availabilityText = it },
                            placeholder = { Text("e.g. Fridays 2-4 PM Virtual, coffee in SF") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant,
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updated = user.copy(availabilityPreferences = availabilityText)
                            authViewModel.updateProfile(updated) {
                                showAvailabilityDialog = false
                                android.widget.Toast.makeText(context, "Availability updated!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save", color = CosmosPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAvailabilityDialog = false }) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        // 6. Blocked Users Dialog
        if (showBlockedUsersDialog) {
            val blockedList = remember { mutableStateListOf<String>().apply { addAll(user.blockedUsers) } }
            AlertDialog(
                onDismissRequest = { showBlockedUsersDialog = false },
                title = { Text("Blocked Users", color = CosmosOnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Members you block will not see your profile or be matched with you.", color = CosmosOnSurfaceVariant)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = blockedInputId,
                                onValueChange = { blockedInputId = it },
                                placeholder = { Text("Enter ID or name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CosmosPrimary,
                                    unfocusedBorderColor = CosmosOutlineVariant,
                                    focusedTextColor = CosmosOnBackground,
                                    unfocusedTextColor = CosmosOnBackground
                                )
                            )
                            Button(
                                onClick = {
                                    if (blockedInputId.isNotBlank()) {
                                        blockedList.add(blockedInputId.trim())
                                        blockedInputId = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
                            ) {
                                Text("Block")
                            }
                        }

                        if (blockedList.isEmpty()) {
                            Text("No blocked users.", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                        } else {
                            Box(modifier = Modifier.height(180.dp)) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(blockedList) { blockedUser ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(blockedUser, color = CosmosOnBackground)
                                            IconButton(onClick = { blockedList.remove(blockedUser) }) {
                                                Icon(Icons.Default.Delete, "Remove", tint = CosmosError)
                                            }
                                        }
                                        Divider(color = CosmosOutlineVariant.copy(alpha = 0.1f), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updated = user.copy(blockedUsers = blockedList.toList())
                            authViewModel.updateProfile(updated) {
                                showBlockedUsersDialog = false
                                android.widget.Toast.makeText(context, "Blocked list saved", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save", color = CosmosPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBlockedUsersDialog = false }) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        // 7. Pause Account Confirm
        if (showPauseAccountConfirm) {
            AlertDialog(
                onDismissRequest = { showPauseAccountConfirm = false },
                title = { Text("Pause Account?", color = CosmosOnBackground) },
                text = { Text("Pausing hides your profile from swipe decks, but keeps your matches, messages, and circles intact. You will be signed out.", color = CosmosOnSurfaceVariant) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updated = user.copy(isRestricted = true)
                            authViewModel.updateProfile(updated) {
                                showPauseAccountConfirm = false
                                authViewModel.signOut {
                                    onSignOut()
                                    android.widget.Toast.makeText(context, "Account paused. Sign back in anytime to unpause.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text("Pause", color = CosmosError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPauseAccountConfirm = false }) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        // 8. Delete Account Confirm
        if (showDeleteAccountConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountConfirm = false },
                title = { Text("Permanently Delete Account?", color = CosmosOnBackground) },
                text = { Text("Warning: This action is irreversible. All your profile data, connections, CRM history, circles, and messages will be permanently deleted.", color = CosmosOnSurfaceVariant) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            authViewModel.deleteAccount {
                                showDeleteAccountConfirm = false
                                onSignOut()
                                android.widget.Toast.makeText(context, "Account permanently deleted.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Text("Delete", color = CosmosError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountConfirm = false }) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        if (showDisconnectConfirm) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirm = false },
                title = { Text("Disconnect LinkedIn", color = CosmosOnBackground) },
                text = { Text("Are you sure you want to disconnect your LinkedIn profile? This will remove your verified credentials and trust badge.", color = CosmosOnSurfaceVariant) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDisconnectConfirm = false
                            val updated = user.copy(isLinkedInConnected = false)
                            authViewModel.updateProfile(updated) {
                                android.widget.Toast.makeText(context, "LinkedIn disconnected!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Disconnect", color = CosmosError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectConfirm = false }) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        if (isConnectingLinkedIn) {
            AlertDialog(
                onDismissRequest = { isConnectingLinkedIn = false },
                confirmButton = {},
                dismissButton = {},
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(CosmosLinkedIn),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("in", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text("LinkedIn Verification", color = CosmosOnBackground, style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(color = CosmosLinkedIn)
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting and importing profile data...", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
}

@Composable
fun CosmosSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16191F).copy(alpha = 0.64f))
                .border(
                    width = 1.dp,
                    color = Color(0x1AFFFFFF),
                    shape = RoundedCornerShape(16.dp)
                ),
            content = content
        )
        // Subtle top border glint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 1.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            CosmosPrimary.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CosmosPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosOnBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.15f),
            thickness = 0.5.dp
        )
    }
}

@Composable
fun SettingsItemWithTrailingText(
    title: String,
    icon: ImageVector,
    valueText: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CosmosPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosOnBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = CosmosPrimary,
            modifier = Modifier.padding(end = 4.dp)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.15f),
            thickness = 0.5.dp
        )
    }
}

@Composable
fun SettingsItemWithSwitch(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CosmosPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosOnBackground,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = CosmosPrimary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = CosmosSurfaceContainerHigh.copy(alpha = 0.5f),
                uncheckedBorderColor = CosmosOutlineVariant.copy(alpha = 0.3f)
            )
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.15f),
            thickness = 0.5.dp
        )
    }
}

@Composable
fun SettingsItemDanger(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CosmosError,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosError,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CosmosError.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.15f),
            thickness = 0.5.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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
    var initialUserType by remember { mutableStateOf("") }
    var initialCompany by remember { mutableStateOf("") }
    var initialHeadline by remember { mutableStateOf("") }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }

    var isLinkedInConnected by remember { mutableStateOf(false) }
    var isConnectingLinkedIn by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                initialUserType = member.primaryUserType
                initialCompany = member.company
                initialHeadline = member.headline
                isLinkedInConnected = member.isLinkedInConnected
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
                    Box(contentAlignment = Alignment.BottomEnd) {
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
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CosmosPrimary)
                                .border(2.dp, CosmosBackground, CircleShape)
                                .clickable { showPhotoOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit photo", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                if (showPhotoOptions) {
                    AlertDialog(
                        onDismissRequest = { showPhotoOptions = false },
                        title = { Text("Select Profile Photo", color = CosmosOnBackground) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Choose a photo from your gallery or take a new one.", color = CosmosOnSurfaceVariant)
                                if (currentAvatarUrl.isNotEmpty() || selectedImageUri != null || selectedImageBitmap != null) {
                                    TextButton(onClick = {
                                        showPhotoOptions = false
                                        selectedImageUri = null
                                        selectedImageBitmap = null
                                        currentAvatarUrl = ""
                                    }) {
                                        Text("Remove Current Photo", color = CosmosError)
                                    }
                                }
                            }
                        },
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

                // LinkedIn Connection Status Card
                Spacer(Modifier.height(16.dp))
                CosmosGlassCard(showTopGradientBorder = false) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isLinkedInConnected) CosmosSuccess.copy(alpha = 0.2f) else CosmosLinkedIn),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLinkedInConnected) {
                                Icon(Icons.Default.Check, contentDescription = "Connected", tint = CosmosSuccess, modifier = Modifier.size(20.dp))
                            } else {
                                Text("in", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isLinkedInConnected) "LinkedIn Connected" else "Connect LinkedIn",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isLinkedInConnected) CosmosSuccess else CosmosOnBackground
                            )
                            Text(
                                if (isLinkedInConnected) "Credentials linked & imported" else "Import profile & build trust",
                                style = MaterialTheme.typography.bodySmall,
                                color = CosmosOnSurfaceVariant
                            )
                        }
                        CosmosOutlinedButton(
                            text = if (isLinkedInConnected) "Disconnect" else "Connect",
                            onClick = {
                                if (isLinkedInConnected) {
                                    showDisconnectConfirm = true
                                } else {
                                    isConnectingLinkedIn = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1500)
                                        isConnectingLinkedIn = false
                                        isLinkedInConnected = true
                                        if (name.isBlank()) name = "Alexandra Chen"
                                        if (headline.isBlank()) headline = "Founder & CEO at NexusAI"
                                        if (role.isBlank()) role = "CEO"
                                        if (company.isBlank()) company = "NexusAI"
                                        if (location.isBlank()) location = "San Francisco, CA"
                                    }
                                }
                            },
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
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
                            onClick = {
                                val trimmedHeadline = headline.trim()
                                val isDefaultHeadline = trimmedHeadline.isBlank() || userTypes.any { t ->
                                    val tLower = t.lowercase()
                                    trimmedHeadline.lowercase() == tLower || trimmedHeadline.lowercase().startsWith("$tLower at")
                                }
                                if (isDefaultHeadline) {
                                    val matchingType = userTypes.find { t ->
                                        val tLower = t.lowercase()
                                        trimmedHeadline.lowercase() == tLower || trimmedHeadline.lowercase().startsWith("$tLower at")
                                    }
                                    val companySuffix = if (matchingType != null && trimmedHeadline.lowercase().startsWith("${matchingType.lowercase()} at")) {
                                        trimmedHeadline.substring(matchingType.length + 4).trim()
                                    } else {
                                        company
                                    }
                                    headline = if (companySuffix.isBlank()) type else "$type at $companySuffix"
                                }
                                selectedUserType = type
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Input fields
                EditProfileTextField(label = "Full Name *", value = name, onValueChange = { name = it }, placeholder = "Alexandra Chen")
                EditProfileTextField(label = "Professional Headline", value = headline, onValueChange = { headline = it }, placeholder = "Founder & CEO at NexusAI")
                EditProfileTextField(label = "Current Role", value = role, onValueChange = { role = it }, placeholder = "CEO")
                EditProfileTextField(
                    label = "Company",
                    value = company,
                    onValueChange = { newCompany ->
                        val trimmedHeadline = headline.trim()
                        val isDefaultHeadline = trimmedHeadline.isBlank() || userTypes.any { t ->
                            val tLower = t.lowercase()
                            trimmedHeadline.lowercase() == tLower || trimmedHeadline.lowercase().startsWith("$tLower at")
                        }
                        if (isDefaultHeadline) {
                            headline = if (newCompany.isBlank()) selectedUserType else "$selectedUserType at $newCompany"
                        }
                        company = newCompany
                    },
                    placeholder = "NexusAI"
                )
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
                                    headline = headline.ifBlank { if (company.isBlank()) selectedUserType else "$selectedUserType at $company" },
                                    role = role,
                                    company = company,
                                    location = location,
                                    primaryUserType = selectedUserType,
                                    bio = bio,
                                    isLinkedInConnected = isLinkedInConnected
                                ) ?: Member(
                                    id = "",
                                    name = name,
                                    headline = headline.ifBlank { if (company.isBlank()) selectedUserType else "$selectedUserType at $company" },
                                    role = role,
                                    company = company,
                                    location = location,
                                    email = currentUserState?.email ?: "",
                                    primaryUserType = selectedUserType,
                                    bio = bio,
                                    avatarUrl = currentAvatarUrl,
                                    isLinkedInConnected = isLinkedInConnected
                                )

                                android.widget.Toast.makeText(context, "Profile updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
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

        if (showDisconnectConfirm) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirm = false },
                title = { Text("Disconnect LinkedIn", color = CosmosOnBackground) },
                text = { Text("Are you sure you want to disconnect your LinkedIn profile? This will remove your verified credentials and trust badge.", color = CosmosOnSurfaceVariant) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDisconnectConfirm = false
                            isLinkedInConnected = false
                        }
                    ) {
                        Text("Disconnect", color = CosmosError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectConfirm = false }) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh
            )
        }

        if (isConnectingLinkedIn) {
            AlertDialog(
                onDismissRequest = { isConnectingLinkedIn = false },
                confirmButton = {},
                dismissButton = {},
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(CosmosLinkedIn),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("in", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text("LinkedIn Verification", color = CosmosOnBackground, style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(color = CosmosLinkedIn)
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting and importing profile data...", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                    }
                },
                containerColor = CosmosSurfaceContainerHigh,
                shape = RoundedCornerShape(16.dp)
            )
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

