package app.cosmos.com.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextOverflow
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
                            CosmosStatCard("Followers", "${me.followersCount}", modifier = Modifier.weight(1f), onClick = { onNavigate(Screen.NetworkRelations.createRoute("followers")) })
                            CosmosStatCard("Following", "${me.followingCount}", modifier = Modifier.weight(1f), accent = CosmosSecondary, onClick = { onNavigate(Screen.NetworkRelations.createRoute("following")) })
                            CosmosStatCard("Connections", "${me.connectionsCount}", modifier = Modifier.weight(1f), accent = CosmosTertiary, onClick = { onNavigate(Screen.NetworkRelations.createRoute("connections")) })
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
    CosmicMembershipScreen(
        onBack = onBack,
        authViewModel = authViewModel
    )
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
    onNavigate: (String) -> Unit = {},
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
    var showMatchingPrefsDialog by remember { mutableStateOf(false) }
    var showAvailabilityDialog by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var showPauseAccountConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var isConnectingLinkedIn by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }
    var activeAppIcon by remember { mutableStateOf(app.cosmos.com.data.util.AppIconManager.getActiveIcon(context)) }
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
                    CosmosSettingsSectionHeader(emoji = "🛸", title = "ACCOUNT")
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

                // ── COSMOS Membership Section ─────────────────────────────────
                item {
                    CosmosSettingsSectionHeader(emoji = "🌟", title = "COSMOS MEMBERSHIP")
                }
                item {
                    CosmosSettingsCard {
                        SettingsItemWithTrailingText(
                            title = "Current Plan",
                            icon = Icons.Outlined.WorkspacePremium,
                            valueText = "${user.membershipTier.badge} (${user.membershipTier.label})",
                            onClick = { onNavigate(Screen.MembershipTiers.route) }
                        )
                        SettingsItemWithTrailingText(
                            title = "Billing Status",
                            icon = Icons.Outlined.Verified,
                            valueText = "Lifetime Member",
                            onClick = { onNavigate(Screen.MembershipTiers.route) }
                        )
                        SettingsItem(
                            title = "Cosmic Journey & Upgrades",
                            icon = Icons.Default.RocketLaunch,
                            onClick = { onNavigate(Screen.MembershipTiers.route) },
                            showDivider = false
                        )
                    }
                }

                // ── COSMOS Appearance Section ─────────────────────────────────
                item {
                    CosmosSettingsSectionHeader(emoji = "🎨", title = "COSMOS APPEARANCE")
                }
                item {
                    CosmosSettingsCard {
                        SettingsItemWithTrailingText(
                            title = "Change App Icon",
                            icon = Icons.Outlined.Palette,
                            valueText = "${activeAppIcon.emoji} ${activeAppIcon.title}",
                            onClick = { onNavigate(Screen.ChangeAppIcon.route) },
                            showDivider = false
                        )
                    }
                }

                // ── Notifications Section ────────────────────────────────────
                item {
                    CosmosSettingsSectionHeader(emoji = "🔔", title = "NOTIFICATIONS")
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
                    CosmosSettingsSectionHeader(emoji = "🛡️", title = "PRIVACY")
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
                    CosmosSettingsSectionHeader(emoji = "🌐", title = "NETWORKING")
                }
                item {
                    CosmosSettingsCard {
                        SettingsItem(
                            title = "Network Relations",
                            icon = Icons.Outlined.People,
                            onClick = { onNavigate(Screen.NetworkRelations.createRoute("connections")) }
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
                    CosmosSettingsSectionHeader(emoji = "⚠️", title = "DANGER ZONE", color = CosmosError)
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

        // 9. App Icon Customization Dialog
        if (showAppIconDialog) {
            CosmosAppIconDialog(
                currentIcon = activeAppIcon,
                userTier = user.membershipTier,
                onDismiss = { showAppIconDialog = false },
                onSelectIcon = { newIcon ->
                    val success = app.cosmos.com.data.util.AppIconManager.setAppIcon(context, newIcon)
                    activeAppIcon = newIcon
                    if (success) {
                        android.widget.Toast.makeText(
                            context,
                            "App icon changed to ${newIcon.title}! Your launcher will update shortly.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "App icon set to ${newIcon.title}.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    showAppIconDialog = false
                },
                onUpgradePrompt = {
                    showAppIconDialog = false
                    onNavigate(Screen.MembershipTiers.route)
                }
            )
        }
    }
}
}

@Composable
fun CosmosAppIconDialog(
    currentIcon: app.cosmos.com.data.util.CosmosAppIcon,
    userTier: MembershipTier,
    onDismiss: () -> Unit,
    onSelectIcon: (app.cosmos.com.data.util.CosmosAppIcon) -> Unit,
    onUpgradePrompt: () -> Unit
) {
    var selectedIcon by remember { mutableStateOf(currentIcon) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(CosmosCosmicDeep, Color(0xFF0D0D2B))
                    )
                )
                .border(
                    1.dp,
                    CosmosOutlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "App Icon",
                            style = MaterialTheme.typography.titleLarge,
                            color = CosmosStarWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Customize your COSMOS launcher icon",
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = CosmosOnSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Icons List
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    app.cosmos.com.data.util.CosmosAppIcon.values().forEach { iconOption ->
                        val isSelected = selectedIcon == iconOption
                        val isUnlocked = app.cosmos.com.data.util.AppIconManager.isIconUnlocked(userTier, iconOption)
                        val borderColor = Color(iconOption.previewBorderColor)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) borderColor.copy(alpha = 0.15f)
                                    else CosmosGlass
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) borderColor else CosmosGlassBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    if (isUnlocked) {
                                        selectedIcon = iconOption
                                    } else {
                                        onUpgradePrompt()
                                    }
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // App Icon Preview Frame
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(iconOption.previewBgColor))
                                        .border(1.5.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CosmosIconEmblemCanvas(
                                        iconOption = iconOption,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(if (iconOption == app.cosmos.com.data.util.CosmosAppIcon.DEFAULT) 0.dp else 4.dp)
                                    )
                                }

                                // Icon Info
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            iconOption.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isUnlocked) CosmosStarWhite else CosmosStarWhite.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!isUnlocked) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(borderColor.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "🔒 ${iconOption.requiredTier.label}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = borderColor,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        iconOption.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CosmosOnSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                // Selection Indicator
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(borderColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (iconOption == app.cosmos.com.data.util.CosmosAppIcon.SUN) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else if (!isUnlocked) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = "Locked",
                                        tint = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Apply Button
                Button(
                    onClick = { onSelectIcon(selectedIcon) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CosmosPrimary
                    )
                ) {
                    Text(
                        "Set as App Icon",
                        color = CosmosOnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CosmosSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerOffset"
    )
    Box(modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0C1020).copy(alpha = 0.65f))
                .border(
                    width = 1.dp,
                    color = Color(0x0FFFFFFF),
                    shape = RoundedCornerShape(20.dp)
                ),
            content = content
        )
        // Animated shimmer top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 1.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CosmosPrimary.copy(alpha = 0.25f + shimmerOffset * 0.3f),
                            Color(0xFF60A5FA).copy(alpha = 0.3f),
                            CosmosPrimary.copy(alpha = 0.25f + (1f - shimmerOffset) * 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ── Cosmic Section Header with Emoji + Gradient Underline ──
@Composable
fun CosmosSettingsSectionHeader(
    emoji: String,
    title: String,
    color: Color = CosmosPrimary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = color
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            color.copy(alpha = 0.35f),
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with glow background
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CosmosPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CosmosPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CosmosOnBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CosmosOnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.08f),
            thickness = 0.5.dp
        )
    }
}


@Composable
fun SettingsItemWithTrailingText(
    title: String,
    icon: ImageVector,
    valueText: String,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CosmosPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CosmosPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CosmosOnBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = CosmosPrimary,
            modifier = Modifier.padding(end = if (onClick != null) 4.dp else 0.dp)
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = CosmosOnSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.08f),
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (checked) CosmosPrimary.copy(alpha = 0.12f)
                    else CosmosOnSurfaceVariant.copy(alpha = 0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) CosmosPrimary else CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CosmosOnBackground,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = CosmosPrimary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = CosmosSurfaceContainerHigh.copy(alpha = 0.4f),
                uncheckedBorderColor = CosmosOutlineVariant.copy(alpha = 0.2f)
            )
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.08f),
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CosmosError.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CosmosError,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CosmosError,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = CosmosError.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = CosmosOutlineVariant.copy(alpha = 0.08f),
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

    val userTypesWithIcons = listOf(
        "Founder" to "🚀",
        "Co-Founder" to "🛸",
        "Startup Operator" to "🪐",
        "Investor" to "💎",
        "Student" to "🎓",
        "Mentor" to "🧭",
        "Tech Professional" to "⚡",
        "Marketing Professional" to "📣",
        "Finance Professional" to "📈",
        "Legal Professional" to "⚖️",
        "Healthcare Professional" to "🧬",
        "Business Professional" to "💼",
        "Creator" to "🎨",
        "Freelancer" to "🔮",
        "Service Provider" to "🛠️",
        "Community Member" to "🌌"
    )
    val userTypes = remember { userTypesWithIcons.map { it.first } }

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
            CosmosTopBar(title = "Cosmic Profile Forge", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp))

                // Cosmic Badge Pill
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = CosmosPrimary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CosmosPrimary.copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CosmosPrimary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "COSMOS CITIZEN ID FORGE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                            color = CosmosPrimaryFixedDim
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Live Hologram Card Preview ──
                CosmosGlassCard(
                    showTopGradientBorder = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = CosmosPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "LIVE HOLOGRAM CARD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = CosmosOnSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CosmosSuccess)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Live Avatar with Cosmic Gradient Halo
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(CosmosGradientStart, CosmosGradientEnd, CosmosPrimary)
                                        )
                                    )
                                    .padding(2.5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(CosmosSurfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedImageUri != null || selectedImageBitmap != null) {
                                        AsyncImage(
                                            model = selectedImageUri ?: selectedImageBitmap,
                                            contentDescription = null,
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
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = if (name.isNotBlank()) name.take(1).uppercase() else "C",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = CosmosPrimary
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (name.isNotBlank()) name else "Your Name",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = CosmosOnBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isLinkedInConnected) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(CosmosLinkedIn),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("in", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = if (headline.isNotBlank()) headline
                                           else if (company.isNotBlank()) "$selectedUserType at $company"
                                           else selectedUserType.ifBlank { "Cosmos Citizen" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CosmosPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedUserType.isNotBlank()) {
                                        val personaIcon = userTypesWithIcons.find { it.first == selectedUserType }?.second ?: "✨"
                                        Surface(
                                            shape = RoundedCornerShape(99.dp),
                                            color = CosmosSurfaceContainerHighest,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CosmosOutlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "$personaIcon $selectedUserType",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CosmosOnSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (location.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(99.dp),
                                            color = CosmosSurfaceContainerHighest,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CosmosOutlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "📍 $location",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CosmosOnSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Profile photo edit with Cosmic Orbital Halo
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        colors = listOf(CosmosGradientStart, CosmosGradientEnd, CosmosPrimary, CosmosSecondary, CosmosGradientStart)
                                    )
                                )
                                .padding(3.dp)
                                .clickable { showPhotoOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(CosmosSurfaceContainerHigh),
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
                                            tint = CosmosPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            "Add Photo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CosmosPrimary
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd))
                                )
                                .border(2.dp, CosmosBackground, CircleShape)
                                .clickable { showPhotoOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit photo", tint = Color.White, modifier = Modifier.size(15.dp))
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                                if (isLinkedInConnected) "LinkedIn Verified & Linked" else "Connect LinkedIn Node",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isLinkedInConnected) CosmosSuccess else CosmosOnBackground
                            )
                            Text(
                                if (isLinkedInConnected) "Credentials linked & imported" else "Import profile & build peer trust",
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

                // Section: Cosmic Identity (Persona Chips)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = CosmosPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Cosmic Identity",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = CosmosOnBackground
                        )
                    }
                    Text(
                        "Select your primary role archetype in the Cosmos ecosystem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        userTypesWithIcons.forEach { (type, emoji) ->
                            val isSelected = selectedUserType == type
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = if (isSelected) CosmosPrimary.copy(alpha = 0.22f) else CosmosSurfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) CosmosPrimary else CosmosOutlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.clickable {
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
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(emoji, fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    EditProfileTextField(
                        label = "Full Legal / Display Name *",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Alexandra Chen",
                        leadingIcon = Icons.Default.Person
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Section: Orbital Trajectory
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Work, contentDescription = null, tint = CosmosSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Orbital Trajectory",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = CosmosOnBackground
                        )
                    }
                    Text(
                        "Your professional orbit, ventures, and mission statement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    EditProfileTextField(
                        label = "Professional Headline",
                        value = headline,
                        onValueChange = { headline = it },
                        placeholder = "Founder & CEO at NexusAI",
                        leadingIcon = Icons.Default.AutoAwesome
                    )

                    EditProfileTextField(
                        label = "Current Role",
                        value = role,
                        onValueChange = { role = it },
                        placeholder = "CEO / Lead Architect",
                        leadingIcon = Icons.Default.WorkOutline
                    )

                    EditProfileTextField(
                        label = "Company / Venture",
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
                        placeholder = "NexusAI",
                        leadingIcon = Icons.Default.Business
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Section: Planetary Coordinates & Transmission
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = CosmosEarthColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Planetary Coordinates & Transmission",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = CosmosOnBackground
                        )
                    }
                    Text(
                        "Where on Earth you operate and your message to the Cosmos community.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    EditProfileTextField(
                        label = "Location / Base",
                        value = location,
                        onValueChange = { location = it },
                        placeholder = "San Francisco, CA (or Remote)",
                        leadingIcon = Icons.Default.LocationOn
                    )

                    EditProfileTextField(
                        label = "Cosmic Bio / Transmission",
                        value = bio,
                        onValueChange = { bio = it },
                        placeholder = "Tell other members what you are building, what inspires you, and how you can collaborate...",
                        singleLine = false,
                        leadingIcon = Icons.Default.Description,
                        maxChar = 500
                    )
                }

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
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(Alignment.Center))
                } else {
                    CosmosButton(
                        text = "Save Cosmic Profile",
                        icon = Icons.Default.AutoAwesome,
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

                                android.widget.Toast.makeText(context, "Cosmic Profile updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
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
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    maxChar: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CosmosOnSurfaceVariant
            )
            if (maxChar != null) {
                Text(
                    "${value.length} / $maxChar",
                    style = MaterialTheme.typography.labelSmall,
                    color = CosmosOutline
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = {
                if (maxChar == null || it.length <= maxChar) {
                    onValueChange(it)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = CosmosOnSurfaceVariant.copy(alpha = 0.45f)) },
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = CosmosPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else null,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CosmosPrimary,
                unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.6f),
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


