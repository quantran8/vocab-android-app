package com.syll.features.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * iOS `hostsStepActionBar()` / `stepActionBar`: a step hands its pinned bottom bar to the flow, which draws it outside
 * the step transition so it stays put while steps slide. [step] picks the newest bar while the outgoing and incoming
 * steps are both on screen.
 */
@Stable
class StepActionBarHost {
    var step by mutableIntStateOf(-1)
        private set
    var bar by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun offer(step: Int, bar: @Composable () -> Unit) {
        if (step < this.step) return
        this.step = step
        this.bar = bar
    }
}

val LocalStepActionBarHost = compositionLocalOf<StepActionBarHost?> { null }

/** The index of the step being drawn, set by the hosting flow. */
val LocalStepIndex = compositionLocalOf { 0 }

/** Pins [content] as the step's bottom bar. Without a hosting flow it is drawn in place. */
@Composable
fun StepActionBar(content: @Composable () -> Unit) {
    val host = LocalStepActionBarHost.current
    if (host == null) {
        content()
        return
    }
    val step = LocalStepIndex.current
    val latest by rememberUpdatedState(content)
    SideEffect { host.offer(step) { latest() } }
}
