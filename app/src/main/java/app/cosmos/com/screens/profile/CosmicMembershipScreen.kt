package app.cosmos.com.screens.profile

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.cosmos.com.data.model.GiftCard
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.payment.MembershipAnalytics
import app.cosmos.com.data.payment.PaymentManager
import app.cosmos.com.data.repository.ServiceLocator
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
// COSMOS Lifetime Membership — Cinematic Cosmic Journey Screen
// ══════════════════════════════════════════════════════════════════════════════

/**
 * A single star in the animated starfield background.
 */
private data class Star(
    val x: Float,          // 0..1 normalized
    val y: Float,          // 0..1 normalized
    val size: Float,       // radius in dp
    val brightness: Float, // 0..1 alpha
    val speed: Float,      // twinkle speed multiplier
    val layer: Int         // parallax depth (0=far, 2=near)
)

/**
 * Generates a random starfield.
 */
private fun generateStars(count: Int): List<Star> {
    return List(count) {
        Star(
            x = Random.nextFloat(),
            y = Random.nextFloat(),
            size = Random.nextFloat() * 1.8f + 0.3f,
            brightness = Random.nextFloat() * 0.6f + 0.4f,
            speed = Random.nextFloat() * 1.5f + 0.5f,
            layer = Random.nextInt(3)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmicMembershipScreen(
    onBack: () -> Unit,
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val currentUserState by authViewModel.currentUser.collectAsState()
    val currentTier = currentUserState?.membershipTier ?: MembershipTier.ASTEROID

    val allPlans = PaymentManager.getAllPlans()
    val coroutineScope = rememberCoroutineScope()

    // Upgrade dialog state
    var selectedUpgradeTier by remember { mutableStateOf<MembershipTier?>(null) }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var successTier by remember { mutableStateOf<MembershipTier?>(null) }
    var showGiftCardHubDialog by remember { mutableStateOf(false) }

    // Starfield
    val stars = remember { generateStars(80) }

    // Infinite animation for starfield twinkle
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic")
    val twinklePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "twinkle"
    )

    // Slow nebula drift
    val nebulaDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebula"
    )

    // Track analytics & seed demo gift cards
    LaunchedEffect(Unit) {
        MembershipAnalytics.membershipPageViewed()
        coroutineScope.launch {
            ServiceLocator.giftCardRepository.seedDemoGiftCards()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Deep Space Background ────────────────────────────────────────
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Deep space gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CosmosCosmicDeep,
                        Color(0xFF0D0D2B),
                        CosmosCosmicPurple.copy(alpha = 0.6f),
                        CosmosCosmicDeep
                    )
                )
            )

            // Nebula glow patches
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CosmosNebulaBlue.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * (0.2f + nebulaDrift * 0.1f), size.height * 0.3f),
                    radius = size.width * 0.5f
                ),
                center = Offset(size.width * (0.2f + nebulaDrift * 0.1f), size.height * 0.3f),
                radius = size.width * 0.5f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CosmosNebulaPink.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * (0.8f - nebulaDrift * 0.08f), size.height * 0.7f),
                    radius = size.width * 0.4f
                ),
                center = Offset(size.width * (0.8f - nebulaDrift * 0.08f), size.height * 0.7f),
                radius = size.width * 0.4f
            )

            // Starfield
            stars.forEach { star ->
                val twinkle = (sin(twinklePhase * star.speed + star.x * 10f) * 0.3f + 0.7f)
                val alpha = star.brightness * twinkle
                val parallaxOffset = when (star.layer) {
                    0 -> nebulaDrift * 2f
                    1 -> nebulaDrift * 5f
                    else -> nebulaDrift * 10f
                }
                val sx = ((star.x + parallaxOffset * 0.01f) % 1f) * size.width
                val sy = star.y * size.height
                drawCircle(
                    color = CosmosStarWhite.copy(alpha = alpha),
                    radius = star.size * (if (star.layer == 2) 1.5f else 1f),
                    center = Offset(sx, sy)
                )
            }
        }

        // ── Content ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = CosmosStarWhite
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "COSMOS Membership",
                    style = MaterialTheme.typography.titleMedium,
                    color = CosmosStarWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showGiftCardHubDialog = true }) {
                    Icon(
                        Icons.Outlined.CardGiftcard,
                        contentDescription = "Gift Card Hub",
                        tint = CosmosPrimary
                    )
                }
            }

            // Cosmic journey headline
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "YOUR COSMIC JOURNEY",
                    style = MaterialTheme.typography.labelMedium,
                    color = CosmosPrimary,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Start small. Upgrade your universe.\nReach the Sun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmosStarWhite.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            // Cosmic Gift Card Banner
            CosmicGiftCardBanner(
                onClick = { showGiftCardHubDialog = true }
            )

            Spacer(Modifier.height(8.dp))

            // ── Cosmic Journey Path ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                allPlans.forEachIndexed { index, plan ->
                    val tier = plan.tier
                    val isCurrentTier = tier == currentTier
                    val isUnlocked = tier.tierLevel <= currentTier.tierLevel
                    val canUpgrade = currentTier.canUpgradeTo(tier)
                    val upgradeAmount = if (canUpgrade) PaymentManager.calculateUpgradeAmount(currentTier, tier) else 0

                    // Orbital connection line (between tiers)
                    if (index > 0) {
                        CosmicPathConnector(
                            isActive = allPlans[index - 1].tier.tierLevel < currentTier.tierLevel ||
                                    allPlans[index - 1].tier == currentTier,
                            twinklePhase = twinklePhase
                        )
                    }

                    // Tier station
                    CosmicTierCard(
                        tier = tier,
                        plan = plan,
                        isCurrentTier = isCurrentTier,
                        isUnlocked = isUnlocked,
                        canUpgrade = canUpgrade,
                        upgradeAmount = upgradeAmount,
                        twinklePhase = twinklePhase,
                        onUpgradeClick = {
                            MembershipAnalytics.upgradeCtaClicked(
                                currentTier.name, tier.name, upgradeAmount
                            )
                            selectedUpgradeTier = tier
                        }
                    )
                }

                Spacer(Modifier.height(100.dp))
            }
        }

        // ── Upgrade Dialog ───────────────────────────────────────────────
        if (selectedUpgradeTier != null) {
            CosmicUpgradeDialog(
                currentTier = currentTier,
                targetTier = selectedUpgradeTier!!,
                onDismiss = { selectedUpgradeTier = null },
                onPaymentSuccess = { newTierName ->
                    val newTier = MembershipTier.fromLegacyName(newTierName)
                    currentUserState?.let { user ->
                        val updatedUser = user.copy(
                            membershipTier = newTier,
                            monthlyConnectionLimit = PaymentManager.getConnectionLimit(newTier)
                        )
                        authViewModel.updateProfile(updatedUser) {}
                    }
                    successTier = MembershipTier.fromLegacyName(newTierName)
                    showSuccessAnimation = true
                    selectedUpgradeTier = null
                }
            )
        }

        // ── Gift Card Hub Dialog ─────────────────────────────────────────
        if (showGiftCardHubDialog) {
            CosmicGiftCardHubDialog(
                currentTier = currentTier,
                onDismiss = { showGiftCardHubDialog = false },
                onUpgradeTier = { targetTier ->
                    showGiftCardHubDialog = false
                    selectedUpgradeTier = targetTier
                }
            )
        }

        // ── Success Animation ────────────────────────────────────────────
        if (showSuccessAnimation && successTier != null) {
            CosmicSuccessOverlay(
                tier = successTier!!,
                onDismiss = {
                    showSuccessAnimation = false
                    successTier = null
                }
            )
        }
    }
}

// ── Orbital Path Connector ──────────────────────────────────────────────────

@Composable
private fun CosmicPathConnector(
    isActive: Boolean,
    twinklePhase: Float
) {
    val color = if (isActive) CosmosPrimary.copy(alpha = 0.6f) else CosmosOutlineVariant.copy(alpha = 0.2f)
    val glowAlpha = if (isActive) (sin(twinklePhase * 0.5f) * 0.3f + 0.5f) else 0.15f

    Box(
        modifier = Modifier
            .width(3.dp)
            .height(40.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = glowAlpha),
                        color,
                        color.copy(alpha = glowAlpha)
                    )
                )
            )
    )
    // Small rocket icon on the active connector
    if (isActive) {
        Text(
            "🚀",
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(40.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = glowAlpha),
                        color,
                        color.copy(alpha = glowAlpha)
                    )
                )
            )
    )
}

// ── Cosmic Tier Card ────────────────────────────────────────────────────────

@Composable
private fun CosmicTierCard(
    tier: MembershipTier,
    plan: app.cosmos.com.data.model.SubscriptionPlan,
    isCurrentTier: Boolean,
    isUnlocked: Boolean,
    canUpgrade: Boolean,
    upgradeAmount: Int,
    twinklePhase: Float,
    onUpgradeClick: () -> Unit
) {
    val tierColor = Color(tier.color)
    val isMaxTier = tier == MembershipTier.SUN

    // Subtle pulse for current tier
    val infiniteTransition = rememberInfiniteTransition(label = "card_${tier.name}")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_${tier.name}"
    )

    // Card container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isCurrentTier) Brush.linearGradient(
                    listOf(
                        tierColor.copy(alpha = 0.12f * glowPulse),
                        CosmosCosmicDeep.copy(alpha = 0.95f)
                    )
                ) else if (isMaxTier && !isUnlocked) Brush.linearGradient(
                    listOf(
                        CosmosSunColor.copy(alpha = 0.05f),
                        CosmosCosmicDeep.copy(alpha = 0.95f)
                    )
                ) else Brush.linearGradient(
                    listOf(
                        CosmosGlass,
                        CosmosCosmicDeep.copy(alpha = 0.9f)
                    )
                )
            )
            .border(
                width = if (isCurrentTier) 1.5.dp else 1.dp,
                brush = if (isCurrentTier) Brush.linearGradient(
                    listOf(tierColor.copy(alpha = 0.6f), tierColor.copy(alpha = 0.2f))
                ) else if (isMaxTier) Brush.linearGradient(
                    listOf(CosmosSunColor.copy(alpha = 0.3f), CosmosSunCorona.copy(alpha = 0.1f))
                ) else Brush.linearGradient(
                    listOf(CosmosGlassBorder, CosmosGlassBorder)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            // Header: Cosmic Object + Tier Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Cosmic object + tier name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Cosmic object visual
                    CosmicObjectVisual(
                        tier = tier,
                        twinklePhase = twinklePhase,
                        isActive = isUnlocked,
                        size = 52f
                    )

                    Column {
                        // Tier metaphor label
                        Text(
                            PaymentManager.getTierMetaphor(tier),
                            style = MaterialTheme.typography.labelSmall,
                            color = tierColor.copy(alpha = 0.8f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            tier.label.uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isUnlocked) CosmosStarWhite else CosmosStarWhite.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            tier.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
                }

                // Price + status
                Column(horizontalAlignment = Alignment.End) {
                    if (isCurrentTier) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(tierColor.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "✓ CURRENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = tierColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else if (isMaxTier && !isUnlocked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(CosmosSunColor.copy(alpha = 0.2f), CosmosSunCorona.copy(alpha = 0.1f))
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "⚡ ULTIMATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosSunColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (tier.lifetimePrice == 0) {
                        Text(
                            "FREE",
                            style = MaterialTheme.typography.titleLarge,
                            color = tierColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "FOREVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            PaymentManager.formatIndianPrice(tier.lifetimePrice),
                            style = MaterialTheme.typography.titleLarge,
                            color = tierColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "LIFETIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Tier description
            Text(
                PaymentManager.getTierDescription(tier),
                style = MaterialTheme.typography.bodySmall,
                color = CosmosOnSurfaceVariant.copy(alpha = 0.6f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tierColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        tier.badge.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tierColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                if (isUnlocked && !isCurrentTier) {
                    Text(
                        "✓ Unlocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosSuccess.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Features
            plan.features.forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val featureAlpha = if (isUnlocked) 1f else 0.5f
                    Icon(
                        if (isUnlocked) Icons.Default.Check else Icons.Outlined.Lock,
                        null,
                        tint = if (isUnlocked) CosmosSuccess else CosmosOnSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosStarWhite.copy(alpha = featureAlpha)
                    )
                }
            }

            // Upgrade CTA
            if (canUpgrade) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onUpgradeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMaxTier) CosmosSunColor else tierColor
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🚀", fontSize = 16.sp)
                        Text(
                            "Upgrade to ${tier.label} — ${PaymentManager.formatIndianPrice(upgradeAmount)}",
                            color = if (isMaxTier) Color(0xFF1A0A00) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (isCurrentTier) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CosmosSuccess.copy(alpha = 0.1f))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = CosmosSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "YOUR CURRENT TIER • LIFETIME ACCESS",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosSuccess,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else if (tier == MembershipTier.SUN && isUnlocked) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(CosmosSunColor.copy(alpha = 0.15f), CosmosSunCorona.copy(alpha = 0.1f))
                            )
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "☀️  SUN UNLOCKED  •  SOLAR ELITE  •  MAXIMUM ACCESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosSunColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ── Cosmic Object Visual (Canvas-drawn planets) ─────────────────────────────

@Composable
private fun CosmicObjectVisual(
    tier: MembershipTier,
    twinklePhase: Float,
    isActive: Boolean,
    size: Float
) {
    val tierColor = Color(tier.color)
    val glowAlpha = if (isActive) 0.3f else 0.1f

    Canvas(
        modifier = Modifier.size(size.dp)
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.width * 0.35f

        when (tier) {
            MembershipTier.ASTEROID -> drawAsteroid(center, radius, twinklePhase, isActive)
            MembershipTier.MOON -> drawMoon(center, radius, twinklePhase, isActive)
            MembershipTier.EARTH -> drawEarth(center, radius, twinklePhase, isActive)
            MembershipTier.SUN -> drawSun(center, radius, twinklePhase, isActive)
        }
    }
}

private fun DrawScope.drawAsteroid(center: Offset, radius: Float, phase: Float, isActive: Boolean) {
    val alpha = if (isActive) 1f else 0.4f
    // Atmospheric glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                CosmosAsteroidColor.copy(alpha = 0.15f * alpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 2f
        ),
        center = center,
        radius = radius * 2f
    )
    // Irregular asteroid body (slightly offset circles to fake irregularity)
    val rotation = phase * 10f
    rotate(rotation, center) {
        drawCircle(
            color = CosmosAsteroidColor.copy(alpha = 0.8f * alpha),
            radius = radius * 0.9f,
            center = Offset(center.x - radius * 0.05f, center.y + radius * 0.05f)
        )
        drawCircle(
            color = Color(0xFF6E6E73).copy(alpha = 0.6f * alpha),
            radius = radius * 0.8f,
            center = Offset(center.x + radius * 0.1f, center.y - radius * 0.08f)
        )
        // Crater
        drawCircle(
            color = Color(0xFF58585D).copy(alpha = 0.5f * alpha),
            radius = radius * 0.2f,
            center = Offset(center.x + radius * 0.2f, center.y - radius * 0.15f)
        )
    }
}

private fun DrawScope.drawMoon(center: Offset, radius: Float, phase: Float, isActive: Boolean) {
    val alpha = if (isActive) 1f else 0.4f
    // Silver glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                CosmosMoonColor.copy(alpha = 0.2f * alpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 2.5f
        ),
        center = center,
        radius = radius * 2.5f
    )
    // Moon body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                CosmosMoonColor.copy(alpha = alpha),
                Color(0xFFA0A0A8).copy(alpha = alpha)
            ),
            center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
            radius = radius
        ),
        center = center,
        radius = radius
    )
    // Craters
    drawCircle(
        color = Color(0xFF909098).copy(alpha = 0.4f * alpha),
        radius = radius * 0.15f,
        center = Offset(center.x - radius * 0.3f, center.y + radius * 0.2f)
    )
    drawCircle(
        color = Color(0xFF909098).copy(alpha = 0.3f * alpha),
        radius = radius * 0.1f,
        center = Offset(center.x + radius * 0.25f, center.y - radius * 0.3f)
    )
    // Crescent shadow for depth
    drawCircle(
        color = CosmosCosmicDeep.copy(alpha = 0.5f * alpha),
        radius = radius * 0.85f,
        center = Offset(center.x + radius * 0.35f, center.y + radius * 0.1f)
    )
}

private fun DrawScope.drawEarth(center: Offset, radius: Float, phase: Float, isActive: Boolean) {
    val alpha = if (isActive) 1f else 0.4f
    // Atmospheric halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF4FC3F7).copy(alpha = 0.12f * alpha),
                CosmosEarthColor.copy(alpha = 0.06f * alpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 2.5f
        ),
        center = center,
        radius = radius * 2.5f
    )
    // Ocean
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1565C0).copy(alpha = alpha),
                Color(0xFF0D47A1).copy(alpha = alpha)
            ),
            center = Offset(center.x - radius * 0.15f, center.y - radius * 0.15f),
            radius = radius
        ),
        center = center,
        radius = radius
    )
    // Continents (simplified green patches)
    val continentRotation = phase * 5f
    rotate(continentRotation, center) {
        drawCircle(
            color = CosmosEarthColor.copy(alpha = 0.7f * alpha),
            radius = radius * 0.35f,
            center = Offset(center.x - radius * 0.2f, center.y - radius * 0.15f)
        )
        drawCircle(
            color = Color(0xFF2E7D32).copy(alpha = 0.6f * alpha),
            radius = radius * 0.2f,
            center = Offset(center.x + radius * 0.25f, center.y + radius * 0.2f)
        )
        drawCircle(
            color = CosmosEarthColor.copy(alpha = 0.5f * alpha),
            radius = radius * 0.15f,
            center = Offset(center.x + radius * 0.1f, center.y - radius * 0.35f)
        )
    }
    // Cloud wisps
    drawArc(
        color = Color.White.copy(alpha = 0.2f * alpha),
        startAngle = 30f + phase * 3f,
        sweepAngle = 60f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = radius * 0.08f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSun(center: Offset, radius: Float, phase: Float, isActive: Boolean) {
    val alpha = if (isActive) 1f else 0.4f
    val coronaPulse = (sin(phase * 1.5f) * 0.15f + 0.85f)

    // Corona / outer glow (the most dramatic visual)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                CosmosSunColor.copy(alpha = 0.25f * alpha * coronaPulse),
                CosmosSunCorona.copy(alpha = 0.1f * alpha),
                Color.Transparent
            ),
            center = center,
            radius = radius * 3f
        ),
        center = center,
        radius = radius * 3f
    )
    // Light rays (subtle)
    for (i in 0 until 8) {
        val angle = (i * 45f + phase * 8f)
        val radians = Math.toRadians(angle.toDouble()).toFloat()
        val rayEnd = Offset(
            center.x + cos(radians) * radius * 2.2f * coronaPulse,
            center.y + sin(radians) * radius * 2.2f * coronaPulse
        )
        drawLine(
            color = CosmosSunGlow.copy(alpha = 0.08f * alpha),
            start = Offset(
                center.x + cos(radians) * radius * 1.1f,
                center.y + sin(radians) * radius * 1.1f
            ),
            end = rayEnd,
            strokeWidth = radius * 0.12f,
            cap = StrokeCap.Round
        )
    }
    // Sun body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                CosmosSunGlow.copy(alpha = alpha),
                CosmosSunColor.copy(alpha = alpha),
                CosmosSunCorona.copy(alpha = alpha)
            ),
            center = Offset(center.x - radius * 0.15f, center.y - radius * 0.15f),
            radius = radius
        ),
        center = center,
        radius = radius
    )
    // Hotspot
    drawCircle(
        color = Color.White.copy(alpha = 0.3f * alpha),
        radius = radius * 0.3f,
        center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f)
    )
}

// ── Cosmic Gift Card Banner ──────────────────────────────────────────────────

@Composable
private fun CosmicGiftCardBanner(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        CosmosPrimary.copy(alpha = 0.15f),
                        CosmosCosmicPurple.copy(alpha = 0.12f),
                        CosmosCosmicDeep.copy(alpha = 0.8f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        CosmosPrimary.copy(alpha = 0.5f),
                        CosmosCosmicPurple.copy(alpha = 0.3f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(CosmosPrimary, CosmosCosmicPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎁", fontSize = 20.sp)
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "COSMIC GIFT CARDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CosmosSuccess.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "CURRENCY",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosSuccess,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        "Check card balance or redeem stored credit",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosStarWhite.copy(alpha = 0.85f)
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = CosmosPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Cosmic Gift Card Hub Dialog ──────────────────────────────────────────────

@Composable
fun CosmicGiftCardHubDialog(
    currentTier: MembershipTier,
    onDismiss: () -> Unit,
    onUpgradeTier: (targetTier: MembershipTier) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchCode by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var activeCard by remember { mutableStateOf<GiftCard?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val sampleDemoCards = listOf(
        "COSMOS-LAUNCH-10K" to "₹10,000 Credit",
        "COSMOS-GIFT-50K" to "₹49,999 (Full Moon)",
        "COSMOS-GIFT-100K" to "₹1,00,000 (Earth)",
        "COSMOS-SUPER-200K" to "₹1,99,999 (Full Sun)"
    )

    fun checkCard(codeToCheck: String) {
        val trimmed = codeToCheck.trim().uppercase()
        if (trimmed.isBlank()) {
            validationError = "Please enter a voucher code"
            return
        }
        searchCode = trimmed
        isValidating = true
        validationError = null
        actionMessage = null
        coroutineScope.launch {
            val res = ServiceLocator.giftCardRepository.validateGiftCard(trimmed)
            isValidating = false
            res.fold(
                onSuccess = { card ->
                    activeCard = card
                    validationError = null
                },
                onFailure = { err ->
                    activeCard = null
                    validationError = err.message ?: "Invalid or exhausted gift card"
                }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(CosmosCosmicDeep, Color(0xFF0D0D2B))
                    )
                )
                .border(
                    1.dp,
                    CosmosPrimary.copy(alpha = 0.3f),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎁", fontSize = 24.sp)
                        Column {
                            Text(
                                "COSMIC GIFT CARD HUB",
                                style = MaterialTheme.typography.titleMedium,
                                color = CosmosStarWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Universal Stored-Value Currency",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosPrimary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = CosmosOnSurfaceVariant)
                    }
                }

                HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.15f), thickness = 1.dp)

                // Input field
                OutlinedTextField(
                    value = searchCode,
                    onValueChange = {
                        searchCode = it
                        validationError = null
                    },
                    label = { Text("Gift Card Code", color = CosmosOnSurfaceVariant) },
                    placeholder = { Text("e.g. COSMOS-GIFT-50K", color = CosmosOnSurfaceVariant.copy(alpha = 0.4f)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.CardGiftcard, contentDescription = null, tint = CosmosPrimary)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CosmosStarWhite,
                        unfocusedTextColor = CosmosStarWhite,
                        focusedBorderColor = CosmosPrimary,
                        unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.4f),
                        cursorColor = CosmosPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Check Balance Button
                Button(
                    onClick = { checkCard(searchCode) },
                    enabled = !isValidating && searchCode.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Check Card Balance", fontWeight = FontWeight.Bold)
                    }
                }

                // Error feedback
                if (!validationError.isNullOrBlank()) {
                    Text(
                        validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosError,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Active Card Details Card
                if (activeCard != null) {
                    val card = activeCard!!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        CosmosPrimary.copy(alpha = 0.15f),
                                        CosmosCosmicDeep.copy(alpha = 0.9f)
                                    )
                                )
                            )
                            .border(1.dp, CosmosSuccess.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    card.code,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CosmosStarWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CosmosSuccess.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        card.status.label.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmosSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                card.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = CosmosOnSurfaceVariant
                            )

                            HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Initial Stored Value", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                                    Text(card.formattedInitialValue, style = MaterialTheme.typography.bodyMedium, color = CosmosStarWhite)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Available Balance", style = MaterialTheme.typography.labelSmall, color = CosmosSuccess)
                                    Text(card.formattedBalance, style = MaterialTheme.typography.titleMedium, color = CosmosSuccess, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                "💡 Stored balances are preserved across multi-use purchases automatically.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosStarWhite.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Sample Demo Codes quick picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "SAMPLE TEST VOUCHERS (TAP TO LOAD)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosOnSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    sampleDemoCards.forEach { (code, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CosmosGlass)
                                .border(1.dp, CosmosGlassBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    checkCard(code)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                code,
                                style = MaterialTheme.typography.labelMedium,
                                color = CosmosPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosStarWhite.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Close Button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", color = CosmosOnSurfaceVariant)
                }
            }
        }
    }
}

