package com.syll.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors

/**
 * iOS `PremiumGradient`: the Premium identity (redesign v1, flow 6). Grape for the Premium blocks, Sun 400 as the
 * spark (crown, Premium button), and one signature gradient, Grape 500 → Coral 400. Only the paywall, Premium entry
 * points and the plan cards use it.
 */
object PremiumGradient {
    /** "Premium" in headlines and copy: Grape 500 → Coral 400. */
    val accent: Brush = Brush.horizontalGradient(listOf(SyllColors.grape500, SyllColors.coral400))

    /** The paywall's wash and the Profile upgrade card: Grape 100 → Grape 50 → Coral 100, diagonal. */
    val wash: Brush = Brush.linearGradient(
        0f to SyllColors.grape100,
        0.48f to SyllColors.grape50,
        1f to SyllColors.coral100,
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}

/** iOS `PremiumWash`: the wash over the top of the paywall at full strength, fading into white before the content. */
@Composable
fun PremiumWash(modifier: Modifier = Modifier, height: Dp = 460.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawRect(PremiumGradient.wash)
                drawRect(
                    Brush.verticalGradient(0.6f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/**
 * iOS `PremiumWord`: "Premium" in the signature gradient with the Home Start button's shimmer running through the
 * letters. Reduce motion: the gradient stays, no sweep.
 */
@Composable
fun PremiumWord(style: TextStyle, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    Text(
        "Premium",
        style = style.copy(brush = PremiumGradient.accent),
        modifier = modifier.then(if (motion.reduceMotion) Modifier else Modifier.letterShimmer()),
    )
}

/** The button shimmer ([shimmerSweep]), kept inside the glyphs it crosses. */
private fun Modifier.letterShimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "premiumShimmer")
    val cycleMillis = (MotionTokens.shimmerCycle * 1000).toInt()
    val sweepMillis = (MotionTokens.shimmerSweep * 1000).toInt()
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = cycleMillis
                0f at 0 using LinearEasing
                1f at sweepMillis
                1f at cycleMillis
            },
        ),
        label = "premiumShimmerProgress",
    )
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val x = -0.6f + progress * 1.9f
        drawRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0f),
                0.5f to Color.White.copy(alpha = 0.55f),
                1f to Color.White.copy(alpha = 0f),
                start = Offset(x * size.width, 0f),
                end = Offset((x + 0.3f) * size.width, size.height),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }
}

/** iOS `PremiumBadge`: the crown on a Grape 500 disc, on Profile's plan cards. */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier, size: Dp = Spacing.iconBadge) {
    Box(modifier.size(size).background(SyllColors.grape500, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = SyllColors.sun400, modifier = Modifier.size(size * 0.5f))
    }
}
