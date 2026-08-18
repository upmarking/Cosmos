package app.cosmos.com.screens.events

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.EventViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEventScreen(
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
    var locationSuggestions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(location) {
        if (location.length > 2 && !location.contains("zoom", ignoreCase = true) && !location.contains("meet.google", ignoreCase = true) && !location.contains("http", ignoreCase = true)) {
            kotlinx.coroutines.delay(500)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val encoded = java.net.URLEncoder.encode(location, "UTF-8")
                    val url = java.net.URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=5&email=contact@cosmos.app")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "CosmosApp/1.0 (contact@cosmos.app)")
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(response)
                    val suggestions = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        suggestions.add(jsonArray.getJSONObject(i).getString("display_name"))
                    }
                    locationSuggestions = suggestions
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        } else {
            locationSuggestions = emptyList()
        }
    }
    var maxParticipants by remember { mutableStateOf("50") }
    var isPaid by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    
    val isCreating by eventViewModel.isCreatingEvent.collectAsState()

    var showAiDialog by remember { mutableStateOf(false) }
    var aiPromptInput by remember { mutableStateOf("") }
    var isGeneratingDescription by remember { mutableStateOf(false) }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = "Post Event",
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "Event Cover Image",
                            style = MaterialTheme.typography.labelMedium,
                            color = CosmosOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        if (selectedImageUri != null) {
                            Column {
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
                                        model = selectedImageUri,
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
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
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
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                CosmosGlassCard(showTopGradientBorder = false) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Zoom",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CosmosOnSurfaceVariant
                                            )
                                            Text(
                                                "${(imageZoom * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
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
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Reposition (Vertical Offset)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CosmosOnSurfaceVariant
                                            )
                                            Text(
                                                "${(imagePanFraction * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
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
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CosmosSurfaceContainerLow)
                                    .border(
                                        width = 1.dp,
                                        color = CosmosOutlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = CosmosPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Add Event Cover Image",
                                        color = CosmosOnSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            text = "Or Select Default Cover Theme",
                            style = MaterialTheme.typography.labelMedium,
                            color = CosmosOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
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
                                        }
                                )
                            }
                        }
                    }
                }

                item {
                    EventTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Event Title",
                        placeholder = "e.g., Founders Meetup",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    EventTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description",
                        placeholder = "What is this event about?",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        headerAction = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmosPrimary.copy(alpha = 0.15f))
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
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                }
                item {
                    EventTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = "Location",
                        placeholder = "e.g., San Francisco, CA or Zoom",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (locationSuggestions.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CosmosOutlineVariant, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CosmosSurfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                locationSuggestions.forEach { suggestion ->
                                    Text(
                                        text = suggestion,
                                        color = CosmosOnBackground,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                location = suggestion
                                                locationSuggestions = emptyList()
                                            }
                                            .padding(8.dp)
                                    )
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        color = CosmosOutlineVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
                if (location.isNotBlank() && !location.contains("zoom", ignoreCase = true) && !location.contains("meet.google", ignoreCase = true) && !location.contains("http", ignoreCase = true)) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, CosmosOutlineVariant, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CosmosSurfaceContainerLow)
                        ) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { context ->
                                    android.webkit.WebView(context).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        webViewClient = android.webkit.WebViewClient()
                                        settings.javaScriptEnabled = true
                                    }
                                },
                                update = { webView ->
                                    try {
                                        val encodedLoc = java.net.URLEncoder.encode(location, "UTF-8")
                                        val mapUrl = "https://maps.google.com/maps?q=$encodedLoc&t=&z=13&ie=UTF8&iwloc=&output=embed"
                                        webView.loadUrl(mapUrl)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                item {
                    EventTextField(
                        value = maxParticipants,
                        onValueChange = { maxParticipants = it.filter { char -> char.isDigit() } },
                        label = "Max Participants",
                        placeholder = "e.g., 50",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Paid Event", color = CosmosOnBackground, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isPaid,
                            onCheckedChange = { isPaid = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CosmosPrimary, checkedTrackColor = CosmosPrimary.copy(alpha = 0.5f))
                        )
                    }
                }
                if (isPaid) {
                    item {
                        EventTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = "Price",
                            placeholder = "e.g., $25",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    CosmosButton(
                        text = if (isCreating) "Posting..." else "Post Event",
                        onClick = {
                            val imageBytes = selectedImageUri?.let { uri ->
                                cropEventBitmap(context, uri, imageZoom, imagePanFraction)
                            }
                            val event = app.cosmos.com.data.model.NetworkEvent(
                                id = "",
                                title = title,
                                description = description,
                                date = date,
                                time = time,
                                location = location,
                                type = app.cosmos.com.data.model.EventType.OPEN_NETWORKING,
                                participantCount = 0,
                                maxParticipants = maxParticipants.toIntOrNull() ?: 50,
                                isPaid = isPaid,
                                price = price,
                                coverUrl = if (selectedImageUri != null) "" else selectedGradient.id,
                                tags = listOf("Networking"),
                                createdBy = "",
                                createdAt = 0L
                            )
                            eventViewModel.createEventWithImage(
                                event = event,
                                imageBytes = imageBytes,
                                onSuccess = onEventPosted,
                                onError = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        enabled = title.isNotBlank() && date.isNotBlank() && time.isNotBlank() && !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

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
                ) {
                    Text("Select", color = CosmosPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
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
        ) {
            DatePicker(state = datePickerState)
        }
    }

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
                ) {
                    Text("Select", color = CosmosPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            title = {
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { if (!isGeneratingDescription) showAiDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CosmosPrimary
                    )
                    Text(
                        text = "AI Description Generator",
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Specify key topics, target audience, or keywords to guide the AI, or leave blank to generate based on Title & Location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aiPromptInput,
                        onValueChange = { aiPromptInput = it },
                        placeholder = { Text("e.g. networking, startup pitch tips, VC speakers", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
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
                        singleLine = false,
                        minLines = 2
                    )
                    if (isGeneratingDescription) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CosmosPrimary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Drafting description with AI...",
                                style = MaterialTheme.typography.bodySmall,
                                color = CosmosPrimary
                            )
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
                            onSuccess = { generatedText ->
                                description = generatedText
                                isGeneratingDescription = false
                                showAiDialog = false
                            },
                            onError = { errorMsg ->
                                isGeneratingDescription = false
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isGeneratingDescription,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
                ) {
                    Text("Generate", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAiDialog = false },
                    enabled = !isGeneratingDescription
                ) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            containerColor = Color(0xFF16191F),
            shape = RoundedCornerShape(24.dp)
        )
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
            style = MaterialTheme.typography.labelMedium,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CosmosSurfaceContainerLow)
                .border(1.dp, CosmosOutlineVariant, RoundedCornerShape(12.dp))
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
                    Text(
                        text = placeholder,
                        color = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = value,
                        color = CosmosOnBackground,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CosmosPrimary,
                    modifier = Modifier.size(20.dp)
                )
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
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = CosmosOnSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            if (headerAction != null) {
                headerAction()
            }
        }
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
            minLines = if (singleLine) 1 else 3
        )
    }
}
