@file:OptIn(ExperimentalLayoutApi::class)

package com.syll.features.debug

import com.syll.domain.practice.IntroductionTemplateID
import com.syll.domain.practice.IntroductionPresentation
import com.syll.features.practice.IntroMotionHost
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Loop
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.syll.R
import com.syll.app.LocalAppSettingsStore
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.ActionContext
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.DestructiveButton
import com.syll.designsystem.components.LocalActionContext
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.RiveArtboard
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SmallButton
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.AppLanguage

/**
 * Debug-only check of Phase 0 on a device: design tokens and buttons next to iOS, the Rive spike (the same
 * `syllie.riv` timelines and state machines), the Lottie confetti, reduce motion and the per-app language (RTL).
 * Text here is deliberately not localized.
 */
@Composable
fun DesignGallery(onClose: () -> Unit, onOpenOfflineOnboarding: () -> Unit = {}) {
    val settings = LocalAppSettingsStore.current
    // One artboard at a time: the gallery keeps every section composed, which is too much Rive for a slow device.
    var showsCelebrateLab by remember { mutableStateOf(false) }
    if (showsCelebrateLab) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.screenGutter, vertical = Spacing.m)) {
            SecondaryButton("Back to gallery", onClick = { showsCelebrateLab = false })
            CelebrateV2Timelines()
        }
        return
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text("Design gallery", style = Typography.display)

        Section("Flows (offline API)") {
            SecondaryButton("Onboarding → Home", onClick = onOpenOfflineOnboarding)
            SecondaryButton("CelebrateV2 timelines", onClick = { showsCelebrateLab = true })
        }

        Section("Buttons (app context)") {
            PrimaryButton("Primary", onClick = {})
            PrimaryButton("Practise now", onClick = {}, kind = ActionButtonKind.PRACTICE)
            PrimaryButton("Disabled", onClick = {}, enabled = false, requirement = "Pick an answer first")
            SecondaryButton("Secondary (flat outline)", onClick = {})
            DestructiveButton("Delete account", onClick = {})
            PrimaryButton("Get Premium", onClick = {}, kind = ActionButtonKind.PREMIUM)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SmallButton("+ Add word", onClick = {})
                SmallButton("Practise", onClick = {}, kind = ActionButtonKind.PRACTICE)
            }
        }

        Section("Buttons (practice context)") {
            CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) {
                PrimaryButton("Check", onClick = {})
                SecondaryButton("Skip (outline 3D)", onClick = {})
                PrimaryButton("Continue", onClick = {}, kind = ActionButtonKind.SUCCESS)
                PrimaryButton("Try again", onClick = {}, kind = ActionButtonKind.RETRY)
            }
        }

        Section("Card") {
            Column(Modifier.cardSurface(accent = SyllColors.markIELTS)) {
                listOf("First row", "Second row", "Third row").forEachIndexed { index, title ->
                    if (index > 0) CardDivider()
                    Text(title, Modifier.padding(Spacing.m), style = Typography.body)
                }
            }
        }

        Section("Rive: Syllie timelines") { SyllieTimelines() }

        Section("Rive: state machines") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                RiveArtboard("Loading", stateMachine = "Loading", modifier = Modifier.weight(1f).height(160.dp))
                RiveArtboard("HomeSyllie", stateMachine = "HomeSyllie", modifier = Modifier.weight(1f).height(160.dp))
            }
        }

        Section("Lottie: confetti") { Confetti() }

        Section("Word intro recipes (tap to replay)") {
            IntroductionTemplateID.entries.forEach { id ->
                Text(id.name, style = Typography.micro, color = SyllColors.mutedInk)
                IntroMotionHost("clarify", IntroductionPresentation(id, listOf("cla", "ri", "fy")))
            }
        }

        Section("Motion and language") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reduce motion (in-app)", Modifier.weight(1f), style = Typography.body)
                Switch(checked = settings.reduceMotion, onCheckedChange = { settings.reduceMotion = it })
            }
            Text("Current: ${settings.language.nativeName}", style = Typography.secondary, color = SyllColors.mutedInk)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AppLanguage.entries.forEach { language ->
                    SmallButton(language.nativeName, onClick = { settings.setLanguage(language) })
                }
            }
        }

        SecondaryButton("Close", onClick = onClose)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = Typography.sectionTitle)
        content()
    }
}

@Composable
private fun SyllieTimelines() {
    var view by remember { mutableStateOf<RiveAnimationView?>(null) }
    RiveArtboard(
        artboard = "Syllie",
        animation = "idle",
        loop = Loop.LOOP,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        onReady = { view = it },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf("idle", "thinking_wait", "deep_thought_in", "deep_thought_hold", "review_to_learn", "loading_blink")
            .forEach { name ->
                SmallButton(name, onClick = {
                    view?.let {
                        it.stop()
                        it.play(name, Loop.AUTO)
                    }
                })
            }
    }
}

@Composable
private fun Confetti() {
    var plays by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth().height(220.dp).cardSurface()) {
        if (plays > 0) {
            key(plays) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti))
                val progress by animateLottieCompositionAsState(composition, iterations = 1)
                LottieAnimation(composition, { progress }, Modifier.fillMaxSize())
            }
        }
    }
    SmallButton("Play confetti", onClick = { plays += 1 })
}

/** Debug: plays one `CelebrateV2` timeline at a time (they are combined per variant on the celebration screen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CelebrateV2Timelines() {
    val names = listOf(
        "v1_puff", "v2_fireworks", "v3_flip", "v4_loop", "v5_bounce", "fx_air", "fx_fireworks", "fx_wind", "fx_sparkle",
        "fx_dust", "fx_confetti", "fx_idle", "perfect_crown", "crown_wait", "crown_sway", "collection_trophy", "rays_spin", "hold",
    )
    var view by remember { mutableStateOf<RiveAnimationView?>(null) }
    var current by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            names.forEach { name ->
                SmallButton(name, onClick = {
                    current = name
                    view?.let {
                        runCatching {
                            it.stop()
                            it.play(name, Loop.ONESHOT, app.rive.runtime.kotlin.core.Direction.AUTO, false, true)
                        }
                    }
                })
            }
        }
        Text("Playing: $current", style = Typography.micro, color = SyllColors.mutedInk)
        RiveArtboard(
            "CelebrateV2",
            modifier = Modifier.fillMaxWidth().height(420.dp),
            // Autoplay picks the artboard; without it the view stays on the file's default (Syllie).
            animation = "hold",
            autoplay = true,
            onReady = { view = it },
        )
    }
}
