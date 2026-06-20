package app.cosmos.com.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.cosmos.com.ui.theme.*

/**
 * Primary gradient button (indigo → blue, full-width pill).
 */
@Composable
fun CosmosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (enabled)
                    Brush.linearGradient(
                        colors = listOf(CosmosGradientStart, CosmosGradientEnd)
                    )
                else
                    Brush.linearGradient(
                        colors = listOf(CosmosOutlineVariant, CosmosOutlineVariant)
                    )
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Secondary outlined button.
 */
@Composable
fun CosmosOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = CosmosPrimary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, CosmosOutlineVariant)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Glass card — frosted glass effect as seen in the Stitch designs.
 */
@Composable
fun CosmosGlassCard(
    modifier: Modifier = Modifier,
    showTopGradientBorder: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CosmosGlass)
                .border(
                    width = 1.dp,
                    color = CosmosGlassBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp),
            content = content
        )
        // Top gradient shimmer line
        if (showTopGradientBorder) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                CosmosPrimary.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Member avatar with optional LinkedIn badge.
 */
@Composable
fun CosmosAvatar(
    avatarUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isLinkedInConnected: Boolean = false,
    membershipTierColor: Color = Color.Transparent
) {
    Box(contentAlignment = Alignment.BottomEnd, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(CosmosSurfaceContainerHigh)
                .border(
                    width = 2.dp,
                    color = membershipTierColor.takeIf { it != Color.Transparent } ?: CosmosOutlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotEmpty()) {
                val model: Any = if (avatarUrl.startsWith("data:image")) {
                    try {
                        val base64Data = avatarUrl.substringAfter(",")
                        android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        avatarUrl
                    }
                } else {
                    avatarUrl
                }
                AsyncImage(
                    model = model,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = CosmosOnSurfaceVariant,
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // LinkedIn badge
        if (isLinkedInConnected) {
            Box(
                modifier = Modifier
                    .offset(x = 2.dp, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(CosmosLinkedIn),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "in",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp
                )
            }
        }
    }
}

/**
 * Tag chip (small pill label with subtle background).
 */
@Composable
fun CosmosTagChip(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = CosmosSurfaceContainerHigh,
    textColor: Color = CosmosPrimary,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

/**
 * Premium glass top bar for sub-screens (back-button flows).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmosTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        CosmosBackground.copy(alpha = 0.97f),
                        CosmosBackground.copy(alpha = 0.88f)
                    )
                )
            )
    ) {
        // Subtle top shimmer line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            CosmosPrimary.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = CosmosOnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CosmosSurfaceContainerHigh.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = CosmosOnBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = CosmosOnBackground,
                navigationIconContentColor = CosmosOnBackground,
                actionIconContentColor = CosmosOnBackground
            )
        )
    }
}

/**
 * Premium glassmorphic top bar for main tabs — creative logo lockup,
 * gradient page title, animated star, glowing badge on notification bell.
 */
@Composable
fun CosmosGlassTopBar(
    pageTitle: String,
    notificationCount: Int = 0,
    requestCount: Int = 0,
    onSearchClick: (() -> Unit)? = null,
    onNotificationsClick: (() -> Unit)? = null,
    onRequestsClick: (() -> Unit)? = null,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    searchPlaceholder: String = "Search...",
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    // Spinning star animation
    val infiniteTransition = rememberInfiniteTransition(label = "topBarAnim")
    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "starSpin"
    )
    // Notification glow pulse
    val notifPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "notifPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF16191F).copy(alpha = 0.88f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0x44FFFFFF),
                            Color(0x0AFFFFFF),
                            Color(0x22C0C1FF)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            // Inner top glint
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x55FFFFFF),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearchActive) {
                    IconButton(
                        onClick = onSearchClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close Search",
                            tint = CosmosOnSurfaceVariant
                        )
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = searchPlaceholder,
                                color = CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = CosmosOnBackground),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = CosmosOnBackground,
                            unfocusedTextColor = CosmosOnBackground,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = CosmosPrimary
                        ),
                        singleLine = true
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = CosmosOnSurfaceVariant
                            )
                        }
                    }
                } else {
                    // ── LEFT: COSMOS logo lockup & title ───────────────────────
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(
                                    brush = Brush.linearGradient(
                                        listOf(CosmosPrimary, CosmosSecondary)
                                    ),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.5.sp
                                )) { append("COSMOS") }
                            }
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "✦",
                            color = CosmosPrimary.copy(alpha = 0.85f),
                            fontSize = 8.sp,
                            modifier = Modifier.rotate(starRotation)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = CosmosOnBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ── RIGHT: action icons ───────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        extraActions()

                        if (onSearchClick != null) {
                            GlassIconButton(
                                icon = Icons.Outlined.Search,
                                contentDescription = "Search",
                                onClick = onSearchClick
                            )
                        }

                        if (onRequestsClick != null) {
                            Box {
                                GlassIconButton(
                                    icon = Icons.Outlined.PersonAdd,
                                    contentDescription = "Requests",
                                    onClick = onRequestsClick
                                )
                                if (requestCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(CosmosPrimary, CosmosGradientStart)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = requestCount.coerceAtMost(9).toString(),
                                            color = Color.White,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (onNotificationsClick != null) {
                            Box {
                                GlassIconButton(
                                    icon = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    onClick = onNotificationsClick
                                )
                                if (notificationCount > 0) {
                                    // Pulsing glow dot
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .size(14.dp)
                                            .graphicsLayer { alpha = notifPulse }
                                            .clip(CircleShape)
                                            .background(CosmosError),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = notificationCount.coerceAtMost(9).toString(),
                                            color = CosmosOnError,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
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
}

/** Small glass icon button used inside CosmosGlassTopBar */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CosmosOnSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * Cosmos section header with optional action link.
 */
@Composable
fun CosmosSectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CosmosOnBackground
        )
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = CosmosPrimary
                )
            }
        }
    }
}

/**
 * Upgraded ambient background: 3 animated radial glow blobs + floating orbs.
 */
@Composable
fun CosmosAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambientAnim")

    // Blob 1 — top indigo pulse
    val blob1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blob1"
    )
    // Blob 2 — mid-right violet pulse
    val blob2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.07f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse),
        label = "blob2"
    )
    // Blob 3 — bottom-left deep blue pulse
    val blob3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.05f, targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(5800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blob3"
    )
    // Floating orb drift
    val orbOffset by infiniteTransition.animateFloat(
        initialValue = -15f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbDrift"
    )
    // Floating orb pulsing opacity
    val orbAlpha by infiniteTransition.animateFloat(
        initialValue = 0.03f, targetValue = 0.09f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbAlpha"
    )
    // Floating orb pulsing radius scale
    val orbRadiusScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbRadiusScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmosBackground)
            .drawBehind {
                // Blob 1 — large indigo at top-center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF494BD6).copy(alpha = blob1Alpha), Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.1f),
                        radius = size.height * 0.75f
                    ),
                    radius = size.height * 0.75f,
                    center = Offset(size.width * 0.5f, -size.height * 0.1f)
                )
                // Blob 2 — violet mid-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7B61FF).copy(alpha = blob2Alpha), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.35f),
                        radius = size.height * 0.45f
                    ),
                    radius = size.height * 0.45f,
                    center = Offset(size.width * 0.85f, size.height * 0.35f)
                )
                // Blob 3 — deep blue bottom-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0566D9).copy(alpha = blob3Alpha), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.82f),
                        radius = size.height * 0.38f
                    ),
                    radius = size.height * 0.38f,
                    center = Offset(size.width * 0.15f, size.height * 0.82f)
                )
                // Floating micro orb — now a soft-glowing pulsing radial blur
                val computedRadius = 120.dp.toPx() * orbRadiusScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFC0C1FF).copy(alpha = orbAlpha), Color.Transparent),
                        center = Offset(size.width * 0.72f, size.height * 0.18f + orbOffset),
                        radius = computedRadius
                    ),
                    radius = computedRadius,
                    center = Offset(size.width * 0.72f, size.height * 0.18f + orbOffset)
                )
            },
        content = content
    )
}

/**
 * Progress dots for onboarding steps.
 */
@Composable
fun CosmosProgressDots(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(if (index == currentStep) 32.dp else 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index == currentStep) CosmosPrimary
                        else CosmosSurfaceVariant
                    )
            )
        }
    }
}

/**
 * Metric stat card for dashboard/profile stats.
 */
@Composable
fun CosmosStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = CosmosPrimary
) {
    CosmosGlassCard(
        modifier = modifier,
        showTopGradientBorder = false
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = CosmosOnSurfaceVariant
        )
    }
}


