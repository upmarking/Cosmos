package app.cosmos.com.screens.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.util.AppIconManager
import app.cosmos.com.data.util.CosmosAppIcon
import app.cosmos.com.ui.theme.*

/**
 * ChangeAppIconScreen — Matches the Google iOS/Android "Change app icon" experience.
 *
 * Displays a clean dark screen with a top bar, subtitle, and an aesthetic card
 * containing the squircle app icon options with the signature bottom-right
 * circular checkmark badge on the currently active icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeAppIconScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeIcon by remember { mutableStateOf(AppIconManager.getActiveIcon(context)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13151B))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Top Bar: Back Button & Title ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFE6E8EE),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Change app icon",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 22.sp
                    ),
                    color = Color(0xFFE6E8EE)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Subtitle ─────────────────────────────────────────────────
            Text(
                text = "Change the COSMOS app icon on your home screen to match your style or mood",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF9AA0A6),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── App Icon Picker Container Card ───────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E222B))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF2E3440).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(vertical = 20.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CosmosAppIcon.values().forEach { iconOption ->
                        val isSelected = activeIcon == iconOption

                        AppIconSquircleItem(
                            iconOption = iconOption,
                            isSelected = isSelected,
                            onSelect = {
                                if (activeIcon != iconOption) {
                                    val success = AppIconManager.setAppIcon(context, iconOption)
                                    activeIcon = iconOption
                                    val msg = if (success) {
                                        "App icon changed to ${iconOption.title}!"
                                    } else {
                                        "Selected ${iconOption.title} as app icon"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Icon Name & Description ──────────────────────────────────
            AnimatedContent(
                targetState = activeIcon,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "icon_info"
            ) { selected ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF181B22))
                        .border(1.dp, Color(0xFF282D37), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = selected.emoji,
                            fontSize = 18.sp
                        )
                        Text(
                            text = selected.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFE6E8EE),
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF283248))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8AB4F8),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = selected.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9AA0A6)
                    )
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

/**
 * A single squircle app icon item with the badge indicator.
 */
@Composable
private fun AppIconSquircleItem(
    iconOption: CosmosAppIcon,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(68.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Main App Icon Squircle ───────────────────────────────────────
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(iconOption.previewBgColor))
                .border(
                    width = 1.dp,
                    color = if (iconOption == CosmosAppIcon.ASTEROID) Color(0xFFD0D5DD) else Color(0xFF333842),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            CosmosIconEmblemCanvas(
                iconOption = iconOption,
                modifier = Modifier.size(38.dp)
            )
        }

        // ── Checkmark Badge (Bottom-Right) ───────────────────────────────
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 2.dp, y = 2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E222B)) // dark ring border matching card
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8AB4F8)), // Google/Cosmos signature blue checkmark
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF13151B),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Canvas drawing the authentic COSMOS "C" emblem corresponding to each style:
 * 1. ASTEROID: White squircle background with multi-color COSMOS "C" (Google icon #1 style)
 * 2. MOON: Dark slate background with vibrant multi-color COSMOS "C" (Google icon #2 style)
 * 3. EARTH: White squircle background with bold dark COSMOS "C" (Google icon #3 style)
 * 4. SUN: Stealth black background with crisp pure white COSMOS "C" (Google icon #4 style)
 */
@Composable
private fun CosmosIconEmblemCanvas(
    iconOption: CosmosAppIcon,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width * 0.38f
        val strokeWidth = size.width * 0.22f

        when (iconOption) {
            // ── Style 1: Classic White with Multi-Color "C" (Google #1) ─
            CosmosAppIcon.ASTEROID -> {
                // Red, Yellow, Green, Blue gradient sweeps
                drawCosmosMultiColorG(center, radius, strokeWidth)
            }

            // ── Style 2: Dark Background with Multi-Color "C" (Google #2) ──
            CosmosAppIcon.MOON -> {
                // Dark background multi-color vibrant sweep
                drawCosmosMultiColorG(center, radius, strokeWidth)
            }

            // ── Style 3: Monochrome Light with Bold Black "C" (Google #3) ──
            CosmosAppIcon.EARTH -> {
                drawCosmosSolidG(center, radius, strokeWidth, Color(0xFF1B1F27))
            }

            // ── Style 4: Stealth Dark with Crisp White "C" (Google #4) ──────
            CosmosAppIcon.SUN -> {
                drawCosmosSolidG(center, radius, strokeWidth, Color(0xFFFFFFFF))
            }
        }
    }
}

/**
 * Helper to draw the multi-color circular "G" / "C" emblem with horizontal crossbar.
 */
private fun DrawScope.drawCosmosMultiColorG(
    center: Offset,
    radius: Float,
    strokeWidth: Float
) {
    val rect = Size(radius * 2f, radius * 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)

    // Red arc (top-left)
    drawArc(
        color = Color(0xFFEA4335),
        startAngle = 140f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = topLeft,
        size = rect,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    )

    // Yellow arc (bottom-left)
    drawArc(
        color = Color(0xFFFBBC05),
        startAngle = 90f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = topLeft,
        size = rect,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    )

    // Green arc (bottom)
    drawArc(
        color = Color(0xFF34A853),
        startAngle = 0f,
        sweepAngle = 95f,
        useCenter = false,
        topLeft = topLeft,
        size = rect,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    )

    // Blue arc + crossbar (right side)
    drawArc(
        color = Color(0xFF4285F4),
        startAngle = 235f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = topLeft,
        size = rect,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    )

    // Blue horizontal bar
    drawLine(
        color = Color(0xFF4285F4),
        start = center,
        end = Offset(center.x + radius + strokeWidth * 0.45f, center.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square
    )
}

/**
 * Helper to draw monochrome solid "G" / "C" emblem.
 */
private fun DrawScope.drawCosmosSolidG(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color
) {
    val rect = Size(radius * 2f, radius * 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)

    // Main arc opening to the right
    drawArc(
        color = color,
        startAngle = 35f,
        sweepAngle = 290f,
        useCenter = false,
        topLeft = topLeft,
        size = rect,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Square)
    )

    // Horizontal bar
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x + radius + strokeWidth * 0.45f, center.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Square
    )
}
