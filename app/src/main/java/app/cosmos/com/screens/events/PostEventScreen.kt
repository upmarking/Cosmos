package app.cosmos.com.screens.events

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.EventViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEventScreen(
    eventId: String? = null,
    onBack: () -> Unit,
    onEventPosted: () -> Unit,
    eventViewModel: EventViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageZoom by remember { mutableStateOf(1f) }
    var imagePanFraction by remember { mutableStateOf(0f) }
    var selectedGradient by remember { mutableStateOf(EventGradient.COSMOS_GLOW) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> 
            selectedImageUri = uri 
            imageZoom = 1f
            imagePanFraction = 0f
        }
    )

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedPlaceId by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf(50) }
    var isPaid by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("USD") }
    var upiId by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var paymentInstructions by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    
    val isCreating by eventViewModel.isCreatingEvent.collectAsState()

    var showAiDialog by remember { mutableStateOf(false) }
    var aiPromptInput by remember { mutableStateOf("") }
    var isGeneratingDescription by remember { mutableStateOf(false) }

    val activeEvent by eventViewModel.activeEvent.collectAsState()
    var isSavingChanges by remember { mutableStateOf(false) }
    var isExistingCoverRemoved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isEditing = !eventId.isNullOrBlank() && eventId != "{eventId}" && eventId != "null"

    LaunchedEffect(eventId, isEditing) {
        if (isEditing) {
            eventId?.let { eventViewModel.selectEvent(it) }
        } else {
            eventViewModel.clearActiveEvent()
        }
    }

    LaunchedEffect(activeEvent, isEditing) {
        val event = activeEvent
        if (isEditing && event != null) {
            title = event.title
            description = event.description
            date = event.date
            time = event.time
            location = event.location
            maxParticipants = event.maxParticipants
            isPaid = event.isPaid
            price = if (event.isPaid) event.price.replace(Regex("[^0-9.]"), "") else ""
            selectedCurrency = event.currency
            upiId = event.paymentUpiId
            accountName = event.paymentAccountName
            paymentInstructions = event.paymentInstructions
            if (event.coverUrl.startsWith("gradient:")) {
                selectedGradient = EventGradient.fromId(event.coverUrl)
            }
        }
    }

    // Shimmer animation for placeholder
    val infiniteTransition = rememberInfiniteTransition(label = "HeaderShimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Shimmer"
    )

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = if (isEditing) "Edit Event" else "Create Event",
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // ── Cinematic Header with Progress ──
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Cosmic subtitle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CosmosPrimary.copy(alpha = shimmerAlpha),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isEditing) "Update your event details" else "Launch your event into the cosmos",
                                style = MaterialTheme.typography.bodySmall,
                                color = CosmosPrimary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Step Progress Indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StepIndicator(
                                step = 1,
                                label = "Details",
                                icon = Icons.Default.Edit,
                                isCompleted = title.isNotBlank(),
                                isActive = title.isBlank(),
                                modifier = Modifier.weight(1f)
                            )
                            StepIndicator(
                                step = 2,
                                label = "Schedule",
                                icon = Icons.Default.Schedule,
                                isCompleted = date.isNotBlank() && time.isNotBlank(),
                                isActive = title.isNotBlank() && (date.isBlank() || time.isBlank()),
                                modifier = Modifier.weight(1f)
                            )
                            StepIndicator(
                                step = 3,
                                label = "Location",
                                icon = Icons.Default.LocationOn,
                                isCompleted = location.isNotBlank(),
                                isActive = date.isNotBlank() && time.isNotBlank() && location.isBlank(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── Cover Image Section in Glass Card ──
                item {
                    CosmosGlassCard(showTopGradientBorder = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Section Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmosPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = CosmosPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Event Cover",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CosmosOnBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Upload a stunning visual or choose a theme",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmosOnSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            val hasExistingCover = isEditing && activeEvent?.coverUrl?.let { it.isNotBlank() && !it.startsWith("gradient:") } == true && !isExistingCoverRemoved
                            if (selectedImageUri != null || hasExistingCover) {
                                // Image Preview
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CosmosSurfaceContainerHigh)
                                        .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
                                ) {
                                    val containerHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
                                    AsyncImage(
                                        model = selectedImageUri ?: activeEvent?.coverUrl,
                                        contentDescription = "Cover Image Preview",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = imageZoom,
                                                scaleY = imageZoom,
                                                translationY = imagePanFraction * containerHeightPx
                                            )
                                    )
                                    IconButton(
                                        onClick = { 
                                            selectedImageUri = null
                                            imageZoom = 1f
                                            imagePanFraction = 0f
                                            if (isEditing) {
                                                isExistingCoverRemoved = true
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove cover image",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Zoom & Pan Controls
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Zoom", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                                        Text(
                                            "${(imageZoom * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CosmosPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = imageZoom,
                                        onValueChange = { imageZoom = it },
                                        valueRange = 1f..3f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = CosmosPrimary,
                                            activeTrackColor = CosmosPrimary,
                                            inactiveTrackColor = CosmosSurfaceContainerHigh
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Reposition", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                                        Text(
                                            "${(imagePanFraction * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CosmosPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = imagePanFraction,
                                        onValueChange = { imagePanFraction = it },
                                        valueRange = -0.5f..0.5f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = CosmosPrimary,
                                            activeTrackColor = CosmosPrimary,
                                            inactiveTrackColor = CosmosSurfaceContainerHigh
                                        )
                                    )
                                }
                            } else {
                                // Upload Placeholder with Shimmer
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CosmosSurfaceContainerLowest)
                                        .border(
                                            width = 1.5.dp,
                                            brush = Brush.linearGradient(
                                                listOf(
                                                    CosmosPrimary.copy(alpha = shimmerAlpha * 0.5f),
                                                    CosmosGradientEnd.copy(alpha = shimmerAlpha * 0.3f),
                                                    CosmosPrimary.copy(alpha = shimmerAlpha * 0.5f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(CosmosPrimary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = null,
                                                tint = CosmosPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Text(
                                            text = "Upload Cover Image",
                                            color = CosmosOnBackground,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "JPG, PNG \u2022 Max 10MB",
                                            color = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            // Gradient Theme Selector
                            Text(
                                text = "OR SELECT THEME",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EventGradient.values().forEach { gradient ->
                                    val isSelected = selectedGradient == gradient && selectedImageUri == null
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(2f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(gradient.brush)
                                            .border(
                                                width = 2.dp,
                                                color = if (isSelected) CosmosPrimary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                selectedGradient = gradient
                                                selectedImageUri = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White.copy(alpha = 0.9f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Event Details Section ──
                item {
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SectionHeader(
                                icon = Icons.Default.Edit,
                                title = "Event Details",
                                subtitle = "Title and description for your event"
                            )

                            // Title Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Event Title",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CosmosOnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("e.g., Founders Meetup", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmosPrimary,
                                        unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.5f),
                                        focusedTextColor = CosmosOnBackground,
                                        unfocusedTextColor = CosmosOnBackground,
                                        cursorColor = CosmosPrimary,
                                        focusedContainerColor = CosmosSurfaceContainerLowest,
                                        unfocusedContainerColor = CosmosSurfaceContainerLowest
                                    ),
                                    singleLine = true
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "${title.length}/80",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (title.length > 80) CosmosError else CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            // Description Field with AI
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Description",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmosOnSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        CosmosPrimary.copy(alpha = 0.15f),
                                                        CosmosGradientEnd.copy(alpha = 0.1f)
                                                    )
                                                )
                                            )
                                            .clickable { showAiDialog = true }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "AI Generate",
                                            tint = CosmosPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "AI Generate",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CosmosPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("What is this event about?", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmosPrimary,
                                        unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.5f),
                                        focusedTextColor = CosmosOnBackground,
                                        unfocusedTextColor = CosmosOnBackground,
                                        cursorColor = CosmosPrimary,
                                        focusedContainerColor = CosmosSurfaceContainerLowest,
                                        unfocusedContainerColor = CosmosSurfaceContainerLowest
                                    ),
                                    singleLine = false,
                                    minLines = 3
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "${description.length}/500",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (description.length > 500) CosmosError else CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Schedule Section ──
                item {
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader(
                                icon = Icons.Default.Schedule,
                                title = "Schedule",
                                subtitle = "When does your event take place?"
                            )

                            // Quick Date Pick Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val quickDates = listOf("Today" to 0, "Tomorrow" to 1, "This Weekend" to -1, "Next Week" to 7)
                                quickDates.forEach { (label, daysOffset) ->
                                    val cal = java.util.Calendar.getInstance()
                                    val resultDate = if (daysOffset == -1) {
                                        while (cal.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.SATURDAY) {
                                            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                        }
                                        val format = SimpleDateFormat("MMM d, yyyy", Locale.US)
                                        format.format(cal.time)
                                    } else {
                                        cal.add(java.util.Calendar.DAY_OF_YEAR, daysOffset)
                                        val format = SimpleDateFormat("MMM d, yyyy", Locale.US)
                                        format.format(cal.time)
                                    }
                                    val isSelected = date == resultDate

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) CosmosPrimary.copy(alpha = 0.2f)
                                                else CosmosSurfaceContainerLowest
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) CosmosPrimary.copy(alpha = 0.6f) else CosmosOutlineVariant.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { date = resultDate }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) CosmosPrimary else CosmosOnBackground,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Date & Time Picker Row
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EventPickerField(
                                    label = "Date",
                                    value = date,
                                    placeholder = "Select Date",
                                    icon = Icons.Default.Event,
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.weight(1f)
                                )
                                EventPickerField(
                                    label = "Time",
                                    value = time,
                                    placeholder = "Select Time",
                                    icon = Icons.Default.Schedule,
                                    onClick = { showTimePicker = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Live Countdown Preview
                            AnimatedVisibility(
                                visible = date.isNotBlank() && time.isNotBlank(),
                                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                                exit = fadeOut(tween(200))
                            ) {
                                val countdownText = remember(date, time) {
                                    try {
                                        val format = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
                                        val eventDate = format.parse("$date $time")
                                        if (eventDate != null) {
                                            val diff = eventDate.time - System.currentTimeMillis()
                                            if (diff > 0) {
                                                val days = diff / (1000 * 60 * 60 * 24)
                                                val hours = (diff / (1000 * 60 * 60)) % 24
                                                when {
                                                    days > 0 -> "In $days day${if (days > 1) "s" else ""}, $hours hour${if (hours != 1L) "s" else ""}"
                                                    hours > 0 -> "In $hours hour${if (hours != 1L) "s" else ""}"
                                                    else -> "Starting soon!"
                                                }
                                            } else "Event time has passed"
                                        } else ""
                                    } catch (e: Exception) { "" }
                                }
                                if (countdownText.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CosmosPrimary.copy(alpha = 0.08f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = CosmosPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = countdownText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CosmosPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Location Section ──
                item {
                    CosmicLocationSection(
                        location = location,
                        onLocationChange = { location = it },
                        selectedPlaceId = selectedPlaceId,
                        onPlaceIdChange = { selectedPlaceId = it }
                    )
                }

                // ── Participants & Pricing Section ──
                item {
                    CosmosGlassCard(showTopGradientBorder = false) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SectionHeader(
                                icon = Icons.Default.Group,
                                title = "Capacity & Pricing",
                                subtitle = "Set attendee limits and ticket pricing"
                            )

                            // Cosmic Participant Stepper
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Max Participants",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CosmosOnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CosmosSurfaceContainerLowest)
                                            .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .clickable { if (maxParticipants > 5) maxParticipants -= 5 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Remove, "Decrease", tint = CosmosPrimary, modifier = Modifier.size(20.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CosmosSurfaceContainerLowest)
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.horizontalGradient(
                                                    listOf(CosmosPrimary.copy(alpha = 0.3f), CosmosGradientEnd.copy(alpha = 0.2f))
                                                ),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.People, null, tint = CosmosPrimary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                            Text(
                                                "$maxParticipants",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = CosmosOnBackground,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text("attendees", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant.copy(alpha = 0.6f))
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CosmosSurfaceContainerLowest)
                                            .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .clickable { if (maxParticipants < 10000) maxParticipants += 5 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, "Increase", tint = CosmosPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }

                                // Quick capacity chips
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(10, 25, 50, 100, 250, 500).forEach { cap ->
                                        val isSelected = maxParticipants == cap
                                        Text(
                                            text = "$cap",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) CosmosPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                                .border(1.dp, if (isSelected) CosmosPrimary.copy(alpha = 0.5f) else CosmosOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .clickable { maxParticipants = cap }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.15f))

                            // Paid Event Toggle
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Paid Event", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                                    Text("Charge attendees for entry", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant.copy(alpha = 0.6f))
                                }
                                Switch(
                                    checked = isPaid,
                                    onCheckedChange = { isPaid = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = CosmosPrimary,
                                        checkedTrackColor = CosmosPrimary.copy(alpha = 0.3f),
                                        uncheckedThumbColor = CosmosOnSurfaceVariant,
                                        uncheckedTrackColor = CosmosSurfaceContainerHigh
                                    )
                                )
                            }

                            // Price Section
                            AnimatedVisibility(
                                visible = isPaid,
                                enter = fadeIn(tween(250)) + expandVertically(tween(300)),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Currency Pills
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CosmosSurfaceContainerLowest)
                                            .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        listOf("USD", "INR", "EUR", "GBP").forEach { currency ->
                                            val isSelectedCurrency = selectedCurrency == currency
                                            Text(
                                                text = currency,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelectedCurrency) Color.White else CosmosOnSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelectedCurrency) Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd))
                                                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                                    )
                                                    .clickable { selectedCurrency = currency }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = price,
                                        onValueChange = { price = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("0.00", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                                        leadingIcon = {
                                            Text(
                                                text = when (selectedCurrency) { "USD" -> "$"; "INR" -> "\u20B9"; "EUR" -> "\u20AC"; "GBP" -> "\u00A3"; else -> "$" },
                                                style = MaterialTheme.typography.titleMedium,
                                                color = CosmosPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(start = 16.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmosPrimary,
                                            unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.5f),
                                            focusedTextColor = CosmosOnBackground,
                                            unfocusedTextColor = CosmosOnBackground,
                                            cursorColor = CosmosPrimary,
                                            focusedContainerColor = CosmosSurfaceContainerLowest,
                                            unfocusedContainerColor = CosmosSurfaceContainerLowest
                                        ),
                                        singleLine = true
                                    )

                                    // Quick Price Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val currencySymbol = when (selectedCurrency) { "USD" -> "$"; "INR" -> "\u20B9"; "EUR" -> "\u20AC"; "GBP" -> "\u00A3"; else -> "$" }
                                        listOf("10" to "10", "25" to "25", "50" to "50", "100" to "100").forEach { (label, priceVal) ->
                                            val isSelectedPrice = price == priceVal
                                            Text(
                                                text = "$currencySymbol$label",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelectedPrice) CosmosPrimary else CosmosOnSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelectedPrice) CosmosPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                                    .border(1.dp, if (isSelectedPrice) CosmosPrimary.copy(alpha = 0.5f) else CosmosOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                    .clickable { price = priceVal }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.15f))

                                    // ── Payment Collection Section ──
                                    Text(
                                        "CENTRALIZED PLATFORM CHECKOUT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmosPrimary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )

                                    // Razorpay Gateway Assurance Card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CosmosPrimary.copy(alpha = 0.08f))
                                            .border(1.dp, CosmosPrimary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Lock, null, tint = CosmosPrimary, modifier = Modifier.size(18.dp))
                                                Text(
                                                    "Razorpay Centralized Gateway Active",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = CosmosStarWhite,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                "Ticket revenue is processed centrally via Cosmos's official Razorpay account. Attendees can pay via any UPI app (GPay, PhonePe, Paytm), Cards, or NetBanking.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = CosmosOnSurfaceVariant
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, null, tint = CosmosSuccess, modifier = Modifier.size(14.dp))
                                                Text(
                                                    "Instant digital pass & QR code issued automatically upon payment.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CosmosSuccess,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // Payment Instructions / Guidelines (optional)
                                    OutlinedTextField(
                                        value = paymentInstructions,
                                        onValueChange = { paymentInstructions = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Attendee Guidelines / Notes (optional)") },
                                        placeholder = { Text("e.g. Please bring your laptop and ID", color = CosmosOnSurfaceVariant.copy(alpha = 0.4f)) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CosmosPrimary,
                                            unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.5f),
                                            focusedTextColor = CosmosOnBackground,
                                            unfocusedTextColor = CosmosOnBackground,
                                            cursorColor = CosmosPrimary,
                                            focusedLabelColor = CosmosPrimary,
                                            unfocusedLabelColor = CosmosOnSurfaceVariant,
                                            focusedContainerColor = CosmosSurfaceContainerLowest,
                                            unfocusedContainerColor = CosmosSurfaceContainerLowest
                                        ),
                                        singleLine = false,
                                        minLines = 2
                                    )

                                    // ── Participant Preview Card ──
                                    val priceFilled = price.isNotBlank()
                                    if (priceFilled) {
                                        val previewCurrencySymbol = when (selectedCurrency) { "USD" -> "$"; "INR" -> "₹"; "EUR" -> "€"; "GBP" -> "£"; else -> "$" }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            CosmosGradientStart.copy(alpha = 0.10f),
                                                            CosmosGradientEnd.copy(alpha = 0.05f)
                                                        )
                                                    )
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.horizontalGradient(
                                                        listOf(CosmosPrimary.copy(alpha = 0.3f), CosmosGradientEnd.copy(alpha = 0.15f))
                                                    ),
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .padding(14.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Visibility, null, tint = CosmosPrimary.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                                    Text(
                                                        "ATTENDEE CHECKOUT PREVIEW",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = CosmosPrimary.copy(alpha = 0.7f),
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.8.sp
                                                    )
                                                }
                                                Text(
                                                    "1x Pass: $previewCurrencySymbol$price",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = CosmosOnBackground,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("💳", fontSize = 14.sp)
                                                    Text(
                                                        "Secured Razorpay Checkout (UPI, Cards, NetBanking)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = CosmosPrimary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                if (paymentInstructions.isNotBlank()) {
                                                    Text(
                                                        "“$paymentInstructions”",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = CosmosOnSurfaceVariant.copy(alpha = 0.7f),
                                                        fontWeight = FontWeight.Light
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

                // ── Launch Summary & Submit ──
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Ready-to-Launch Summary Card
                        AnimatedVisibility(
                            visible = title.isNotBlank() && date.isNotBlank() && time.isNotBlank(),
                            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                            exit = fadeOut(tween(200))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(CosmosGradientStart.copy(alpha = 0.12f), CosmosGradientEnd.copy(alpha = 0.06f))
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.horizontalGradient(
                                            listOf(CosmosPrimary.copy(alpha = 0.4f), CosmosGradientEnd.copy(alpha = 0.2f), CosmosPrimary.copy(alpha = 0.4f))
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.RocketLaunch, null, tint = CosmosPrimary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (isEditing) "READY TO UPDATE" else "READY TO LAUNCH",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CosmosPrimary,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CosmosOnBackground,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                             Icon(Icons.Default.Event, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                             Text(date, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                             Icon(Icons.Default.Schedule, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                             Text(time, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                             Icon(Icons.Default.People, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                             Text("$maxParticipants", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                        }
                                    }

                                    if (location.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                             Icon(Icons.Default.LocationOn, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(14.dp))
                                             Text(location, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        CosmosButton(
                            text = if (isCreating || isSavingChanges) "Saving..." else if (isEditing) "Save Changes" else "Launch Event \uD83D\uDE80",
                            onClick = {
                                val imageBytes = selectedImageUri?.let { uri ->
                                    cropEventBitmap(context, uri, imageZoom, imagePanFraction)
                                }
                                val currencySymbol = when (selectedCurrency) { "USD" -> "$"; "INR" -> "\u20B9"; "EUR" -> "\u20AC"; "GBP" -> "\u00A3"; else -> "$" }
                                val parsedPrice = price.toDoubleOrNull() ?: 0.0
                                
                                if (isEditing) {
                                    val targetEventId = eventId ?: return@CosmosButton
                                    isSavingChanges = true
                                    val updates = mutableMapOf<String, Any>(
                                        "title" to title,
                                        "description" to description,
                                        "date" to date,
                                        "time" to time,
                                        "location" to location,
                                        "maxParticipants" to maxParticipants,
                                        "isPaid" to isPaid,
                                        "price" to (if (isPaid) "$currencySymbol$price" else ""),
                                        "currency" to selectedCurrency,
                                        "priceAmount" to (if (isPaid) parsedPrice else 0.0),
                                        "paymentUpiId" to (if (isPaid) upiId.trim() else ""),
                                        "paymentAccountName" to (if (isPaid) accountName.trim() else ""),
                                        "paymentInstructions" to (if (isPaid) paymentInstructions.trim() else "")
                                    )

                                    scope.launch {
                                        try {
                                            if (selectedImageUri != null && imageBytes != null) {
                                                val tempId = java.util.UUID.randomUUID().toString()
                                                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("events/$tempId.jpg")
                                                storageRef.putBytes(imageBytes).await()
                                                val downloadUrl = storageRef.downloadUrl.await().toString()
                                                updates["coverUrl"] = downloadUrl
                                            } else if (isExistingCoverRemoved || activeEvent?.coverUrl?.startsWith("gradient:") == true || activeEvent?.coverUrl.isNullOrBlank()) {
                                                updates["coverUrl"] = selectedGradient.id
                                            }

                                            eventViewModel.updateEvent(
                                                eventId = targetEventId,
                                                updates = updates,
                                                onSuccess = {
                                                    isSavingChanges = false
                                                    Toast.makeText(context, "Event updated successfully ✏️", Toast.LENGTH_SHORT).show()
                                                    onEventPosted()
                                                },
                                                onError = { errorMsg ->
                                                    isSavingChanges = false
                                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        } catch (e: Exception) {
                                            isSavingChanges = false
                                            Toast.makeText(context, e.message ?: "Failed to upload cover image", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    val isVirtual = location.startsWith("https://meet.google.com") || location.contains("meet.google", ignoreCase = true)
                                    val event = app.cosmos.com.data.model.NetworkEvent(
                                        id = "",
                                        title = title,
                                        description = description,
                                        date = date,
                                        time = time,
                                        location = location,
                                        type = app.cosmos.com.data.model.EventType.OPEN_NETWORKING,
                                        participantCount = 0,
                                        maxParticipants = maxParticipants,
                                        isPaid = isPaid,
                                        price = if (isPaid) "$currencySymbol$price" else "",
                                        currency = selectedCurrency,
                                        priceAmount = if (isPaid) parsedPrice else 0.0,
                                        paymentUpiId = if (isPaid) upiId.trim() else "",
                                        paymentAccountName = if (isPaid) accountName.trim() else "",
                                        paymentInstructions = if (isPaid) paymentInstructions.trim() else "",
                                        coverUrl = if (selectedImageUri != null) "" else selectedGradient.id,
                                        tags = listOf("Networking"),
                                        createdBy = "",
                                        createdAt = 0L,
                                        isVirtual = isVirtual
                                    )
                                    eventViewModel.createEventWithImage(
                                        event = event,
                                        imageBytes = imageBytes,
                                        onSuccess = onEventPosted,
                                        onError = { errorMsg ->
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            },
                            enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank() && !isCreating && !isSavingChanges,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // ── Date Picker Dialog ──
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                            utcCal.timeInMillis = millis
                            val localCal = java.util.Calendar.getInstance()
                            localCal.set(utcCal.get(java.util.Calendar.YEAR), utcCal.get(java.util.Calendar.MONTH), utcCal.get(java.util.Calendar.DAY_OF_MONTH))
                            val format = SimpleDateFormat("MMM d, yyyy", Locale.US)
                            date = format.format(localCal.time)
                        }
                        showDatePicker = false
                    }
                ) { Text("Select", color = CosmosPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = CosmosOnSurfaceVariant) }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color(0xFF16191F),
                titleContentColor = CosmosOnBackground,
                headlineContentColor = CosmosOnBackground,
                weekdayContentColor = CosmosOnSurfaceVariant,
                subheadContentColor = CosmosOnSurfaceVariant,
                navigationContentColor = CosmosOnBackground,
                yearContentColor = CosmosOnSurfaceVariant,
                selectedYearContentColor = Color.White,
                selectedYearContainerColor = CosmosPrimaryContainer,
                dayContentColor = CosmosOnBackground,
                selectedDayContentColor = Color.White,
                selectedDayContainerColor = CosmosGradientStart,
                todayContentColor = CosmosPrimary,
                todayDateBorderColor = CosmosPrimary
            ),
            shape = RoundedCornerShape(24.dp)
        ) { DatePicker(state = datePickerState) }
    }

    // ── Time Picker Dialog ──
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                        val format = SimpleDateFormat("h:mm a", Locale.US)
                        time = format.format(cal.time)
                        showTimePicker = false
                    }
                ) { Text("Select", color = CosmosPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = CosmosOnSurfaceVariant) }
            },
            title = {
                Text("Select Time", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = CosmosSurfaceContainerHigh,
                            clockDialSelectedContentColor = Color.White,
                            clockDialUnselectedContentColor = CosmosOnSurfaceVariant,
                            selectorColor = CosmosPrimary,
                            periodSelectorBorderColor = CosmosOutlineVariant,
                            periodSelectorSelectedContainerColor = CosmosPrimaryContainer,
                            periodSelectorUnselectedContainerColor = CosmosSurfaceContainerLow,
                            periodSelectorSelectedContentColor = Color.White,
                            periodSelectorUnselectedContentColor = CosmosOnSurfaceVariant,
                            timeSelectorSelectedContainerColor = CosmosPrimaryContainer,
                            timeSelectorUnselectedContainerColor = CosmosSurfaceContainerLow,
                            timeSelectorSelectedContentColor = Color.White,
                            timeSelectorUnselectedContentColor = CosmosOnSurfaceVariant
                        )
                    )
                }
            },
            containerColor = Color(0xFF16191F),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── AI Description Dialog ──
    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { if (!isGeneratingDescription) showAiDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = CosmosPrimary)
                    Text("AI Description Generator", color = CosmosOnBackground, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Specify key topics, target audience, or keywords to guide the AI, or leave blank to generate based on Title & Location.",
                        style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aiPromptInput,
                        onValueChange = { aiPromptInput = it },
                        placeholder = { Text("e.g. networking, startup pitch tips, VC speakers", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmosPrimary, unfocusedBorderColor = CosmosOutlineVariant,
                            focusedTextColor = CosmosOnBackground, unfocusedTextColor = CosmosOnBackground,
                            cursorColor = CosmosPrimary,
                            focusedContainerColor = CosmosSurfaceContainerLow, unfocusedContainerColor = CosmosSurfaceContainerLow
                        ),
                        singleLine = false, minLines = 2
                    )
                    if (isGeneratingDescription) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CosmosPrimary, strokeWidth = 2.dp)
                            Text("Drafting description with AI...", style = MaterialTheme.typography.bodySmall, color = CosmosPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isGeneratingDescription = true
                        eventViewModel.generateEventDescription(
                            title = title.ifBlank { "Exclusive Meetup" },
                            location = location.ifBlank { "Virtual" },
                            details = aiPromptInput,
                            onSuccess = { generatedText -> description = generatedText; isGeneratingDescription = false; showAiDialog = false },
                            onError = { errorMsg -> isGeneratingDescription = false; Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show() }
                        )
                    },
                    enabled = !isGeneratingDescription,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
                ) { Text("Generate", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAiDialog = false }, enabled = !isGeneratingDescription) { Text("Cancel", color = CosmosOnSurfaceVariant) }
            },
            containerColor = Color(0xFF16191F),
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// ── Helper Composables ──

@Composable
private fun StepIndicator(
    step: Int,
    label: String,
    icon: ImageVector,
    isCompleted: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    when {
                        isCompleted -> Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd))
                        isActive -> Brush.horizontalGradient(listOf(CosmosPrimary.copy(alpha = 0.4f), CosmosPrimary.copy(alpha = 0.2f)))
                        else -> Brush.horizontalGradient(listOf(CosmosOutlineVariant.copy(alpha = 0.3f), CosmosOutlineVariant.copy(alpha = 0.2f)))
                    }
                )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isCompleted) {
                Icon(Icons.Default.CheckCircle, null, tint = CosmosSuccess, modifier = Modifier.size(12.dp))
            } else {
                Icon(icon, null, tint = if (isActive) CosmosPrimary else CosmosOnSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isCompleted -> CosmosSuccess
                    isActive -> CosmosPrimary
                    else -> CosmosOnSurfaceVariant.copy(alpha = 0.4f)
                },
                fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CosmosPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = CosmosPrimary, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun EventPickerField(
    label: String,
    value: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = CosmosOnSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CosmosSurfaceContainerLowest)
                .border(
                    1.dp,
                    if (value.isNotBlank()) CosmosPrimary.copy(alpha = 0.4f) else CosmosOutlineVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = CosmosOnSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(value, color = CosmosOnBackground, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Icon(icon, null, tint = CosmosPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    headerAction: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            if (headerAction != null) { headerAction() }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CosmosPrimary, unfocusedBorderColor = CosmosOutlineVariant,
                focusedTextColor = CosmosOnBackground, unfocusedTextColor = CosmosOnBackground,
                cursorColor = CosmosPrimary,
                focusedContainerColor = CosmosSurfaceContainerLow, unfocusedContainerColor = CosmosSurfaceContainerLow
            ),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3
        )
    }
}
