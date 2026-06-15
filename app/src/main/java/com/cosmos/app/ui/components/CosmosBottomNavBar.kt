package com.cosmos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.cosmos.app.data.repository.ServiceLocator
import com.cosmos.app.navigation.Screen
import com.cosmos.app.ui.theme.*

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

@Composable
fun CosmosBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Get current logged-in user dynamically
    val currentUserState by ServiceLocator.authRepository.currentUser.collectAsState(initial = null)
    val userId = currentUserState?.id

    // Collect unread count reactively if user is logged in
    val unreadChatCount by remember(userId) {
        if (userId != null) {
            ServiceLocator.chatRepository.getUnreadCountTotal(userId)
        } else {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }.collectAsState(initial = 0)

    // Only show bottom nav on top-level tab routes
    val topLevelRoutes = cosmosBottomNavItems.map { it.route }
    val shouldShow = topLevelRoutes.any { currentRoute == it }

    if (!shouldShow) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CosmosBackground)
    ) {
        // Top border line
        Divider(
            color = CosmosOutlineVariant.copy(alpha = 0.4f),
            thickness = 0.5.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            cosmosBottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val displayItem = if (item.route == Screen.Conversations.route) {
                    item.copy(badgeCount = unreadChatCount)
                } else {
                    item
                }
                CosmosNavBarItem(
                    item = displayItem,
                    isSelected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            navController.navigate(item.route) {
                                // Pop up to the first tab route to avoid a large backstack
                                popUpTo(Screen.Connect.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RowScope.CosmosNavBarItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BadgedBox(
                badge = {
                    if (item.badgeCount > 0 && !isSelected) {
                        Badge(
                            containerColor = CosmosError,
                            contentColor = CosmosOnError
                        ) {
                            Text(item.badgeCount.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant
            )
        }
    }
}
