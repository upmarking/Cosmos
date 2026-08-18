package app.cosmos.com.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.payment.PaymentManager
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/**
 * A single particle in the celebration explosion.
 */
private data class CelebrationParticle(
    val angle: Float,       // radians, direction of travel
    val speed: Float,       // pixels per animation frame
    val size: Float,        // radius
    val color: Color,
    val delay: Int           // ms delay before appearing
)

/**
 * Full-screen cinematic celebration overlay shown after a successful membership upgrade.
 *
 * Animation sequence:
 * 1. Dark overlay fades in
 * 2. Planet zooms in with spring animation
 * 3. Particle explosion radiates outward
 * 4. "WELCOME TO [TIER]" typewriter reveal
 * 5. Feature checklist staggers in
 * 6. Membership badge appears with spring
 * 7. Auto-dismisses after 4 seconds
 */
@Composable
fun CosmicSuccessOverlay(
    tier: MembershipTier,
    onDismiss: () -> Unit
) {
    val tierColor = Color(tier.color)

    // Animation states
    var showOverlay by remember { mutableStateOf(false) }
    var showPlanet by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showChecks by remember { mutableStateOf(false) }
    var showBadge by remember { mutableStateOf(false) }

    // Generate celebration particles
    val particles = remember {
        List(30) {
            CelebrationParticle(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                speed = Random.nextFloat() * 3f + 1.5f,
                size = Random.nextFloat() * 4f + 1f,
                color = listOf(
                    tierColor,
                    tierColor.copy(alpha = 0.7f),
                    CosmosSunGlow,
                    CosmosStarWhite,
                    CosmosPrimary.copy(alpha = 0.6f)
                ).random(),
                delay = Random.nextInt(300)
            )
        }
    }

    // Planet scale
    val planetScale = remember { Animatable(0f) }

    // Particle expansion progress
    val particleProgress = remember { Animatable(0f) }

    // Orchestrate the animation sequence
    LaunchedEffect(Unit) {
        showOverlay = true
        delay(200)
        showPlanet = true
        planetScale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        showParticles = true
        particleProgress.animateTo(
            1f,
            animationSpec = tween(1200, easing = EaseOutCubic)
        )
        delay(200)
        showText = true
        delay(500)
        showChecks = true
        delay(400)
        showBadge = true
        delay(2500)
        onDismiss()
    }

    // Overlay
    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmosCosmicDeep.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume clicks */ },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // Particle explosion canvas
                if (showParticles) {
                    Canvas(
                        modifier = Modifier
                            .size(200.dp)
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        particles.forEach { particle ->
                            val progress = particleProgress.value
                            val distance = particle.speed * progress * 80f
                            val particleAlpha = (1f - progress).coerceIn(0f, 1f)
                            val px = center.x + cos(particle.angle) * distance
                            val py = center.y + sin(particle.angle) * distance
                            drawCircle(
                                color = particle.color.copy(alpha = particleAlpha * 0.8f),
                                radius = particle.size * (1f - progress * 0.5f),
                                center = Offset(px, py)
                            )
                        }
                    }
                }

                // Planet icon (overlapping the particles)
                if (showPlanet) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-100).dp)
                            .scale(planetScale.value)
                    ) {
                        Text(
                            when (tier) {
                                MembershipTier.ASTEROID -> "☄️"
                                MembershipTier.MOON -> "🌙"
                                MembershipTier.EARTH -> "🌍"
                                MembershipTier.SUN -> "☀️"
                            },
                            fontSize = 64.sp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Title
                AnimatedVisibility(
                    visible = showText,
                    enter = fadeIn(tween(500)) + slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(500)
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "WELCOME TO",
                            style = MaterialTheme.typography.labelMedium,
                            color = tierColor,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            tier.label.uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            color = CosmosStarWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your COSMOS universe has expanded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Staggered feature checklist
                AnimatedVisibility(
                    visible = showChecks,
                    enter = fadeIn(tween(400))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val checks = listOf(
                            "Lifetime access unlocked",
                            "Previous features retained",
                            "${tier.label} features unlocked",
                            "No recurring payments"
                        )
                        checks.forEachIndexed { index, check ->
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(index * 200L)
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(tween(300)) + slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(300)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = CosmosSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        check,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CosmosStarWhite
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Badge reveal
                AnimatedVisibility(
                    visible = showBadge,
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(tween(300))
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(tierColor.copy(alpha = 0.2f), tierColor.copy(alpha = 0.05f))
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "${tier.badge} • Lifetime Member",
                            style = MaterialTheme.typography.labelLarge,
                            color = tierColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
