package com.cosmos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cosmos.app.ui.theme.*

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
    size: Dp = 56.dp,
    isLinkedInConnected: Boolean = false,
    membershipTierColor: Color = Color.Transparent
) {
    Box(contentAlignment = Alignment.BottomEnd) {
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
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = name,
                    tint = CosmosOnSurfaceVariant,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
        // LinkedIn badge
        if (isLinkedInConnected) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(CosmosLinkedIn)
                    .offset(x = 2.dp, y = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "in",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 7.sp
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
 * Cosmos top app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmosTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = CosmosOnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CosmosOnBackground
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CosmosBackground,
            titleContentColor = CosmosOnBackground,
            navigationIconContentColor = CosmosOnBackground,
            actionIconContentColor = CosmosOnBackground
        )
    )
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
 * Ambient background radial gradient (matches Stitch bg-ambient CSS).
 */
@Composable
fun CosmosAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CosmosBackground)
            .drawBehind {
                // Radial gradient at top-center (like CSS: circle at 50% -20%)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF494BD6).copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, -size.height * 0.2f),
                        radius = size.height * 0.85f
                    ),
                    radius = size.height * 0.85f,
                    center = Offset(size.width / 2f, -size.height * 0.2f)
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


