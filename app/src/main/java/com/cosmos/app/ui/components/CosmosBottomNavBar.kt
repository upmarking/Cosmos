package com.cosmos.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cosmos.app.data.repository.ServiceLocator
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

val cosmosBottomNavItems = listOf(
    BottomNavItem(
        label = "Connect",
        route = Screen.Connect.route,
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    ),
    BottomNavItem(
        label = "Events",
        route = Screen.Events.route,
        selectedIcon = Icons.Filled.Event,
        unselectedIcon = Icons.Outlined.Event
    ),
    BottomNavItem(
        label = "Circles",
        route = Screen.Communities.route,
        selectedIcon = Icons.Filled.Hub,
        unselectedIcon = Icons.Outlined.Hub
    ),
    BottomNavItem(
        label = "Chats",
        route = Screen.Conversations.route,
        selectedIcon = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum
    ),
    BottomNavItem(
        label = "Profile",
        route = Screen.Profile.route,
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle
    )
)

// Draws a drop shadow beneath the composable — simulates elevation blur
private fun Modifier.coloredShadow(
    color: Color,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 20.dp,
    offsetY: Dp = 6.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0.dp
) = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(
                    blurRadius.toPx(),
                    offsetX.toPx(),
                    offsetY.toPx(),
                    color.copy(alpha = 0.35f).toArgb()
                )
            }
        }
        canvas.drawRoundRect(
            left   = spread.toPx(),
            top    = spread.toPx(),
            right  = size.width - spread.toPx(),
            bottom = size.height - spread.toPx(),
            radiusX = borderRadius.toPx(),
            radiusY = borderRadius.toPx(),
            paint  = paint
        )
    }
}

@Composable
fun CosmosBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Reactive unread count
    val currentUserState by ServiceLocator.authRepository.currentUser.collectAsState(initial = null)
    val userId = currentUserState?.id
    val unreadChatCount by remember(userId) {
        if (userId != null) {
            ServiceLocator.chatRepository.getUnreadCountTotal(userId)
        } else {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }.collectAsState(initial = 0)

    // Only show on top-level tab routes
    val topLevelRoutes = cosmosBottomNavItems.map { it.route }
    val shouldShow = topLevelRoutes.any { currentRoute == it }

    if (!shouldShow) return

    val currentRouteIndex = remember(currentRoute) {
        cosmosBottomNavItems.indexOfFirst { it.route == currentRoute }
    }

    var visualSelectedIndex by remember {
        mutableIntStateOf(if (currentRouteIndex != -1) currentRouteIndex else 0)
    }
    var isTransitioning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Sync visual selection with route changes (e.g. initial navigation or external updates)
    LaunchedEffect(currentRouteIndex) {
        if (!isTransitioning && currentRouteIndex != -1) {
            visualSelectedIndex = currentRouteIndex
        }
    }

    // Glassmorphic pill colors
    val glassColor    = Color(0xFF1C1F26).copy(alpha = 0.82f)  // deep dark translucent
    val borderBrush   = Brush.linearGradient(
        colors = listOf(
            Color(0x40FFFFFF),   // bright top-left glint
            Color(0x0AFFFFFF),   // fade to near-transparent
            Color(0x18C0C1FF),   // subtle indigo shimmer
        )
    )
    val pillShape     = RoundedCornerShape(32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // ── Outer glow / shadow ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .coloredShadow(
                    color        = CosmosPrimary,
                    borderRadius = 32.dp,
                    blurRadius   = 24.dp,
                    offsetY      = 8.dp,
                    spread       = (-4).dp
                )
                .clip(pillShape)
                // Frosted glass base
                .background(glassColor)
                // Gradient border overlay
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val strokePx = 1.2.dp.toPx()
                        val framePaint = Paint().apply {
                            asFrameworkPaint().apply {
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = strokePx
                            }
                        }
                        // Draw rounded rect border
                        val rr = size.height / 2f
                        canvas.drawRoundRect(
                            left   = strokePx / 2,
                            top    = strokePx / 2,
                            right  = size.width - strokePx / 2,
                            bottom = size.height - strokePx / 2,
                            radiusX = rr,
                            radiusY = rr,
                            paint   = framePaint
                        )
                    }
                }
        ) {
            // Subtle inner white top-glint strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x33FFFFFF),
                                Color(0x55FFFFFF),
                                Color(0x33FFFFFF),
                                Color.Transparent
                            )
                        )
                    )
            )

            // ── Nav items ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                cosmosBottomNavItems.forEachIndexed { index, item ->
                    val isSelected = visualSelectedIndex == index
                    val displayItem = if (item.route == Screen.Conversations.route) {
                        item.copy(badgeCount = unreadChatCount)
                    } else {
                        item
                    }
                    GlassNavBarItem(
                        item       = displayItem,
                        isSelected = isSelected,
                        onClick    = {
                            if (!isTransitioning && visualSelectedIndex != index) {
                                coroutineScope.launch {
                                    isTransitioning = true
                                    val start = visualSelectedIndex
                                    val end = index
                                    val step = if (end > start) 1 else -1

                                    // Navigate immediately for responsive screen transitions
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Connect.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }

                                    var curr = start
                                    while (curr != end) {
                                        curr += step
                                        visualSelectedIndex = curr
                                        delay(70)
                                    }
                                    isTransitioning = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.GlassNavBarItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Spring-animated scale on press
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "navItemScale"
    )

    // Animated icon tint
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) CosmosPrimary else Color(0xFF8A8FA8),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "navIconColor"
    )

    // Animated label tint
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) CosmosPrimary else Color(0xFF6B7080),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "navLabelColor"
    )

    // Pill indicator height (grows when selected)
    val indicatorSize by animateDpAsState(
        targetValue = if (isSelected) 36.dp else 0.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "navIndicatorSize"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scale)
        ) {

            // Glowing pill behind selected icon
            Box(contentAlignment = Alignment.Center) {
                // Glow pill — visible only when selected
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(indicatorSize)
                            .height(indicatorSize)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        CosmosPrimary.copy(alpha = 0.25f),
                                        CosmosPrimary.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                BadgedBox(
                    badge = {
                        if (item.badgeCount > 0 && !isSelected) {
                            Badge(
                                containerColor = CosmosError,
                                contentColor   = CosmosOnError
                            ) {
                                Text(
                                    item.badgeCount.coerceAtMost(99).toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector     = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint            = iconColor,
                        modifier        = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text  = item.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                ),
                color = labelColor
            )
        }
    }
}
