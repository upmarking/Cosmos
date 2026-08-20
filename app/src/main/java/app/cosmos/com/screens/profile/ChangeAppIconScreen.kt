package app.cosmos.com.screens.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.R
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
                    .padding(vertical = 20.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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

            Spacer(Modifier.height(28.dp))

            // ── Active Icon Card & Quick Reset ───────────────────────────
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
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = selected.emoji,
                                fontSize = 20.sp
                            )
                            Text(
                                text = selected.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE6E8EE),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF283248))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (selected == CosmosAppIcon.DEFAULT) "Default Active" else "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8AB4F8),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = selected.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9AA0A6)
                    )

                    if (selected != CosmosAppIcon.DEFAULT) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                val success = AppIconManager.setAppIcon(context, CosmosAppIcon.DEFAULT)
                                activeIcon = CosmosAppIcon.DEFAULT
                                val msg = if (success) "Restored default Cosmos icon!" else "Set to Cosmos Classic"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = CosmosPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CosmosPrimary.copy(alpha = 0.5f))
                        ) {
                            Text("↺ Restore Default Cosmos Icon", fontSize = 13.sp)
                        }
                    }
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
            .size(72.dp)
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
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(iconOption.previewBgColor))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color(iconOption.previewBorderColor) else Color(0xFF333842),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            CosmosIconEmblemCanvas(
                iconOption = iconOption,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (iconOption == CosmosAppIcon.DEFAULT) 0.dp else 6.dp)
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
                    .background(Color(0xFF1E222B))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8AB4F8)),
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
 * Renders the authentic original Cosmos logo and official tier launcher icons.
 */
@Composable
fun CosmosIconEmblemCanvas(
    iconOption: CosmosAppIcon,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (iconOption) {
            CosmosAppIcon.DEFAULT -> {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "Original Cosmos Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            CosmosAppIcon.ASTEROID -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_fg_asteroid),
                    contentDescription = "Asteroid Emblem",
                    modifier = Modifier.fillMaxSize(0.9f),
                    contentScale = ContentScale.Fit
                )
            }
            CosmosAppIcon.MOON -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_fg_moon),
                    contentDescription = "Lunar Moon Emblem",
                    modifier = Modifier.fillMaxSize(0.9f),
                    contentScale = ContentScale.Fit
                )
            }
            CosmosAppIcon.EARTH -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_fg_earth),
                    contentDescription = "Terra Earth Emblem",
                    modifier = Modifier.fillMaxSize(0.9f),
                    contentScale = ContentScale.Fit
                )
            }
            CosmosAppIcon.SUN -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_fg_sun),
                    contentDescription = "Solar Sun Emblem",
                    modifier = Modifier.fillMaxSize(0.9f),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
