package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DiceMode
import com.example.ui.theme.AntiqueParchment
import com.example.ui.theme.BioluminescentCyan
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.CrimsonCorsair
import com.example.ui.theme.InkDark
import com.example.ui.theme.MahoganyWood
import com.example.ui.theme.TreasureGold
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

data class DiceRollAnimState(
    val isRolling: Boolean = false,
    val targetDie1: Int = 1,
    val targetDie2: Int = 1,
    val diceMode: DiceMode = DiceMode.NORMAL,
    val isDoubles: Boolean = false,
    val rollerName: String = "",
    val rollId: Long = 0L
)

/**
 * Jetpack Compose component that uses [Animatable] to simulate a 4-second spinning dice
 * animation with randomized faces, culminating in the final result display.
 */
@Composable
fun DiceRollingComponent(
    animState: DiceRollAnimState,
    staticDie1: Int,
    staticDie2: Int,
    onDiceSpinTick: () -> Unit = {},
    onDiceSettle: () -> Unit = {},
    onRollComplete: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var displayedDie1 by remember(staticDie1) { mutableIntStateOf(staticDie1.coerceIn(1, 6)) }
    var displayedDie2 by remember(staticDie2) { mutableIntStateOf(staticDie2.coerceIn(1, 6)) }
    var isSpinningPhase by remember { mutableStateOf(false) }
    var showFinalResultBadge by remember { mutableStateOf(false) }

    val spinRotation1 = remember { Animatable(0f) }
    val spinRotation2 = remember { Animatable(0f) }
    val tiltX = remember { Animatable(0f) }
    val bounceScale = remember { Animatable(1f) }
    val hopOffsetY = remember { Animatable(0f) }

    LaunchedEffect(animState.isRolling, animState.rollId) {
        if (animState.isRolling && animState.rollId > 0L) {
            isSpinningPhase = true
            showFinalResultBadge = false
            bounceScale.snapTo(1.08f)

            val rollStartMs = System.currentTimeMillis()
            val spinDurationMs = 3200L
            val totalSequenceMs = 4000L
            var tick = 0

            // Phase 1 (~3.2s): Decelerating spin with randomized dice faces + tactile ticks
            while (System.currentTimeMillis() - rollStartMs < spinDurationMs) {
                val elapsed = System.currentTimeMillis() - rollStartMs
                val progress = (elapsed.toFloat() / spinDurationMs.toFloat()).coerceIn(0f, 1f)

                displayedDie1 = ((displayedDie1 + Random.nextInt(1, 5)) % 6) + 1
                displayedDie2 = ((displayedDie2 + Random.nextInt(1, 5)) % 6) + 1

                if (tick % 2 == 0) {
                    onDiceSpinTick()
                }

                val stepDurationMs = (65L + (progress * 165L).toLong())
                val angleDelta = 150f * (1f - progress * 0.45f)
                coroutineScope {
                    launch {
                        spinRotation1.animateTo(
                            targetValue = spinRotation1.value + angleDelta,
                            animationSpec = tween(stepDurationMs.toInt(), easing = LinearEasing)
                        )
                    }
                    launch {
                        spinRotation2.animateTo(
                            targetValue = spinRotation2.value - angleDelta * 0.92f,
                            animationSpec = tween(stepDurationMs.toInt(), easing = LinearEasing)
                        )
                    }
                    launch {
                        tiltX.animateTo(
                            targetValue = sin(tick * 0.9f) * 28f * (1f - progress * 0.45f),
                            animationSpec = tween(stepDurationMs.toInt(), easing = LinearEasing)
                        )
                    }
                    launch {
                        hopOffsetY.animateTo(
                            targetValue = -abs(sin(tick * 0.85f)) * 12f * (1f - progress * 0.4f),
                            animationSpec = tween(stepDurationMs.toInt(), easing = LinearEasing)
                        )
                    }
                }
                tick++
                delay(20L)
            }

            // Phase 2 (~0.8s): Settle smoothly onto final faces and reveal the result display
            isSpinningPhase = false
            displayedDie1 = animState.targetDie1.coerceIn(1, 6)
            displayedDie2 = animState.targetDie2.coerceIn(1, 6)
            onDiceSettle()

            bounceScale.snapTo(1.26f)
            coroutineScope {
                launch {
                    spinRotation1.animateTo(
                        targetValue = ((spinRotation1.value / 360f).toInt() + 1) * 360f,
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    spinRotation2.animateTo(
                        targetValue = ((spinRotation2.value / 360f).toInt()) * 360f,
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    )
                }
                launch { tiltX.animateTo(0f, animationSpec = tween(200)) }
                launch { hopOffsetY.animateTo(0f, animationSpec = tween(200)) }
                launch {
                    bounceScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
            showFinalResultBadge = true

            val remainingMs = (totalSequenceMs - (System.currentTimeMillis() - rollStartMs)).coerceAtLeast(350L)
            delay(remainingMs)
            onRollComplete(animState.rollId)
        } else {
            isSpinningPhase = false
            showFinalResultBadge = false
            displayedDie1 = staticDie1.coerceIn(1, 6)
            displayedDie2 = staticDie2.coerceIn(1, 6)
            spinRotation1.snapTo(0f)
            spinRotation2.snapTo(0f)
            tiltX.snapTo(0f)
            hopOffsetY.snapTo(0f)
            bounceScale.snapTo(1f)
        }
    }

    val auraTransition = rememberInfiniteTransition(label = "dice_aura")
    val auraRotation by auraTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_angle"
    )

    val accentColor = when (animState.diceMode) {
        DiceMode.ODD -> CrimsonCorsair
        DiceMode.EVEN -> BioluminescentCyan
        DiceMode.NORMAL -> TreasureGold
    }

    Column(
        modifier = modifier.testTag("dice_rolling_component"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSpinningPhase) {
                Canvas(
                    modifier = Modifier
                        .size(116.dp)
                        .graphicsLayer { rotationZ = auraRotation }
                ) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                TreasureGold.copy(alpha = 0.0f),
                                TreasureGold.copy(alpha = 0.78f),
                                BioluminescentCyan.copy(alpha = 0.72f),
                                TreasureGold.copy(alpha = 0.0f)
                            )
                        ),
                        style = Stroke(width = 6f)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoldenRuneDieFace(
                    value = displayedDie1,
                    rotationZ = if (animState.isRolling) spinRotation1.value else 0f,
                    rotationX = if (animState.isRolling) tiltX.value else 0f,
                    scale = bounceScale.value,
                    offsetY = hopOffsetY.value,
                    isGlowing = isSpinningPhase || showFinalResultBadge,
                    accentColor = accentColor
                )
                GoldenRuneDieFace(
                    value = displayedDie2,
                    rotationZ = if (animState.isRolling) spinRotation2.value else 0f,
                    rotationX = if (animState.isRolling) -tiltX.value else 0f,
                    scale = bounceScale.value,
                    offsetY = hopOffsetY.value,
                    isGlowing = isSpinningPhase || showFinalResultBadge,
                    accentColor = accentColor
                )
            }
        }

        AnimatedVisibility(
            visible = animState.isRolling,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val badgeText = when {
                isSpinningPhase -> "🎲 ${animState.rollerName} กำลังทอยเต๋า..."
                animState.isDoubles -> "🔥 เบิ้ล! รวม ${displayedDie1 + displayedDie2} แต้ม!"
                else -> "✨ ได้ผลลัพธ์ ${displayedDie1 + displayedDie2} แต้ม!"
            }
            Surface(
                color = if (showFinalResultBadge && animState.isDoubles) CrimsonCorsair else MahoganyWood.copy(alpha = 0.94f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .border(1.dp, TreasureGold, RoundedCornerShape(10.dp))
                    .testTag("dice_result_badge")
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showFinalResultBadge && animState.isDoubles) Color.White else TreasureGold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun GoldenRuneDieFace(
    value: Int,
    rotationZ: Float,
    rotationX: Float,
    scale: Float,
    offsetY: Float,
    isGlowing: Boolean,
    accentColor: Color
) {
    val clamped = value.coerceIn(1, 6)
    Box(
        modifier = Modifier
            .size(46.dp)
            .offset(y = offsetY.dp)
            .graphicsLayer {
                this.rotationZ = rotationZ
                this.rotationX = rotationX
                this.scaleX = scale
                this.scaleY = scale
                this.cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.linearGradient(
                    listOf(TreasureGold, BurnishedGold, Color(0xFFB58924))
                )
            )
            .border(
                width = if (isGlowing) 2.5.dp else 2.dp,
                color = if (isGlowing) accentColor else AntiqueParchment,
                shape = RoundedCornerShape(11.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            val w = size.width
            val h = size.height
            val r = size.minDimension * 0.13f

            val tl = Offset(w * 0.2f, h * 0.2f)
            val tr = Offset(w * 0.8f, h * 0.2f)
            val ml = Offset(w * 0.2f, h * 0.5f)
            val mc = Offset(w * 0.5f, h * 0.5f)
            val mr = Offset(w * 0.8f, h * 0.5f)
            val bl = Offset(w * 0.2f, h * 0.8f)
            val br = Offset(w * 0.8f, h * 0.8f)

            val pips = when (clamped) {
                1 -> listOf(mc)
                2 -> listOf(tl, br)
                3 -> listOf(tl, mc, br)
                4 -> listOf(tl, tr, bl, br)
                5 -> listOf(tl, tr, mc, bl, br)
                else -> listOf(tl, tr, ml, mr, bl, br)
            }

            pips.forEach { pos ->
                drawCircle(
                    color = if (clamped == 1) CrimsonCorsair else InkDark,
                    radius = if (clamped == 1) r * 1.35f else r,
                    center = pos
                )
            }
        }
    }
}
