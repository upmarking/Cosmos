package app.cosmos.com.screens.events

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.EventPaymentRecord
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.AuthViewModel
import app.cosmos.com.ui.viewmodel.EventViewModel
import app.cosmos.com.navigation.Screen
import coil.compose.AsyncImage

@Composable
fun EventLobbyScreen(
    eventId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    eventViewModel: EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(eventId) {
        eventViewModel.selectEvent(eventId)
        eventViewModel.loadEventParticipants(eventId)
        eventViewModel.loadEventRounds(eventId)
        eventViewModel.loadEventRegistrants(eventId)
    }

    val eventState by eventViewModel.activeEvent.collectAsState()
    val participants by eventViewModel.eventParticipants.collectAsState()
    val eventRounds by eventViewModel.eventRounds.collectAsState()
    val eventRegistrants by eventViewModel.eventRegistrants.collectAsState()
    val currentUserState by authViewModel.currentUser.collectAsState()
    val currentUserId = currentUserState?.id

    val context = LocalContext.current
    val isRegisteringPaid by eventViewModel.isRegisteringPaid.collectAsState()
    val latestPaymentRecord by eventViewModel.paymentRecord.collectAsState()

    // Registration dialog state (for free events)
    var showRegistrationDialog by remember { mutableStateOf(false) }
    var registrationName by remember(currentUserState) { mutableStateOf(currentUserState?.name ?: "") }
    var registrationEmail by remember(currentUserState) { mutableStateOf(currentUserState?.email ?: "") }

    // Paid event flow state
    var showPaidSheet by remember { mutableStateOf(false) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var successPaymentRecord by remember { mutableStateOf<EventPaymentRecord?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (eventState == null) {
        CosmosAmbientBackground {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CosmosPrimary)
            }
        }
        return
    }

    val event = eventState!!
    val isCreator = event.createdBy == currentUserId
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Participants", "Schedule", "My Meetings")

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            val isGradientCover = event.coverUrl.startsWith("gradient:") || event.coverUrl.isEmpty()
            val coverGradient = if (event.coverUrl.startsWith("gradient:")) EventGradient.fromId(event.coverUrl) else EventGradient.COSMOS_GLOW

            // Hero
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGradientCover) {
                    Box(modifier = Modifier.matchParentSize().background(coverGradient.brush))
                } else {
                    AsyncImage(
                        model = event.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (event.isPaid) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(event.price, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isCreator) {
                                IconButton(onClick = {
                                    onNavigate(Screen.PostEvent.createRoute(event.id))
                                }) {
                                    Icon(Icons.Default.Edit, "Edit Event", tint = Color.White)
                                }
                                IconButton(onClick = {
                                    showDeleteConfirmation = true
                                }) {
                                    Icon(Icons.Default.Delete, "Delete Event", tint = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    CosmosTagChip(text = event.type.label, backgroundColor = Color.White.copy(alpha = 0.2f), textColor = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(event.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("📅 ${event.date}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        Text("📍 ${event.location}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(16.dp))
                    // Progress
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { event.participantCount.toFloat() / event.maxParticipants },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Text("${event.participantCount}/${event.maxParticipants}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                    Spacer(Modifier.height(20.dp))
                    CosmosButton(
                        text = if (isCreator) "✓ Hosting" else if (event.isRegistered) "✓ Participating" else if (event.isPaid) "Pay & Participate" else "Participate",
                        onClick = {
                            if (event.isPaid) {
                                showPaidSheet = true
                            } else {
                                showRegistrationDialog = true
                            }
                        },
                        enabled = !event.isRegistered && !isCreator
                    )
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CosmosBackground,
                contentColor = CosmosPrimary,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                        selectedContentColor = CosmosPrimary,
                        unselectedContentColor = CosmosOnSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Overview
                        item {
                            CosmosGlassCard(showTopGradientBorder = false) {
                                Text("About This Event", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 8.dp))
                                Text(event.description, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text("Format", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.padding(bottom = 4.dp))
                                val formatText = if (eventRounds.isNotEmpty()) {
                                    "${eventRounds.size} rounds of ${eventRounds.first().duration}-minute networking with AI-matched participants."
                                } else {
                                    "Multiple networking rounds with AI-matched participants."
                                }
                                Text(formatText, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                if (event.isPaid) {
                                    Spacer(Modifier.height(12.dp))
                                    Text("Refund Policy", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.padding(bottom = 4.dp))
                                    Text("Better rated conversations → better refund. Rate your meetings after the event.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                }
                            }
                        }
                    }
                    1 -> {
                        // Participants tab — host sees registrant name + email
                        if (!isCreator) {
                            item {
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Text("Only the event organizer can view the participant list.", color = CosmosOnSurfaceVariant)
                                }
                            }
                        } else if (eventRegistrants.isEmpty()) {
                            item {
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("📭", style = MaterialTheme.typography.displaySmall)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "No participants yet",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = CosmosOnBackground
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Share your event to get participants!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CosmosOnSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            // Summary header
                            item {
                                CosmosGlassCard(showTopGradientBorder = true) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Registered Participants",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = CosmosOnBackground
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(CosmosPrimary.copy(alpha = 0.15f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "${eventRegistrants.size}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = CosmosPrimary
                                            )
                                        }
                                    }
                                }
                            }
                            items(eventRegistrants) { registrant ->
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Avatar with initial
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(CosmosGradientStart, CosmosGradientEnd)
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = registrant.name.firstOrNull()?.uppercase() ?: "?",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                registrant.name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = CosmosOnBackground
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Email,
                                                    contentDescription = null,
                                                    tint = CosmosOnSurfaceVariant,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    registrant.email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = CosmosOnSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                            // Payment status for paid events
                                            if (event.isPaid && registrant.paymentStatus.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val isPaid = registrant.paymentStatus == "CONFIRMED"
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                if (isPaid) CosmosSuccess.copy(alpha = 0.12f)
                                                                else Color(0xFFFF9800).copy(alpha = 0.12f)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            if (isPaid) "✓ Paid" else "⏳ Pending",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isPaid) CosmosSuccess else Color(0xFFFF9800),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    if (registrant.amountPaid > 0) {
                                                        Text(
                                                            "${event.currencySymbol}${registrant.amountPaid.toLong()}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = CosmosOnSurfaceVariant,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    if (registrant.transactionId.isNotBlank()) {
                                                        Text(
                                                            "Txn: ${registrant.transactionId}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        item {
                            val scheduleItems = mutableListOf<Pair<String, String>>()
                            var offset = 0
                            
                            scheduleItems.add(addMinutesToTimeString(event.time, offset) to "Registration & Welcome")
                            offset += 15
                            
                            if (eventRounds.isNotEmpty()) {
                                eventRounds.forEach { round ->
                                    val roundTime = addMinutesToTimeString(event.time, offset)
                                    scheduleItems.add(roundTime to "${round.title} (${round.duration} min)")
                                    offset += round.duration
                                }
                            } else {
                                val defaultRounds = listOf(
                                    "Round 1 — AI Matching" to 20,
                                    "Round 2 — Open Swap" to 20,
                                    "Round 3 — Industry Focus" to 20
                                )
                                defaultRounds.forEach { (title, duration) ->
                                    scheduleItems.add(addMinutesToTimeString(event.time, offset) to title)
                                    offset += duration
                                }
                            }
                            
                            scheduleItems.add(addMinutesToTimeString(event.time, offset) to "Open Networking")
                            offset += 60
                            scheduleItems.add(addMinutesToTimeString(event.time, offset) to "Feedback & Wrap-up")
                            
                            Column {
                                scheduleItems.forEach { (time, activity) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(time, style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.width(80.dp))
                                        Text(activity, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                                    }
                                    Divider(color = CosmosOutlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                    3 -> {
                        item {
                            if (!event.isRegistered && !isCreator) {
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text("🔒", style = MaterialTheme.typography.displayLarge)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Register to see your schedule", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                        Text("After registration, your AI-matched meeting schedule will appear here.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                    }
                                }
                            } else {
                                val roundsWithOtherParticipant = eventRounds.mapNotNull { round ->
                                    val hasCurrentUser = round.participants.any { it.id == currentUserId }
                                    if (hasCurrentUser) {
                                        val other = round.participants.firstOrNull { it.id != currentUserId }
                                        if (other != null) round to other else null
                                    } else {
                                        null
                                    }
                                }

                                if (roundsWithOtherParticipant.isEmpty()) {
                                    CosmosGlassCard(showTopGradientBorder = false) {
                                        Text("No meetings scheduled yet.", color = CosmosOnSurfaceVariant, modifier = Modifier.fillMaxWidth())
                                    }
                                } else {
                                    roundsWithOtherParticipant.forEachIndexed { index, (round, member) ->
                                        val roundStartTime = getRoundStartTime(event.time, eventRounds, index)
                                        CosmosGlassCard(showTopGradientBorder = false) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text("Round ${index + 1}", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary, modifier = Modifier.width(64.dp))
                                                CosmosAvatar(avatarUrl = member.avatarUrl, name = member.name, size = 40.dp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(member.name, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                                                    Text("${round.duration} min · $roundStartTime", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // ── Registration Dialog ─────────────────────────────────────────────────
    if (showRegistrationDialog) {
        AlertDialog(
            onDismissRequest = { showRegistrationDialog = false },
            containerColor = Color(0xFF1A1D24),
            titleContentColor = CosmosOnBackground,
            textContentColor = CosmosOnSurfaceVariant,
            shape = RoundedCornerShape(20.dp),
            title = {
                Column {
                    Text(
                        "Participate in Event",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosPrimary
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Please confirm your details to register.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant
                    )

                    OutlinedTextField(
                        value = registrationName,
                        onValueChange = { registrationName = it },
                        label = { Text("Full Name") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = CosmosPrimary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmosPrimary,
                            unfocusedBorderColor = CosmosOutlineVariant,
                            focusedLabelColor = CosmosPrimary,
                            unfocusedLabelColor = CosmosOnSurfaceVariant,
                            cursorColor = CosmosPrimary,
                            focusedTextColor = CosmosOnBackground,
                            unfocusedTextColor = CosmosOnBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = registrationEmail,
                        onValueChange = { registrationEmail = it },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = CosmosPrimary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmosPrimary,
                            unfocusedBorderColor = CosmosOutlineVariant,
                            focusedLabelColor = CosmosPrimary,
                            unfocusedLabelColor = CosmosOnSurfaceVariant,
                            cursorColor = CosmosPrimary,
                            focusedTextColor = CosmosOnBackground,
                            unfocusedTextColor = CosmosOnBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                val isValid = registrationName.isNotBlank() && registrationEmail.isNotBlank() && registrationEmail.contains("@")
                Button(
                    onClick = {
                        eventViewModel.register(eventId, registrationName.trim(), registrationEmail.trim())
                        showRegistrationDialog = false
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CosmosPrimary,
                        contentColor = CosmosBackground,
                        disabledContainerColor = CosmosPrimary.copy(alpha = 0.3f),
                        disabledContentColor = CosmosBackground.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Participate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRegistrationDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = CosmosOnSurfaceVariant)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    // ── Paid Event Payment Sheet ───────────────────────────────────────────────
    if (showPaidSheet && event.isPaid) {
        PaidEventRegistrationSheet(
            event = event,
            userName = registrationName,
            userEmail = registrationEmail,
            isRegistering = isRegisteringPaid,
            onRegisterWithPayment = { transactionId ->
                eventViewModel.registerWithPayment(
                    eventId = eventId,
                    name = registrationName.trim(),
                    email = registrationEmail.trim(),
                    transactionId = transactionId.trim(),
                    amount = event.priceAmount,
                    currency = event.currency,
                    onSuccess = { record ->
                        showPaidSheet = false
                        successPaymentRecord = record
                        showSuccessOverlay = true
                    },
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showPaidSheet = false }
        )
    }

    // ── Payment Success Overlay ────────────────────────────────────────────────
    if (showSuccessOverlay && successPaymentRecord != null) {
        EventPaymentSuccessOverlay(
            event = event,
            paymentRecord = successPaymentRecord!!,
            onDismiss = {
                showSuccessOverlay = false
                successPaymentRecord = null
                eventViewModel.clearPaymentRecord()
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = Color(0xFF1A1D24),
            titleContentColor = CosmosOnBackground,
            textContentColor = CosmosOnSurfaceVariant,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Delete Event",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${event.title}\"? This will permanently remove the event and all participant registrations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        eventViewModel.deleteEvent(
                            eventId = event.id,
                            onSuccess = {
                                Toast.makeText(context, "Event deleted successfully 🗑️", Toast.LENGTH_SHORT).show()
                                showDeleteConfirmation = false
                                onBack()
                            },
                            onError = { errorMsg ->
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                showDeleteConfirmation = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CosmosError,
                        contentColor = CosmosOnError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = CosmosOnSurfaceVariant)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
