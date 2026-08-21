package app.cosmos.com.screens.events

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.cosmos.com.BuildConfig
import app.cosmos.com.ui.components.CosmosButton
import app.cosmos.com.ui.components.CosmosGlassCard
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class EventLocationMode {
    IN_PERSON,
    VIRTUAL
}

data class CosmicPlaceSuggestion(
    val description: String,
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val isVerified: Boolean = true
)

data class CosmicHotspot(
    val name: String,
    val city: String,
    val icon: String
)

val POPULAR_COSMIC_HOTSPOTS = listOf(
    CosmicHotspot("San Francisco", "San Francisco, CA, USA", "🌉"),
    CosmicHotspot("Silicon Valley", "Palo Alto, CA, USA", "⚡"),
    CosmicHotspot("New York", "New York, NY, USA", "🗽"),
    CosmicHotspot("Bengaluru", "Bengaluru, Karnataka, India", "🚀"),
    CosmicHotspot("London", "London, UK", "🏛️"),
    CosmicHotspot("Singapore", "Singapore", "🦁"),
    CosmicHotspot("Austin", "Austin, TX, USA", "🤠"),
    CosmicHotspot("Tokyo", "Tokyo, Japan", "🗼"),
    CosmicHotspot("Berlin", "Berlin, Germany", "🎧"),
    CosmicHotspot("Dubai", "Dubai, UAE", "✨")
)

data class VirtualPlatform(
    val name: String,
    val tagline: String,
    val defaultUrl: String,
    val brandColor: Color,
    val icon: ImageVector,
    val urlPrefix: String
)

val VIRTUAL_PLATFORMS = listOf(
    VirtualPlatform(
        name = "Google Meet",
        tagline = "Secure HD Video",
        defaultUrl = "https://meet.google.com/",
        brandColor = Color(0xFF00897B),
        icon = Icons.Default.Videocam,
        urlPrefix = "meet.google.com"
    )
)

data class VirtualAtmosphere(
    val label: String,
    val emoji: String,
    val description: String
)

val VIRTUAL_ATMOSPHERES = listOf(
    VirtualAtmosphere("Boardroom", "🏛️", "Formal & Professional"),
    VirtualAtmosphere("Fireside Chat", "🔥", "Casual & Intimate"),
    VirtualAtmosphere("Workshop", "⚡", "Hands-on & Collaborative"),
    VirtualAtmosphere("Town Hall", "🌐", "Open & Large-scale")
)

// Keep old presets for backward compat
val VIRTUAL_PRESETS = listOf(
    "Google Meet" to "https://meet.google.com/new",
    "Zoom Video" to "https://zoom.us/join",
    "Discord Stage" to "https://discord.gg/",
    "X Space" to "https://x.com/i/spaces/"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmicLocationSection(
    location: String,
    onLocationChange: (String) -> Unit,
    selectedPlaceId: String,
    onPlaceIdChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var locationMode by remember {
        mutableStateOf(
            if (location.contains("meet.google", ignoreCase = true) ||
                location.contains("zoom", ignoreCase = true) ||
                location.contains("http", ignoreCase = true) ||
                location.contains("discord", ignoreCase = true)
            ) EventLocationMode.VIRTUAL else EventLocationMode.IN_PERSON
        )
    }

    var suggestions by remember { mutableStateOf(emptyList<CosmicPlaceSuggestion>()) }
    var isSearching by remember { mutableStateOf(false) }
    var isSuggestionSelected by remember { mutableStateOf(location.isNotBlank()) }
    var showFullscreenMap by remember { mutableStateOf(false) }
    val apiKey = BuildConfig.MAPS_API_KEY

    // Query Autocomplete
    LaunchedEffect(location, locationMode) {
        if (locationMode == EventLocationMode.VIRTUAL || isSuggestionSelected) {
            suggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }

        if (location.trim().length >= 2) {
            isSearching = true
            delay(400) // Debounce
            try {
                withContext(Dispatchers.IO) {
                    val encoded = URLEncoder.encode(location.trim(), "UTF-8")
                    if (apiKey.isNotBlank() && apiKey != "YOUR_API_KEY") {
                        val url = URL("https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$encoded&key=$apiKey")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        val status = json.optString("status", "")
                        if (status == "OK" || status == "ZERO_RESULTS") {
                            val preds = json.optJSONArray("predictions")
                            val results = mutableListOf<CosmicPlaceSuggestion>()
                            if (preds != null) {
                                for (i in 0 until preds.length()) {
                                    val item = preds.getJSONObject(i)
                                    val desc = item.getString("description")
                                    val pid = item.getString("place_id")
                                    val struct = item.optJSONObject("structured_formatting")
                                    val primary = struct?.optString("main_text") ?: desc.substringBefore(",")
                                    val secondary = struct?.optString("secondary_text") ?: desc.substringAfter(",", "")
                                    results.add(
                                        CosmicPlaceSuggestion(
                                            description = desc,
                                            placeId = pid,
                                            primaryText = primary.trim(),
                                            secondaryText = secondary.trim(),
                                            isVerified = true
                                        )
                                    )
                                }
                            }
                            suggestions = results
                        }
                    } else {
                        // OpenStreetMap Nominatim Fallback
                        val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=5&email=contact@cosmos.app")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", "CosmosApp/1.0 (contact@cosmos.app)")
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val jsonArray = JSONArray(response)
                        val results = mutableListOf<CosmicPlaceSuggestion>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val name = obj.getString("display_name")
                            val parts = name.split(",")
                            val primary = parts.firstOrNull() ?: name
                            val secondary = parts.drop(1).joinToString(",")
                            results.add(
                                CosmicPlaceSuggestion(
                                    description = name,
                                    placeId = "",
                                    primaryText = primary.trim(),
                                    secondaryText = secondary.trim(),
                                    isVerified = false
                                )
                            )
                        }
                        suggestions = results
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearching = false
            }
        } else {
            suggestions = emptyList()
            isSearching = false
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TravelExplore,
                contentDescription = null,
                tint = CosmosPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Event Location",
                style = MaterialTheme.typography.labelMedium,
                color = CosmosOnSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Full-width Mode Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CosmosSurfaceContainerHigh)
                .border(1.dp, CosmosGlassBorder, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LocationModeTab(
                title = "In-Person",
                icon = Icons.Default.LocationOn,
                isSelected = locationMode == EventLocationMode.IN_PERSON,
                onClick = {
                    locationMode = EventLocationMode.IN_PERSON
                    if (location.contains("http", ignoreCase = true) || location.contains("meet", ignoreCase = true)) {
                        onLocationChange("")
                        onPlaceIdChange("")
                        isSuggestionSelected = false
                    }
                },
                modifier = Modifier.weight(1f)
            )
            LocationModeTab(
                title = "Virtual",
                icon = Icons.Default.Language,
                isSelected = locationMode == EventLocationMode.VIRTUAL,
                onClick = {
                    locationMode = EventLocationMode.VIRTUAL
                    if (location.isBlank() || !location.contains("http")) {
                        onLocationChange("https://meet.google.com/")
                        onPlaceIdChange("")
                        isSuggestionSelected = true
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Animated Body Depending on Mode
        AnimatedContent(
            targetState = locationMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "LocationModeAnim"
        ) { mode ->
            if (mode == EventLocationMode.IN_PERSON) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Cosmic Search Input Field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CosmosSurfaceContainerLow)
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        CosmosPrimary.copy(alpha = if (location.isNotBlank()) 0.6f else 0.2f),
                                        CosmosGradientEnd.copy(alpha = if (location.isNotBlank()) 0.6f else 0.2f)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = {
                                onLocationChange(it)
                                onPlaceIdChange("")
                                isSuggestionSelected = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "Search venue, address, or tech hub...",
                                    color = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = CosmosPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = if (location.isNotBlank()) CosmosPrimary else CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    if (location.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                onLocationChange("")
                                                onPlaceIdChange("")
                                                isSuggestionSelected = false
                                                suggestions = emptyList()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = CosmosOnSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground,
                                cursorColor = CosmosPrimary,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }

                    // Popular Tech Hub Hotspots Horizontal Row
                    if (!isSuggestionSelected && location.isBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "GLOBAL TECH HUBS",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosPrimary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                POPULAR_COSMIC_HOTSPOTS.forEach { hotspot ->
                                    HotspotChip(
                                        hotspot = hotspot,
                                        onClick = {
                                            onLocationChange(hotspot.city)
                                            isSuggestionSelected = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Google Places Autocomplete Suggestions Dropdown
                    AnimatedVisibility(
                        visible = suggestions.isNotEmpty() && !isSuggestionSelected,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        CosmosGlassCard(
                            showTopGradientBorder = true,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "GOOGLE PLACES PREDICTIONS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmosPrimary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Verified Coordinates",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmosOnSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                suggestions.forEachIndexed { index, suggestion ->
                                    PlaceSuggestionRow(
                                        suggestion = suggestion,
                                        onClick = {
                                            onLocationChange(suggestion.description)
                                            onPlaceIdChange(suggestion.placeId)
                                            isSuggestionSelected = true
                                            suggestions = emptyList()
                                        }
                                    )
                                    if (index < suggestions.lastIndex) {
                                        HorizontalDivider(
                                            color = CosmosOutlineVariant.copy(alpha = 0.25f),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Cinematic Dark Cosmic Google Map View
                    if (location.isNotBlank() && isSuggestionSelected) {
                        CosmicDarkMapDisplay(
                            location = location,
                            placeId = selectedPlaceId,
                            apiKey = apiKey,
                            onExpandClick = { showFullscreenMap = true },
                            onChangeClick = {
                                isSuggestionSelected = false
                            },
                            onOpenExternalMaps = {
                                launchExternalGoogleMaps(context, location, selectedPlaceId)
                            }
                        )
                    }
                }
            } else {
                // Virtual Meeting Link Mode
                VirtualMeetingSection(
                    url = location,
                    onUrlChange = onLocationChange
                )
            }
        }
    }

    // Fullscreen Cinematic Interactive Map Modal
    if (showFullscreenMap && location.isNotBlank()) {
        CosmicMapFullscreenDialog(
            location = location,
            placeId = selectedPlaceId,
            apiKey = apiKey,
            onDismiss = { showFullscreenMap = false },
            onOpenExternal = {
                launchExternalGoogleMaps(context, location, selectedPlaceId)
            }
        )
    }
}

@Composable
private fun LocationModeTab(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgModifier = if (isSelected) {
        Modifier.background(
            brush = Brush.horizontalGradient(
                listOf(CosmosGradientStart, CosmosGradientEnd)
            ),
            shape = RoundedCornerShape(12.dp)
        )
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) Color.White else CosmosOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) Color.White else CosmosOnSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun HotspotChip(
    hotspot: CosmicHotspot,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CosmosSurfaceContainerLow)
            .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(hotspot.icon, fontSize = 14.sp)
        Text(
            text = hotspot.name,
            style = MaterialTheme.typography.bodySmall,
            color = CosmosOnBackground,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PlaceSuggestionRow(
    suggestion: CosmicPlaceSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CosmosPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = CosmosPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.primaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = CosmosOnBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.secondaryText.isNotBlank()) {
                Text(
                    text = suggestion.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Select",
            tint = CosmosPrimary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun CosmicDarkMapDisplay(
    location: String,
    placeId: String,
    apiKey: String,
    onExpandClick: () -> Unit,
    onChangeClick: () -> Unit,
    onOpenExternalMaps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CosmosSurfaceContainerLowest)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        CosmosPrimary.copy(alpha = 0.8f),
                        CosmosGradientEnd.copy(alpha = 0.4f),
                        CosmosPrimary.copy(alpha = 0.8f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column {
            // Map Frame (Height 210dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(Color(0xFF0A0C10))
            ) {
                if (apiKey.isBlank() || apiKey == "YOUR_API_KEY") {
                    // API Key Missing Warning State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "No API Key",
                            tint = CosmosPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Google Maps Key Required",
                            style = MaterialTheme.typography.titleSmall,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add MAPS_API_KEY to local.properties to unlock dark cosmic maps rendering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    // Native WebView with Dark Space CSS Filter
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                setBackgroundColor(0xFF0C0E12.toInt())
                            }
                        },
                        update = { webView ->
                            try {
                                val html = buildCosmicMapHtml(apiKey, placeId, location)
                                webView.loadDataWithBaseURL("https://maps.google.com", html, "text/html", "UTF-8", null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Sci-Fi Cosmic Overlay HUD
                    // Top Left: Coordinates HUD
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, CosmosGlassBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CosmosSuccess)
                        )
                        Text(
                            text = "GEO-LOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    // Top Right: Expand & External Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        IconButton(
                            onClick = onOpenExternalMaps,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(1.dp, CosmosGlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in Google Maps",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onExpandClick,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(1.dp, CosmosGlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Center Radar Reticle Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulsing outer radar circle
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .border(1.5.dp, CosmosPrimary.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }

            // Venue Info Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmosSurfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CosmosPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = null,
                            tint = CosmosPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = location.substringBefore(","),
                            style = MaterialTheme.typography.titleSmall,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (location.contains(",")) {
                            Text(
                                text = location.substringAfter(",").trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = CosmosOnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onChangeClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Change",
                        color = CosmosPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualMeetingSection(
    url: String,
    onUrlChange: (String) -> Unit
) {
    var selectedPlatform by remember {
        mutableStateOf(
            VIRTUAL_PLATFORMS.firstOrNull { url.contains(it.urlPrefix, ignoreCase = true) }
        )
    }
    var selectedAtmosphere by remember { mutableStateOf<VirtualAtmosphere?>(null) }
    val detectedPlatform = remember(url) {
        VIRTUAL_PLATFORMS.firstOrNull { url.contains(it.urlPrefix, ignoreCase = true) }
    }
    val isValidUrl = remember(url) { url.startsWith("http://") || url.startsWith("https://") }

    val infiniteTransition = rememberInfiniteTransition(label = "VirtualPulse")
    val signalPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SignalPulse"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Section: Platform ──
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "PLATFORM",
                style = MaterialTheme.typography.labelSmall,
                color = CosmosPrimary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            // Single Google Meet Card — full width
            val platform = VIRTUAL_PLATFORMS.first()
            val isSelected = selectedPlatform == platform
            VirtualPlatformCard(
                platform = platform,
                isSelected = isSelected,
                onClick = {
                    selectedPlatform = platform
                    onUrlChange(platform.defaultUrl)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Subtle section divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            CosmosOutlineVariant.copy(alpha = 0.2f),
                            CosmosOutlineVariant.copy(alpha = 0.3f),
                            CosmosOutlineVariant.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Section: Meeting Link Input ──
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "MEETING LINK",
                style = MaterialTheme.typography.labelSmall,
                color = CosmosPrimary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            // Smart URL Input with Glass Wrapper
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CosmosSurfaceContainerLow)
                    .border(
                        width = 1.dp,
                        brush = if (isValidUrl) {
                            Brush.horizontalGradient(
                                listOf(
                                    (detectedPlatform?.brandColor ?: CosmosPrimary).copy(alpha = 0.6f),
                                    CosmosPrimary.copy(alpha = 0.3f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    CosmosOutlineVariant.copy(alpha = 0.4f),
                                    CosmosOutlineVariant.copy(alpha = 0.2f)
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        onUrlChange(it)
                        val detected = VIRTUAL_PLATFORMS.firstOrNull { p -> it.contains(p.urlPrefix, ignoreCase = true) }
                        if (detected != null) selectedPlatform = detected
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Paste your meeting link here...",
                            color = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    (detectedPlatform?.brandColor ?: CosmosPrimary).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = detectedPlatform?.icon ?: Icons.Default.Link,
                                contentDescription = null,
                                tint = detectedPlatform?.brandColor ?: CosmosPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        if (isValidUrl) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                if (detectedPlatform != null) {
                                    Text(
                                        text = detectedPlatform.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = detectedPlatform.brandColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Valid Link",
                                    tint = CosmosSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (url.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Invalid",
                                tint = CosmosError.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = CosmosOnBackground,
                        unfocusedTextColor = CosmosOnBackground,
                        cursorColor = CosmosPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }
        }

        // ── Virtual Venue Confirmation Card ──
        AnimatedVisibility(
            visible = isValidUrl && url.length > 10,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CosmosSurfaceContainerLowest)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                (detectedPlatform?.brandColor ?: CosmosPrimary).copy(alpha = 0.6f),
                                CosmosPrimary.copy(alpha = 0.3f),
                                (detectedPlatform?.brandColor ?: CosmosPrimary).copy(alpha = 0.6f)
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header with SIGNAL ACTIVE badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                (detectedPlatform?.brandColor ?: CosmosPrimary).copy(alpha = 0.3f),
                                                (detectedPlatform?.brandColor ?: CosmosPrimary).copy(alpha = 0.1f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = detectedPlatform?.icon ?: Icons.Default.Language,
                                    contentDescription = null,
                                    tint = detectedPlatform?.brandColor ?: CosmosPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Virtual Venue",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CosmosOnBackground,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = detectedPlatform?.name ?: "Custom Platform",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CosmosOnSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // SIGNAL ACTIVE Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, CosmosGlassBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        CosmosSuccess.copy(alpha = signalPulse)
                                    )
                            )
                            Text(
                                text = "SIGNAL ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosSuccess,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Link Preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CosmosSurfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = CosmosPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosPrimary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Venue Stats Row — equal width columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        VenueStatItem(
                            icon = Icons.Default.Security,
                            label = "Encrypted",
                            value = if (url.startsWith("https")) "Yes" else "No",
                            modifier = Modifier.weight(1f)
                        )
                        VenueStatItem(
                            icon = Icons.Default.Language,
                            label = "Protocol",
                            value = if (url.startsWith("https")) "HTTPS" else "HTTP",
                            modifier = Modifier.weight(1f)
                        )
                        VenueStatItem(
                            icon = Icons.Default.Wifi,
                            label = "Stream",
                            value = "Ready",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── Subtle section divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            CosmosOutlineVariant.copy(alpha = 0.2f),
                            CosmosOutlineVariant.copy(alpha = 0.3f),
                            CosmosOutlineVariant.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Atmosphere Presets ──
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "EVENT ATMOSPHERE",
                style = MaterialTheme.typography.labelSmall,
                color = CosmosPrimary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left edge spacing for visual balance
                Spacer(modifier = Modifier.width(0.dp))
                VIRTUAL_ATMOSPHERES.forEach { atmosphere ->
                    val isSelected = selectedAtmosphere == atmosphere
                    val chipScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.02f else 1f,
                        animationSpec = tween(200),
                        label = "ChipScale"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .graphicsLayer(scaleX = chipScale, scaleY = chipScale)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected)
                                    Brush.horizontalGradient(
                                        listOf(
                                            CosmosPrimary.copy(alpha = 0.2f),
                                            CosmosGradientEnd.copy(alpha = 0.1f)
                                        )
                                    )
                                else Brush.horizontalGradient(
                                    listOf(CosmosSurfaceContainerLow, CosmosSurfaceContainerLow)
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CosmosPrimary.copy(alpha = 0.6f) else CosmosOutlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedAtmosphere = if (isSelected) null else atmosphere
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(atmosphere.emoji, fontSize = 16.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = atmosphere.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) CosmosPrimary else CosmosOnBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = atmosphere.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosOnSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                // Right edge spacing for visual balance
                Spacer(modifier = Modifier.width(0.dp))
            }
        }
    }
}

@Composable
private fun VirtualPlatformCard(
    platform: VirtualPlatform,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.8f else 0f,
        animationSpec = tween(300),
        label = "BorderAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) platform.brandColor.copy(alpha = 0.08f)
                else CosmosSurfaceContainerLow
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) platform.brandColor.copy(alpha = borderAlpha)
                else CosmosOutlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Platform icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(platform.brandColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = platform.icon,
                    contentDescription = platform.name,
                    tint = platform.brandColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Name + tagline
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = platform.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) platform.brandColor else CosmosOnBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = platform.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Check indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(CosmosGradientStart, CosmosGradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VenueStatItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(CosmosPrimary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CosmosPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = CosmosOnBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CosmosOnSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 9.sp
        )
    }
}

@Composable
fun CosmicMapFullscreenDialog(
    location: String,
    placeId: String,
    apiKey: String,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0E12))
                .systemBarsPadding()
        ) {
            // Fullscreen Map WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                    }
                },
                update = { webView ->
                    try {
                        val html = buildCosmicMapHtml(apiKey, placeId, location)
                        webView.loadDataWithBaseURL("https://maps.google.com", html, "text/html", "UTF-8", null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, CosmosGlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, CosmosGlassBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = CosmosPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = location.substringBefore(","),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenExternal,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, CosmosGlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open in App",
                        tint = Color.White
                    )
                }
            }

            // Bottom Action Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                CosmosButton(
                    text = "Confirm Coordinates",
                    icon = Icons.Default.Check,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildCosmicMapHtml(apiKey: String, placeId: String, location: String): String {
    val embedUrl = if (placeId.isNotBlank()) {
        "https://www.google.com/maps/embed/v1/place?key=$apiKey&q=place_id:$placeId"
    } else {
        val encoded = URLEncoder.encode(location, "UTF-8")
        "https://www.google.com/maps/embed/v1/place?key=$apiKey&q=$encoded"
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body, html { width: 100%; height: 100%; overflow: hidden; background: #0c0e12; }
                .map-wrapper {
                    width: 100%;
                    height: 100%;
                    position: relative;
                    background: #0c0e12;
                }
                iframe {
                    width: 100%;
                    height: 100%;
                    border: 0;
                    filter: invert(90%) hue-rotate(180deg) brightness(95%) contrast(110%) saturate(120%);
                    -webkit-filter: invert(90%) hue-rotate(180deg) brightness(95%) contrast(110%) saturate(120%);
                }
            </style>
        </head>
        <body>
            <div class="map-wrapper">
                <iframe 
                    src="$embedUrl" 
                    allowfullscreen="" 
                    loading="lazy">
                </iframe>
            </div>
        </body>
        </html>
    """.trimIndent()
}

private fun launchExternalGoogleMaps(context: Context, location: String, placeId: String) {
    try {
        val encodedLoc = URLEncoder.encode(location, "UTF-8")
        val uri = if (placeId.isNotBlank()) {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedLoc&query_place_id=$placeId")
        } else {
            Uri.parse("https://maps.google.com/?q=$encodedLoc")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
