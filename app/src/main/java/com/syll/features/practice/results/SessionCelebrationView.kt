package com.syll.features.practice.results

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import com.syll.R
import com.syll.app.LocalSoundEffects
import com.syll.data.SharedPreferencesKeyValueStore
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.RiveArtboard
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.CelebrationCounts
import com.syll.domain.practice.CelebrationKind
import com.syll.domain.practice.CelebrationVariant
import com.syll.domain.practice.PracticeResult
import com.syll.domain.practice.ScopeEvents
import com.syll.domain.ranking.SessionEffort
import kotlinx.coroutines.delay

/** iOS `SessionCelebration`: what the celebration says, decided from the commit. */
data class SessionCelebration(
    val kind: CelebrationKind,
    val perfect: Boolean,
    val counts: CelebrationCounts,
    /** The collection this session finished; null otherwise. */
    val finishedScopeName: String?,
    /** Null from a backend without ranking. */
    val earnedLP: Int? = null,
) {
    companion object {
        fun of(result: PracticeResult, effort: SessionEffort?, scopeEvents: ScopeEvents?, finishedScopeName: String?): SessionCelebration {
            val counts = CelebrationCounts.of(result.summary)
            val finishedCollection = scopeEvents?.collectionCompleted == true
            return SessionCelebration(
                kind = if (finishedCollection) CelebrationKind.COLLECTION else CelebrationKind.SESSION,
                perfect = counts.perfect,
                counts = counts,
                finishedScopeName = finishedScopeName?.takeIf { finishedCollection },
                earnedLP = effort?.earned,
            )
        }
    }
}

@Composable
private fun SessionCelebration.title(): String = when {
    finishedScopeName != null -> stringResource(R.string.you_ve_learned_every_word_in_x, finishedScopeName)
    counts.perfect -> stringResource(R.string.perfect_session)
    counts.newWords == 0 && counts.reviewed > 0 -> stringResource(R.string.review_complete)
    else -> stringResource(R.string.nice_work)
}

/** Only counts, new and reviewed words alike: nothing about right or wrong. */
@Composable
private fun SessionCelebration.line(): String? {
    val (new, reviewed) = counts.newWords to counts.reviewed
    return when {
        new == 0 && reviewed == 0 -> null
        new == 0 -> stringResource(R.string.you_reviewed_x_words, reviewed)
        reviewed == 0 -> stringResource(R.string.you_learned_x_new_words, new)
        else -> stringResource(R.string.you_learned_x_new_words_and_reviewed_x_words, new, reviewed)
    }
}

/** The artboard is a 390×844 screen: Syllie's stage is centred 230 pt down, about 220 pt tall. */
private const val ARTBOARD_WIDTH = 390f
private const val ARTBOARD_HEIGHT = 844f
/** Where the title starts, in artboard points: just under Syllie's stage. */
private const val COPY_TOP = 364f
private const val STAGGER = 0.15
/** With reduced motion the badge stands where Syllie would be, just above the title. */
private val BADGE_LIFT = 140.dp

/**
 * iOS `SessionCelebrationView`: beat 1 of the end of a session. Syllie celebrates on the `CelebrateV2` artboard, then
 * the title, one line of counts and "Continue" pop in as he lands. Tapping before he lands skips to the end.
 */
@Composable
fun SessionCelebrationView(celebration: SessionCelebration, onContinue: () -> Unit, fixedVariant: CelebrationVariant? = null) {
    val motion = LocalMotion.current
    val sounds = LocalSoundEffects.current
    val context = LocalContext.current
    val variant = remember { fixedVariant ?: nextCelebrationVariant(SharedPreferencesKeyValueStore(context)) }
    var showsCopy by remember { mutableStateOf(false) }
    /** Set once the artboard is playing; the sound cues count from then. */
    var startedAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(variant) {
        sounds.prepareCues(variant.soundCues.map { it.name })
        if (!motion.reduceMotion) delay((variant.landingSeconds * 1000).toLong())
        showsCopy = true
    }
    // Each cue starts at its offset from the start of the animation; leaving early stops the rest.
    LaunchedEffect(startedAt) {
        val start = startedAt ?: return@LaunchedEffect
        variant.soundCues.sortedBy { it.atSeconds }.forEach { cue ->
            val wait = start + (cue.atSeconds * 1000).toLong() - SystemClock.uptimeMillis()
            if (wait > 0) delay(wait)
            sounds.playCue(cue.name)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null) { showsCopy = true },
    ) {
        // The artboard is drawn from the top of the screen, status bar included, like the design frame.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val scale = maxWidth.value / ARTBOARD_WIDTH
            if (motion.reduceMotion) {
                Box(Modifier.fillMaxWidth().padding(top = (COPY_TOP * scale).dp - BADGE_LIFT), contentAlignment = Alignment.TopCenter) {
                    CelebrationBadge()
                }
            } else {
                CelebrateV2(
                    variant, celebration.kind, celebration.perfect,
                    Modifier.fillMaxWidth().height((ARTBOARD_HEIGHT * scale).dp),
                    onStart = { if (startedAt == null) startedAt = SystemClock.uptimeMillis() },
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.practiceGutter)
                    .padding(top = (COPY_TOP * scale).dp)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    celebration.title(),
                    style = Typography.display,
                    color = SyllColors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }.popIn(showsCopy, 0.0),
                )
                celebration.line()?.let {
                    Text(
                        it,
                        style = Typography.ipaCard,
                        color = SyllColors.mutedInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xs).popIn(showsCopy, STAGGER),
                    )
                }
                celebration.earnedLP?.let { LPPill(it, Modifier.padding(top = Spacing.l + Spacing.xxs).popIn(showsCopy, STAGGER * 2)) }
            }
        }
        PinnedActionBar(Modifier.graphicsLayer { alpha = if (showsCopy) 1f else 0f }) {
            PrimaryButton(stringResource(R.string.s_continue), onClick = onContinue, enabled = showsCopy)
        }
    }
}

/** iOS `CelebrationVariant.next`: a random variant, never the one shown last time. */
private fun nextCelebrationVariant(storage: SharedPreferencesKeyValueStore): CelebrationVariant {
    val pick = CelebrationVariant.pick(CelebrationVariant.of(storage.getString(LAST_VARIANT_KEY)))
    storage.putString(LAST_VARIANT_KEY, pick.raw)
    return pick
}

private const val LAST_VARIANT_KEY = "celebration.lastVariant"

/** The timelines of one celebration on the `CelebrateV2` artboard (see [celebrateV2Timelines]). */
data class CelebrateV2Timelines(
    /** Played together from the start: the variant's flight and its effects. */
    val flight: List<String>,
    /** Played once Syllie lands: the crown of a perfect session or the trophy of a finished collection. */
    val extra: String?,
    /** Loops after [extra] so the end pose keeps moving (crown sway, turning rays). */
    val hold: String?,
)

/**
 * iOS sets `variant`, `kind` and `perfect` on the artboard's view model and its state machine picks the timelines;
 * Android plays the same timelines directly (no view models, see the port plan). Matched by playing each timeline
 * on its own in the debug gallery: `fx_air` puffs, `fx_fireworks` the burst, `fx_wind` swooshes, `fx_sparkle` the
 * loop's end sparkle, `fx_dust` the landing ring; `perfect_crown` / `collection_trophy` show the prop, and
 * `crown_sway` / `rays_spin` hold it.
 */
fun celebrateV2Timelines(variant: CelebrationVariant, kind: CelebrationKind, perfect: Boolean): CelebrateV2Timelines {
    val flight = when (variant) {
        CelebrationVariant.PUFF -> listOf("v1_puff", "fx_air", "fx_dust")
        CelebrationVariant.FIREWORKS -> listOf("v2_fireworks", "fx_fireworks")
        CelebrationVariant.FLIP -> listOf("v3_flip", "fx_wind", "fx_dust")
        CelebrationVariant.LOOP -> listOf("v4_loop", "fx_wind", "fx_sparkle")
        CelebrationVariant.BOUNCE -> listOf("v5_bounce", "fx_dust")
    }
    return when {
        kind == CelebrationKind.COLLECTION -> CelebrateV2Timelines(flight, "collection_trophy", "rays_spin")
        perfect -> CelebrateV2Timelines(flight, "perfect_crown", "crown_sway")
        else -> CelebrateV2Timelines(flight, null, null)
    }
}

/** The `CelebrateV2` artboard playing [celebrateV2Timelines]. Decorative only. */
@Composable
private fun CelebrateV2(variant: CelebrationVariant, kind: CelebrationKind, perfect: Boolean, modifier: Modifier, onStart: () -> Unit) {
    val timelines = remember(variant, kind, perfect) { celebrateV2Timelines(variant, kind, perfect) }
    var view by remember { mutableStateOf<app.rive.runtime.kotlin.RiveAnimationView?>(null) }
    // The prop arrives as Syllie lands, then its hold loop takes over.
    LaunchedEffect(view) {
        val playing = view ?: return@LaunchedEffect
        val extra = timelines.extra ?: return@LaunchedEffect
        delay((variant.landingSeconds * 1000).toLong())
        runCatching { playing.play(extra, Loop.ONESHOT, Direction.FORWARDS, false, true) }
        val hold = timelines.hold ?: return@LaunchedEffect
        delay(PROP_POP_MILLIS)
        runCatching { playing.play(hold, Loop.LOOP, Direction.FORWARDS, false, true) }
    }
    // Autoplay with the flight selects the artboard (without it the view stays on the file's default, Syllie);
    // the effects are added to it right away.
    RiveArtboard(
        "CelebrateV2",
        modifier = modifier.clearAndSetSemantics { },
        animation = timelines.flight.first(),
        autoplay = true,
        fit = Fit.FIT_WIDTH,
        loop = Loop.ONESHOT,
        onReady = { riveView ->
            runCatching { riveView.play(timelines.flight.drop(1), Loop.ONESHOT, Direction.FORWARDS, false, true) }
            view = riveView
            onStart()
        },
    )
}

/** Time for the crown or trophy to pop in before its hold loop starts. */
private const val PROP_POP_MILLIS = 800L

/** iOS `CelebrationBadge`: the reduced-motion stand-in for Syllie, a Sun check that settles in. */
@Composable
internal fun CelebrationBadge() {
    val motion = LocalMotion.current
    val scale = remember { Animatable(if (motion.reduceMotion) 1f else 0.6f) }
    LaunchedEffect(Unit) { if (!motion.reduceMotion) scale.animateTo(1f, MotionTokens.entrance.spec()) }
    Box(Modifier.size(96.dp + Spacing.buttonDepth).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clearAndSetSemantics { }) {
        Box(Modifier.size(96.dp).offset(y = Spacing.buttonDepth).background(SyllColors.rewardDepth, CircleShape))
        Box(Modifier.size(96.dp).background(SyllColors.reward, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = SyllColors.ink, modifier = Modifier.size(44.dp))
        }
    }
}

/** iOS `LPPill`: "+45 LP" on Sun 50. */
@Composable
private fun LPPill(lp: Int, modifier: Modifier) {
    Row(
        modifier.height(Spacing.minTouchTarget).background(SyllColors.sun50, CircleShape).padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Bolt, null, tint = SyllColors.reward)
        Text(
            "+$lp LP",
            style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp, fontFeatureSettings = "tnum"),
            color = SyllColors.sun800,
        )
    }
}

/** iOS `PopIn`: the copy pops in from slightly below, one line after the other, on the `entrance` spring. */
private fun Modifier.popIn(shown: Boolean, delaySeconds: Double): Modifier = composed {
    val motion = LocalMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        if (motion.reduceMotion) {
            progress.animateTo(1f, MotionTokens.reducedCrossfade.spec())
            return@LaunchedEffect
        }
        delay((delaySeconds * 1000).toLong())
        progress.animateTo(1f, MotionTokens.entrance.spec())
    }
    graphicsLayer {
        val t = progress.value
        alpha = t.coerceIn(0f, 1f)
        if (!motion.reduceMotion) {
            val scale = 0.85f + 0.15f * t
            scaleX = scale
            scaleY = scale
            translationY = (1 - t) * MotionTokens.enterOffset.dp.toPx()
        }
    }
}
