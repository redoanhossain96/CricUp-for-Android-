package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PredictionCelebrationStyle
import com.example.data.model.PredictionWinCelebration
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class CelebrationParticle(
    val xFraction: Float,
    val yFraction: Float,
    val angleRad: Float,
    val speed: Float,
    val radius: Float,
    val color: Color,
    val rotationSpeed: Float
)

@Composable
fun PredictionCelebrationOverlay(
    celebration: PredictionWinCelebration?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    var activeItem by remember { mutableStateOf<PredictionWinCelebration?>(null) }
    val progress = remember { Animatable(0f) }

    val particles = remember(activeItem?.id) {
        val rng = Random(activeItem?.id?.hashCode() ?: 42)
        val palette = listOf(
            Color(0xFF10B981),
            Color(0xFFFACC15),
            Color(0xFF38BDF8),
            Color(0xFFF97316),
            Color(0xFFEC4899),
            Color(0xFFA855F7)
        )
        List(44) { idx ->
            CelebrationParticle(
                xFraction = rng.nextFloat(),
                yFraction = rng.nextFloat() * 0.45f,
                angleRad = (rng.nextFloat() * 2f * PI.toFloat()),
                speed = 0.25f + rng.nextFloat() * 0.75f,
                radius = 5f + rng.nextFloat() * 9f,
                color = palette[idx % palette.size],
                rotationSpeed = (rng.nextFloat() - 0.5f) * 360f
            )
        }
    }

    LaunchedEffect(celebration?.id) {
        if (celebration != null) {
            activeItem = celebration
            visible = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2800, easing = FastOutSlowInEasing)
            )
            delay(450L)
            visible = false
            delay(250L)
            onDismiss()
        } else {
            visible = false
        }
    }

    val badgeScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.75f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "celebration_badge_scale"
    )

    AnimatedVisibility(
        visible = visible && activeItem != null,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.86f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        activeItem?.let { item ->
            val style = item.style
            val (gradientColors, accentColor, heroEmoji, styleTag) = when (style) {
                PredictionCelebrationStyle.CONFETTI_TROPHY -> CelebrationThemeSpec(
                    listOf(Color(0xFF064E3B), Color(0xFF047857), Color(0xFF065F46)),
                    Color(0xFFFACC15),
                    "🏆",
                    "CONFETTI WINNER"
                )
                PredictionCelebrationStyle.FIREWORKS_STARBURST -> CelebrationThemeSpec(
                    listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF1E3A8A)),
                    Color(0xFF38BDF8),
                    "🎆",
                    "STARBURST HIT"
                )
                PredictionCelebrationStyle.ROCKET_STREAK -> CelebrationThemeSpec(
                    listOf(Color(0xFF431407), Color(0xFF9A3412), Color(0xFF7C2D12)),
                    Color(0xFFFB923C),
                    "🚀",
                    "SIXER ROCKET"
                )
                PredictionCelebrationStyle.CROWN_SPARKLE -> CelebrationThemeSpec(
                    listOf(Color(0xFF3B0764), Color(0xFF6B21A8), Color(0xFF581C87)),
                    Color(0xFFE879F9),
                    "👑",
                    "CRICKET GURU"
                )
                PredictionCelebrationStyle.STADIUM_FLASH -> CelebrationThemeSpec(
                    listOf(Color(0xFF0F172A), Color(0xFF0F766E), Color(0xFF115E59)),
                    Color(0xFF34D399),
                    "🌟",
                    "STADIUM CHAMPION"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .testTag("prediction_celebration_overlay"),
                contentAlignment = Alignment.Center
            ) {
                // Full-screen celebratory Canvas particle layer tailored to each win style
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = progress.value
                    when (style) {
                        PredictionCelebrationStyle.CONFETTI_TROPHY -> drawConfettiBurst(t, particles)
                        PredictionCelebrationStyle.FIREWORKS_STARBURST -> drawFireworksStarburst(t, particles)
                        PredictionCelebrationStyle.ROCKET_STREAK -> drawRocketStreak(t, particles)
                        PredictionCelebrationStyle.CROWN_SPARKLE -> drawCrownSparkle(t, particles, accentColor)
                        PredictionCelebrationStyle.STADIUM_FLASH -> drawStadiumFlash(t, particles, accentColor)
                    }
                }

                // Centered celebratory congratulation card
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .scale(badgeScale)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .testTag("prediction_celebration_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(2.dp, accentColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(gradientColors))
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = styleTag,
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.4.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f))
                                .border(2.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = heroEmoji, fontSize = 34.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.subtitle,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            color = accentColor,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "+${item.pointsEarned} পয়েন্ট প্রোফাইলে যুক্ত হয়েছে!",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("prediction_celebration_dismiss_btn")
                        ) {
                            Text("চমৎকার! 👍", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class CelebrationThemeSpec(
    val gradient: List<Color>,
    val accent: Color,
    val emoji: String,
    val tag: String
)

private fun DrawScope.drawConfettiBurst(t: Float, particles: List<CelebrationParticle>) {
    val alpha = (1f - t * 0.4f).coerceIn(0f, 1f)
    particles.forEach { p ->
        val px = size.width * p.xFraction + sin(t * 8f + p.angleRad) * 42f
        val py = (size.height * p.yFraction) + t * size.height * (0.65f + p.speed * 0.55f)
        rotate(degrees = t * p.rotationSpeed, pivot = Offset(px, py)) {
            drawRect(
                color = p.color.copy(alpha = alpha),
                topLeft = Offset(px - p.radius, py - p.radius * 0.5f),
                size = Size(p.radius * 2f, p.radius * 1.1f)
            )
        }
    }
}

private fun DrawScope.drawFireworksStarburst(t: Float, particles: List<CelebrationParticle>) {
    val centers = listOf(
        Offset(size.width * 0.25f, size.height * 0.25f),
        Offset(size.width * 0.78f, size.height * 0.28f),
        Offset(size.width * 0.5f, size.height * 0.72f)
    )
    centers.forEachIndexed { idx, center ->
        val localT = ((t * 1.4f) - idx * 0.16f).coerceIn(0f, 1f)
        if (localT > 0f) {
            val maxR = size.minDimension * 0.34f * localT
            val alpha = (1f - localT).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.35f),
                radius = maxR * 0.75f,
                center = center,
                style = Stroke(width = 3f)
            )
            particles.take(15).forEachIndexed { pIdx, p ->
                val ang = (pIdx / 15f) * 2f * PI.toFloat() + idx
                val dist = maxR * (0.4f + p.speed * 0.6f)
                val pos = Offset(center.x + cos(ang) * dist, center.y + sin(ang) * dist)
                drawLine(
                    color = p.color.copy(alpha = alpha),
                    start = Offset(center.x + cos(ang) * dist * 0.55f, center.y + sin(ang) * dist * 0.55f),
                    end = pos,
                    strokeWidth = 4f
                )
                drawCircle(color = p.color.copy(alpha = alpha), radius = p.radius * 0.65f, center = pos)
            }
        }
    }
}

private fun DrawScope.drawRocketStreak(t: Float, particles: List<CelebrationParticle>) {
    val alpha = (1f - t * 0.5f).coerceIn(0f, 1f)
    particles.forEachIndexed { idx, p ->
        val startX = size.width * (0.12f + (idx % 7) * 0.13f)
        val startY = size.height * 0.95f
        val rise = t * size.height * (0.75f + p.speed * 0.35f)
        val curY = startY - rise
        val curX = startX + sin(t * 10f + idx) * 24f
        drawLine(
            color = p.color.copy(alpha = alpha * 0.75f),
            start = Offset(curX, curY + 46f),
            end = Offset(curX, curY),
            strokeWidth = 5f
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = p.radius * 0.6f,
            center = Offset(curX, curY)
        )
    }
}

private fun DrawScope.drawCrownSparkle(t: Float, particles: List<CelebrationParticle>, accent: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val alpha = (1f - t * 0.45f).coerceIn(0f, 1f)
    val haloRadius = size.minDimension * (0.28f + 0.22f * t)
    drawCircle(
        color = accent.copy(alpha = alpha * 0.45f),
        radius = haloRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 6f)
    )
    particles.forEachIndexed { idx, p ->
        val ang = p.angleRad + t * 2.4f
        val r = haloRadius * (0.5f + p.speed * 0.85f)
        val px = cx + cos(ang) * r
        val py = cy + sin(ang) * r
        val starPath = Path().apply {
            val s = p.radius * 1.1f
            moveTo(px, py - s)
            lineTo(px + s * 0.35f, py - s * 0.35f)
            lineTo(px + s, py)
            lineTo(px + s * 0.35f, py + s * 0.35f)
            lineTo(px, py + s)
            lineTo(px - s * 0.35f, py + s * 0.35f)
            lineTo(px - s, py)
            lineTo(px - s * 0.35f, py - s * 0.35f)
            close()
        }
        drawPath(starPath, color = if (idx % 2 == 0) accent.copy(alpha = alpha) else p.color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawStadiumFlash(t: Float, particles: List<CelebrationParticle>, accent: Color) {
    val alpha = (1f - t * 0.45f).coerceIn(0f, 1f)
    // Sweeping light beams from stadium top corners
    val sweep = sin(t * PI.toFloat()) * size.width * 0.35f
    drawLine(
        color = accent.copy(alpha = alpha * 0.35f),
        start = Offset(0f, 0f),
        end = Offset(size.width * 0.4f + sweep, size.height),
        strokeWidth = 65f
    )
    drawLine(
        color = Color(0xFFFACC15).copy(alpha = alpha * 0.3f),
        start = Offset(size.width, 0f),
        end = Offset(size.width * 0.6f - sweep, size.height),
        strokeWidth = 65f
    )
    particles.forEach { p ->
        val px = size.width * p.xFraction
        val py = size.height * ((p.yFraction + t * p.speed) % 1f)
        drawCircle(
            color = p.color.copy(alpha = alpha * 0.85f),
            radius = p.radius * 0.7f,
            center = Offset(px, py)
        )
    }
}
