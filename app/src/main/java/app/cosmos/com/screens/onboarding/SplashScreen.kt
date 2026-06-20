package app.cosmos.com.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.ui.components.CosmosAmbientBackground
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Math.random
import kotlin.math.*

/**
 * A highly creative, premium, hyper-3D interactive startup splash screen.
 * Implements a custom 3D Canvas Projection engine with depth sorting (Painter's Algorithm),
 * dynamic orbital rings, a holographic sphere grid, parallax starfield, custom particles,
 * and reactive drag-and-spring physical interactive mechanics.
 */
@Composable
fun SplashScreen(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // ── INTERACTIVE SPRING ROTATION CONTROLS ──────────────────────────────────
    val dragPitch = remember { Animatable(0f) }
    val dragYaw = remember { Animatable(0f) }

    // ── ANIMATION TIMELINES ───────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "splashSpin")
    
    // Central logo rotation rate
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    // Base entry transition scale
    val entryScale = remember { Animatable(0f) }
    val entryAlpha = remember { Animatable(0f) }

    // Progress bar and state cycler
    val progress = remember { Animatable(0f) }
    var messageIndex by remember { mutableStateOf(0) }

    val messages = listOf(
        "Initializing core systems...",
        "Aligning spatial orbits...",
        "Syncing digital member credentials...",
        "Establishing encrypted uplink...",
        "Optimizing neural connection matrix...",
        "Authentication successful."
    )

    // Trigger startup sequences
    LaunchedEffect(Unit) {
        // Entry zoom & fade
        launch {
            entryScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            entryAlpha.animateTo(1f, tween(1200, easing = EaseOutQuad))
        }

        // Loading progression
        launch {
            progress.animateTo(1f, tween(3200, easing = FastOutSlowInEasing))
            delay(150)
            onAnimationFinished()
        }

        // Message cycling
        launch {
            while (messageIndex < messages.size - 1) {
                delay(600)
                messageIndex++
            }
        }
    }

    // ── STARFIELD PARALLAX ───────────────────────────────────────────────────
    val stars = remember {
        List(85) {
            Star(
                x = (random() * 2000 - 1000).toFloat(),
                y = (random() * 2000 - 1000).toFloat(),
                z = (random() * 980 + 20).toFloat(),
                size = (random() * 3.5 + 1).toFloat(),
                alpha = (random() * 0.7 + 0.3).toFloat()
            )
        }
    }

    // ── DYNAMIC PARTICLE SYSTEM ──────────────────────────────────────────────
    var particles by remember { mutableStateOf(emptyList<CosmicParticle>()) }
    var particleCounter by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            // Update positions
            particles = particles.mapNotNull { p ->
                p.distance += p.speed
                p.alpha = (1f - (p.distance / 250f)).coerceIn(0f, 1f)
                if (p.distance < 250f && p.alpha > 0.05f) p else null
            }

            // Spawn new particles
            if (particles.size < 35 && random() < 0.2) {
                val pColor = if (random() < 0.5) CosmosPrimary else CosmosSecondary
                val newParticle = CosmicParticle(
                    id = particleCounter++,
                    angle = (random() * 360f).toFloat(),
                    speed = (random() * 1.5f + 1f).toFloat(),
                    distance = (random() * 12f + 5f).toFloat(),
                    size = (random() * 3.5f + 1.5f).toFloat(),
                    color = pColor
                )
                particles = particles + newParticle
            }
            delay(16)
        }
    }

    // Combine idle animations + drag controls
    val totalPitch = 22f + dragPitch.value
    val totalYaw = spinAngle + dragYaw.value
    val totalRoll = spinAngle * 0.25f

    CosmosAmbientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top branding text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = "✦  E N T E R   T H E   C O S M O S  ✦",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmosPrimary.copy(alpha = 0.6f * entryAlpha.value),
                    letterSpacing = 2.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Central 3D Interactive Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    // Scale down drag to make rotation feel solid and heavy
                                    dragPitch.snapTo(dragPitch.value - dragAmount.y * 0.3f)
                                    dragYaw.snapTo(dragYaw.value + dragAmount.x * 0.3f)
                                }
                            },
                            onDragEnd = {
                                // Spring back to default orientation on release
                                coroutineScope.launch {
                                    dragPitch.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                                coroutineScope.launch {
                                    dragYaw.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val width = size.width
                    val height = size.height
                    val cameraDistance = 900f
                    val scaleFactor = entryScale.value

                    // Draw 3D starfield with drag-responsive parallax
                    stars.forEach { star ->
                        val parallaxX = dragYaw.value * (1f - star.z / 1200f) * 0.7f
                        val parallaxY = -dragPitch.value * (1f - star.z / 1200f) * 0.7f

                        val finalX = width / 2 + star.x + parallaxX
                        val finalY = height / 2 + star.y + parallaxY

                        // Wrap coordinates within screen bounds
                        val wrappedX = ((finalX % width) + width) % width
                        val wrappedY = ((finalY % height) + height) % height

                        drawCircle(
                            color = Color.White.copy(alpha = star.alpha * 0.35f * entryAlpha.value),
                            radius = star.size * (1f - star.z / 1200f * 0.6f) * scaleFactor,
                            center = Offset(wrappedX, wrappedY)
                        )
                    }

                    // Radii in pixels
                    val sphereRadius = 45.dp.toPx() * scaleFactor
                    val ring1Radius = 78.dp.toPx() * scaleFactor
                    val ring2Radius = 115.dp.toPx() * scaleFactor
                    val ring3Radius = 145.dp.toPx() * scaleFactor

                    if (scaleFactor > 0.01f) {
                        val drawables = mutableListOf<Renderable>()

                        // 1. Core Sphere Glow
                        drawables.add(
                            Renderable.SphereGlow(
                                depth = -100f,
                                center = Offset(width / 2, height / 2),
                                radius = sphereRadius * 2.8f,
                                color = CosmosPrimary
                            )
                        )

                        // 2. Core Sphere Solid Body (placed at depth = 0)
                        drawables.add(
                            Renderable.SphereCore(
                                depth = 0f,
                                center = Offset(width / 2, height / 2),
                                radius = sphereRadius
                            )
                        )

                        // 3. Orbits/Rings (generated as line segments)
                        // Inner ring (Ring 1) — tilted X-axis
                        drawables.addAll(
                            generateRingSegments(
                                radius = ring1Radius,
                                pitch = totalPitch + 25f,
                                yaw = totalYaw * 1.4f,
                                roll = totalRoll,
                                color = CosmosTertiary,
                                strokeWidth = 1.2.dp.toPx(),
                                alpha = 0.5f * entryAlpha.value,
                                width = width,
                                height = height,
                                cameraDistance = cameraDistance
                            )
                        )

                        // Middle ring (Ring 2) — tilted Y-axis
                        drawables.addAll(
                            generateRingSegments(
                                radius = ring2Radius,
                                pitch = totalPitch - 15f,
                                yaw = -totalYaw * 0.8f,
                                roll = totalRoll * 0.5f,
                                color = CosmosPrimary,
                                strokeWidth = 2.0.dp.toPx(),
                                alpha = 0.85f * entryAlpha.value,
                                width = width,
                                height = height,
                                cameraDistance = cameraDistance
                            )
                        )

                        // Outer ring (Ring 3) — low tilt
                        drawables.addAll(
                            generateRingSegments(
                                radius = ring3Radius,
                                pitch = totalPitch + 8f,
                                yaw = totalYaw * 0.4f,
                                roll = -totalRoll * 0.3f,
                                color = CosmosSecondary,
                                strokeWidth = 1.6.dp.toPx(),
                                alpha = 0.6f * entryAlpha.value,
                                width = width,
                                height = height,
                                cameraDistance = cameraDistance
                            )
                        )

                        // 4. Holographic grid lines on the rotating sphere
                        // Latitude lines
                        for (lat in listOf(-60f, -30f, 0f, 30f, 60f)) {
                            drawables.addAll(
                                generateSphereLatitudeSegments(
                                    sphereRadius = sphereRadius,
                                    phiDegrees = lat,
                                    pitch = totalPitch,
                                    yaw = totalYaw * 0.5f,
                                    roll = totalRoll * 0.2f,
                                    color = CosmosPrimaryContainer,
                                    strokeWidth = 1f.dp.toPx(),
                                    sphereAlpha = 0.55f * entryAlpha.value,
                                    width = width,
                                    height = height,
                                    cameraDistance = cameraDistance
                                )
                            )
                        }

                        // Longitude lines
                        for (lng in listOf(0f, 45f, 90f, 135f)) {
                            drawables.addAll(
                                generateSphereLongitudeSegments(
                                    sphereRadius = sphereRadius,
                                    lambdaDegrees = lng,
                                    pitch = totalPitch,
                                    yaw = totalYaw * 0.5f,
                                    roll = totalRoll * 0.2f,
                                    color = CosmosPrimaryContainer,
                                    strokeWidth = 1f.dp.toPx(),
                                    sphereAlpha = 0.55f * entryAlpha.value,
                                    width = width,
                                    height = height,
                                    cameraDistance = cameraDistance
                                )
                            )
                        }

                        // 5. Orbiting moon/satellite on Ring 2
                        val satelliteAngle = (spinAngle * 2.2f) % 360f
                        val satRad = Math.toRadians(satelliteAngle.toDouble())
                        val satLocalPos = Point3D(
                            ring2Radius * cos(satRad).toFloat(),
                            ring2Radius * sin(satRad).toFloat(),
                            0f
                        ).rotate(totalPitch - 15f, -totalYaw * 0.8f, totalRoll * 0.5f)
                        
                        val satDepth = satLocalPos.z
                        val satOffset = satLocalPos.project(width, height, cameraDistance)
                        drawables.add(
                            Renderable.Satellite(
                                depth = satDepth,
                                center = satOffset,
                                radius = 6.dp.toPx() * scaleFactor,
                                color = CosmosPrimary,
                                glowColor = CosmosPrimaryContainer
                            )
                        )

                        // 6. 3D Particles swirling in the system
                        particles.forEach { p ->
                            val pRad = Math.toRadians(p.angle.toDouble())
                            // Swirl position rotated into 3D
                            val pPos = Point3D(
                                p.distance * cos(pRad).toFloat(),
                                p.distance * sin(pRad).toFloat(),
                                0f
                            ).rotate(totalPitch, totalYaw, totalRoll)

                            val pOffset = pPos.project(width, height, cameraDistance)
                            val pScale = cameraDistance / if (abs(cameraDistance - pPos.z) < 0.1f) 0.1f else (cameraDistance - pPos.z)
                            
                            drawables.add(
                                Renderable.ParticleNode(
                                    depth = pPos.z,
                                    center = pOffset,
                                    radius = p.size * pScale * scaleFactor,
                                    color = p.color,
                                    alpha = p.alpha * entryAlpha.value
                                )
                            )
                        }

                        // ── PAINTER'S ALGORITHM DEPTH SORT ──────────────────────────
                        drawables.sort()

                        // Render sorted 3D elements
                        drawables.forEach { item ->
                            when (item) {
                                is Renderable.LineSegment -> {
                                    drawLine(
                                        color = item.color,
                                        start = item.start,
                                        end = item.end,
                                        strokeWidth = item.strokeWidth,
                                        alpha = item.alpha,
                                        cap = StrokeCap.Round
                                    )
                                }
                                is Renderable.SphereGlow -> {
                                    val radiusValue = max(1f, item.radius)
                                    val radialBrush = Brush.radialGradient(
                                        colors = listOf(item.color.copy(alpha = 0.45f * entryAlpha.value), Color.Transparent),
                                        center = item.center,
                                        radius = radiusValue
                                    )
                                    drawCircle(
                                        brush = radialBrush,
                                        center = item.center,
                                        radius = radiusValue
                                    )
                                }
                                is Renderable.SphereCore -> {
                                    val radiusValue = max(1f, item.radius)
                                    // Deep 3D radial gradient representing specular light highlight on the planet
                                    val specularBrush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White,                        // Spot reflection highlight
                                            CosmosPrimaryFixed,                 // Light reflection color
                                            CosmosPrimary,                      // Mid-tone indigo
                                            CosmosGradientStart,                // Darker base shade
                                            CosmosBackground                    // Shadow termination color
                                        ),
                                        center = Offset(item.center.x - radiusValue * 0.35f, item.center.y - radiusValue * 0.35f),
                                        radius = radiusValue * 1.35f
                                    )
                                    drawCircle(
                                        brush = specularBrush,
                                        center = item.center,
                                        radius = radiusValue
                                    )
                                    // Holographic atmosphere edge ring
                                    drawCircle(
                                        color = CosmosPrimary.copy(alpha = 0.5f * entryAlpha.value),
                                        center = item.center,
                                        radius = radiusValue,
                                        style = Stroke(width = 1.2.dp.toPx())
                                    )
                                }
                                is Renderable.Satellite -> {
                                    val radiusValue = max(1f, item.radius)
                                    // Radial glow of satellite
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(item.glowColor.copy(alpha = 0.55f * entryAlpha.value), Color.Transparent),
                                            center = item.center,
                                            radius = radiusValue * 2.8f
                                        ),
                                        center = item.center,
                                        radius = radiusValue * 2.8f
                                    )
                                    // Solid core
                                    drawCircle(
                                        color = item.color,
                                        center = item.center,
                                        radius = radiusValue
                                    )
                                }
                                is Renderable.ParticleNode -> {
                                    if (item.radius > 0.05f) {
                                        drawCircle(
                                            color = item.color,
                                            radius = item.radius,
                                            center = item.center,
                                            alpha = item.alpha
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom loading details & title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .fillMaxWidth()
            ) {
                // Interactive clue
                Text(
                    text = "DRAG TO ROTATE DECK",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmosOnSurfaceVariant.copy(alpha = 0.45f * entryAlpha.value),
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // App Title
                Text(
                    text = "COSMOS",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = CosmosOnBackground,
                    letterSpacing = (-1.2).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                // App Subtitle
                Text(
                    text = "Digital Private Member's Club",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    color = CosmosOnSurfaceVariant.copy(alpha = 0.8f * entryAlpha.value),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(48.dp))

                // Premium neon progress indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(CosmosOutlineVariant.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.value)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        CosmosGradientStart,
                                        CosmosGradientEnd,
                                        CosmosPrimary
                                    )
                                )
                            )
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Active loading state cycler
                Text(
                    text = messages[messageIndex],
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = CosmosPrimary.copy(alpha = 0.8f * entryAlpha.value),
                    textAlign = TextAlign.Center,
                    minLines = 1
                )
            }
        }
    }
}

// ── 3D GEOMETRY DATA STRUCTURES & MATH FUNCTIONS ─────────────────────────────

private data class Point3D(val x: Float, val y: Float, val z: Float) {
    fun rotateX(angleDegrees: Float): Point3D {
        val rad = Math.toRadians(angleDegrees.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        return Point3D(x, y * cos - z * sin, y * sin + z * cos)
    }

    fun rotateY(angleDegrees: Float): Point3D {
        val rad = Math.toRadians(angleDegrees.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        return Point3D(x * cos + z * sin, y, -x * sin + z * cos)
    }

    fun rotateZ(angleDegrees: Float): Point3D {
        val rad = Math.toRadians(angleDegrees.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        return Point3D(x * cos - y * sin, x * sin + y * cos, z)
    }

    fun rotate(pitch: Float, yaw: Float, roll: Float): Point3D {
        return rotateZ(roll).rotateX(pitch).rotateY(yaw)
    }

    fun project(width: Float, height: Float, cameraDistance: Float): Offset {
        val denom = cameraDistance - z
        val scale = cameraDistance / if (abs(denom) < 0.1f) 0.1f else denom
        return Offset(width / 2 + x * scale, height / 2 + y * scale)
    }
}

private sealed class Renderable(val depth: Float) : Comparable<Renderable> {
    override fun compareTo(other: Renderable): Int = this.depth.compareTo(other.depth)

    class LineSegment(
        depth: Float,
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float,
        val alpha: Float
    ) : Renderable(depth)

    class SphereGlow(
        depth: Float,
        val center: Offset,
        val radius: Float,
        val color: Color
    ) : Renderable(depth)

    class SphereCore(
        depth: Float,
        val center: Offset,
        val radius: Float
    ) : Renderable(depth)

    class Satellite(
        depth: Float,
        val center: Offset,
        val radius: Float,
        val color: Color,
        val glowColor: Color
    ) : Renderable(depth)

    class ParticleNode(
        depth: Float,
        val center: Offset,
        val radius: Float,
        val color: Color,
        val alpha: Float
    ) : Renderable(depth)
}

// Generate Ring segments in 3D
private fun generateRingSegments(
    radius: Float,
    pitch: Float,
    yaw: Float,
    roll: Float,
    color: Color,
    strokeWidth: Float,
    alpha: Float,
    width: Float,
    height: Float,
    cameraDistance: Float,
    segmentsCount: Int = 90
): List<Renderable.LineSegment> {
    val list = ArrayList<Renderable.LineSegment>(segmentsCount)
    val step = 360f / segmentsCount
    for (i in 0 until segmentsCount) {
        val angleA = i * step
        val angleB = (i + 1) * step

        val radA = Math.toRadians(angleA.toDouble())
        val radB = Math.toRadians(angleB.toDouble())

        val ptA = Point3D(radius * cos(radA).toFloat(), radius * sin(radA).toFloat(), 0f)
            .rotate(pitch, yaw, roll)
        val ptB = Point3D(radius * cos(radB).toFloat(), radius * sin(radB).toFloat(), 0f)
            .rotate(pitch, yaw, roll)

        val depth = (ptA.z + ptB.z) / 2f
        val startOffset = ptA.project(width, height, cameraDistance)
        val endOffset = ptB.project(width, height, cameraDistance)

        list.add(
            Renderable.LineSegment(
                depth = depth,
                start = startOffset,
                end = endOffset,
                color = color,
                strokeWidth = strokeWidth,
                alpha = alpha
            )
        )
    }
    return list
}

// Generate latitude lines for holographic 3D globe
private fun generateSphereLatitudeSegments(
    sphereRadius: Float,
    phiDegrees: Float,
    pitch: Float,
    yaw: Float,
    roll: Float,
    color: Color,
    strokeWidth: Float,
    sphereAlpha: Float,
    width: Float,
    height: Float,
    cameraDistance: Float,
    segmentsCount: Int = 45
): List<Renderable.LineSegment> {
    val list = ArrayList<Renderable.LineSegment>(segmentsCount)
    val phiRad = Math.toRadians(phiDegrees.toDouble())
    val yLocal = sphereRadius * sin(phiRad).toFloat()
    val rLat = sphereRadius * cos(phiRad).toFloat()
    val step = 360f / segmentsCount

    for (i in 0 until segmentsCount) {
        val thetaA = i * step
        val thetaB = (i + 1) * step

        val radA = Math.toRadians(thetaA.toDouble())
        val radB = Math.toRadians(thetaB.toDouble())

        val ptA = Point3D(rLat * cos(radA).toFloat(), yLocal, rLat * sin(radA).toFloat())
            .rotate(pitch, yaw, roll)
        val ptB = Point3D(rLat * cos(radB).toFloat(), yLocal, rLat * sin(radB).toFloat())
            .rotate(pitch, yaw, roll)

        val depth = (ptA.z + ptB.z) / 2f
        // Dynamic transparency: fade lines on the back of the planet (Z < 0)
        val finalAlpha = if (depth < 0) 0.08f * sphereAlpha else 0.5f * sphereAlpha
        
        val startOffset = ptA.project(width, height, cameraDistance)
        val endOffset = ptB.project(width, height, cameraDistance)

        list.add(
            Renderable.LineSegment(
                depth = depth,
                start = startOffset,
                end = endOffset,
                color = color,
                strokeWidth = strokeWidth,
                alpha = finalAlpha
            )
        )
    }
    return list
}

// Generate longitude lines for holographic 3D globe
private fun generateSphereLongitudeSegments(
    sphereRadius: Float,
    lambdaDegrees: Float,
    pitch: Float,
    yaw: Float,
    roll: Float,
    color: Color,
    strokeWidth: Float,
    sphereAlpha: Float,
    width: Float,
    height: Float,
    cameraDistance: Float,
    segmentsCount: Int = 45
): List<Renderable.LineSegment> {
    val list = ArrayList<Renderable.LineSegment>(segmentsCount)
    val lambdaRad = Math.toRadians(lambdaDegrees.toDouble())
    val cosL = cos(lambdaRad).toFloat()
    val sinL = sin(lambdaRad).toFloat()
    val step = 360f / segmentsCount

    for (i in 0 until segmentsCount) {
        val thetaA = i * step
        val thetaB = (i + 1) * step

        val radA = Math.toRadians(thetaA.toDouble())
        val radB = Math.toRadians(thetaB.toDouble())

        val ptA = Point3D(
            sphereRadius * cos(radA).toFloat() * cosL,
            sphereRadius * sin(radA).toFloat(),
            sphereRadius * cos(radA).toFloat() * sinL
        ).rotate(pitch, yaw, roll)

        val ptB = Point3D(
            sphereRadius * cos(radB).toFloat() * cosL,
            sphereRadius * sin(radB).toFloat(),
            sphereRadius * cos(radB).toFloat() * sinL
        ).rotate(pitch, yaw, roll)

        val depth = (ptA.z + ptB.z) / 2f
        val finalAlpha = if (depth < 0) 0.08f * sphereAlpha else 0.5f * sphereAlpha
        
        val startOffset = ptA.project(width, height, cameraDistance)
        val endOffset = ptB.project(width, height, cameraDistance)

        list.add(
            Renderable.LineSegment(
                depth = depth,
                start = startOffset,
                end = endOffset,
                color = color,
                strokeWidth = strokeWidth,
                alpha = finalAlpha
            )
        )
    }
    return list
}

private data class Star(
    val x: Float,
    val y: Float,
    val z: Float,
    val size: Float,
    val alpha: Float
)

private data class CosmicParticle(
    val id: Int,
    val angle: Float,
    val speed: Float,
    var distance: Float,
    val size: Float,
    val color: Color,
    var alpha: Float = 1f
)