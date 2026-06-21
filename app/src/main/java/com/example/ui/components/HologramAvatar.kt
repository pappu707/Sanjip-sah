package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.withFrameNanos

class HologramParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 0f,
    var life: Float = 0f,
    var maxLife: Float = 0f,
    var alpha: Float = 0f
)

@Composable
fun HologramParticleSystem(
    modifier: Modifier = Modifier,
    glowColor: Color,
    isActive: Boolean
) {
    val particles = remember { Array(100) { HologramParticle() } }
    var tick by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            var lastTime = withFrameNanos { it }
            while (true) {
                withFrameNanos { newTime ->
                    val dt = (newTime - lastTime) / 1_000_000_000f
                    lastTime = newTime
                    
                    particles.forEach { p ->
                        if (p.life <= 0) {
                            val angle = Random.nextFloat() * Math.PI * 2
                            val dist = Random.nextFloat() * 0.2f + 0.3f
                            p.x = 0.5f + (kotlin.math.cos(angle) * dist).toFloat()
                            p.y = 0.5f + (kotlin.math.sin(angle) * dist).toFloat()
                            p.vx = (p.x - 0.5f) * (Random.nextFloat() * 150f + 50f)
                            p.vy = (p.y - 0.5f) * (Random.nextFloat() * 150f + 50f) - 40f
                            p.life = Random.nextFloat() * 1.5f + 0.5f
                            p.maxLife = p.life
                            p.radius = Random.nextFloat() * 4f + 2f
                            p.alpha = Random.nextFloat() * 0.6f + 0.4f
                        } else {
                            p.x += (p.vx * dt) / 300f
                            p.y += (p.vy * dt) / 300f
                            p.life -= dt
                        }
                    }
                    tick = newTime.toFloat()
                }
            }
        }
    }

    if (isActive) {
        Canvas(modifier = modifier) {
            val currentTick = tick 
            val w = size.width
            val h = size.height
            particles.forEach { p ->
                if (p.life > 0) {
                    val lifeRatio = p.life / p.maxLife
                    val currentAlpha = p.alpha * kotlin.math.sin(lifeRatio * Math.PI).toFloat().coerceIn(0f, 1f)
                    drawCircle(
                        color = glowColor.copy(alpha = currentAlpha),
                        radius = p.radius,
                        center = androidx.compose.ui.geometry.Offset(p.x * w, p.y * h)
                    )
                }
            }
        }
    }
}

enum class AvatarState {
    IDLE,
    THINKING,
    SPEAKING,
    WAVING
}

@Composable
fun HologramAvatar(
    avatarState: AvatarState,
    modifier: Modifier = Modifier,
    avatarStyle: String = "Sci-Fi Cyber Suit"
) {
    // 1. Infinite Animations
    val infiniteTransition = rememberInfiniteTransition(label = "HologramLoop")

    // Breathing scale animation
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    // HUD rotation animation
    val hudRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Reverse HUD rotation
    val innerHudRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RevRotation"
    )

    // Glowing color changing based on State
    val glowColor by animateColorAsState(
        targetValue = when (avatarState) {
            AvatarState.IDLE -> Color(0xFF00E5FF) // Cyber cyan
            AvatarState.THINKING -> Color(0xFFFF007F) // Intense magenta/neon pink
            AvatarState.SPEAKING -> Color(0xFF39FF14) // Electric green
            AvatarState.WAVING -> Color(0xFFD4AF37) // Gold
        },
        animationSpec = tween(500),
        label = "GlowColor"
    )

    // Dynamic wave progression for speaking soundwaves
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveOffset"
    )

    Box(
        modifier = modifier
            .size(190.dp)
            .graphicsLayer {
                scaleX = breathingScale
                scaleY = breathingScale
            },
        contentAlignment = Alignment.Center
    ) {
        // A. Multi-layered background neon rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw outer neon glow ring (blending)
            drawCircle(
                color = glowColor.copy(alpha = 0.12f),
                radius = size.minDimension / 2f
            )
            // Draw structural outer HUD ticks
            drawCircle(
                color = glowColor.copy(alpha = 0.35f),
                radius = size.minDimension / 2.1f,
                style = Stroke(width = 2f)
            )
        }

        // B. Holographic Particle Simulation System
        HologramParticleSystem(
            modifier = Modifier.fillMaxSize(),
            glowColor = glowColor,
            isActive = avatarState == AvatarState.SPEAKING || avatarState == AvatarState.THINKING
        )

        // C. Dynamic Rotating HUD Rings
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = hudRotation
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Drawing sci-fi dashes for outer ring
                drawArc(
                    color = glowColor,
                    startAngle = 0f,
                    sweepAngle = 45f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = glowColor,
                    startAngle = 90f,
                    sweepAngle = 30f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = glowColor.copy(alpha = 0.5f),
                    startAngle = 180f,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawArc(
                    color = glowColor,
                    startAngle = 270f,
                    sweepAngle = 45f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .graphicsLayer {
                    rotationZ = innerHudRotation
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Drawing secondary inner rings
                drawArc(
                    color = glowColor.copy(alpha = 0.7f),
                    startAngle = 45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawArc(
                    color = glowColor.copy(alpha = 0.7f),
                    startAngle = 200f,
                    sweepAngle = 50f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // C. Core Character Frame
        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .border(2.dp, glowColor.copy(alpha = 0.7f), CircleShape)
                .border(6.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.Black, Color(0xFF030D1B)))),
            contentAlignment = Alignment.Center
        ) {
            // Displays generated beautiful avatar image
            Image(
                painter = painterResource(id = R.drawable.img_ava_avatar_custom_1782013638273),
                contentDescription = "Ava Portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // State-specific sci-fi overlays (e.g. blinking scanners/wave overlay)
            if (avatarState == AvatarState.THINKING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(glowColor.copy(alpha = 0.15f))
                ) {
                    // Scanning line sweep
                    val sweepProgress by infiniteTransition.animateFloat(
                        initialValue = -0.5f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1800, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "ScanSweep"
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * sweepProgress
                        if (y in 0f..size.height) {
                            drawLine(
                                color = glowColor,
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(size.width, y),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                }
            }

            if (avatarState == AvatarState.SPEAKING) {
                // Procedural overlay grid
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF39FF14).copy(alpha = 0.08f),
                        radius = size.minDimension / 2f
                    )
                }
            }
        }

        // D. Soundwaves (Lip Sync Simulation)
        if (avatarState == AvatarState.SPEAKING) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(80.dp)
                    .align(Alignment.Center)
            ) {
                val halfHeight = size.height / 2f
                val pointsCount = 40
                val spacing = size.width / pointsCount

                for (i in 0 until pointsCount) {
                    val x = i * spacing
                    // Formula to make sine wave ripple beautifully centered
                    val xFactor = sin((i.toFloat() / pointsCount) * Math.PI).toFloat()
                    val waveValue = sin((i * 0.4f) + (waveOffset * 0.2f))
                    // Height is dynamically scale-breathes
                    val barHeight = halfHeight * waveValue * xFactor * 0.8f

                    drawLine(
                        color = Color(0xFF39FF14).copy(alpha = 0.85f),
                        start = androidx.compose.ui.geometry.Offset(x, halfHeight - barHeight),
                        end = androidx.compose.ui.geometry.Offset(x, halfHeight + barHeight),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
        }

        // E. State Name Badge HUD Overlay below
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (14).dp)
                .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                .border(1.dp, glowColor.copy(alpha = 0.8f), CircleShape)
                .padding(vertical = 4.dp, horizontal = 14.dp)
        ) {
            Text(
                text = when (avatarState) {
                    AvatarState.IDLE -> "AVA: ONLINE"
                    AvatarState.THINKING -> "AVA: COGNITIVE PROCESS"
                    AvatarState.SPEAKING -> "AVA: VOCAL UNIT ACTIVE"
                    AvatarState.WAVING -> "AVA: WELCOME GESTURE"
                },
                color = glowColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}
