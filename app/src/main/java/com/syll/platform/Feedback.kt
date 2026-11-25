package com.syll.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** iOS `FeedbackProviding`: the three haptics the app plays. */
interface FeedbackProviding {
    fun selectionChanged()
    fun success()
    fun error()
}

/** iOS `NoOpFeedback`, for tests and previews. */
object NoOpFeedback : FeedbackProviding {
    override fun selectionChanged() {}
    override fun success() {}
    override fun error() {}
}

/**
 * iOS `FeedbackHaptics`: the same three patterns (one light tap; two rising taps; three firm taps), played with
 * the system's predefined effects where they exist (API 29+) and short waveforms before that.
 */
class VibratorFeedback(context: Context) : FeedbackProviding {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun selectionChanged() = play(
        predefined = VibrationEffect.EFFECT_TICK,
        fallback = { VibrationEffect.createOneShot(10, 120) },
    )

    override fun success() = play(
        predefined = null,
        fallback = { VibrationEffect.createWaveform(longArrayOf(0, 15, 105, 25), intArrayOf(0, 150, 0, 255), -1) },
    )

    override fun error() = play(
        predefined = null,
        fallback = { VibrationEffect.createWaveform(longArrayOf(0, 20, 80, 20, 80, 30), intArrayOf(0, 200, 0, 200, 0, 255), -1) },
    )

    private fun play(predefined: Int?, fallback: () -> VibrationEffect) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (predefined != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(predefined)
        } else {
            fallback()
        }
        runCatching { vibrator.vibrate(effect) }
    }
}

/** iOS `SettingsAwareFeedback`: silent while haptics are off in Settings. */
class SettingsAwareFeedback(private val base: FeedbackProviding) : FeedbackProviding {
    var isEnabled = true

    override fun selectionChanged() {
        if (isEnabled) base.selectionChanged()
    }

    override fun success() {
        if (isEnabled) base.success()
    }

    override fun error() {
        if (isEnabled) base.error()
    }
}
