package com.syll.platform

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.syll.R

/**
 * iOS `SoundEffects`: short UI cues from `res/raw` (copies of iOS `Resources/Sounds`). A [SoundPool] decodes them
 * once off the main thread, so they play without delay; a cue due before its sound has loaded is skipped, as on iOS.
 */
class SoundEffects(private val context: Context) : SoundEffectsPlaying {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded = mutableSetOf<Int>()
    private val correct = pool.load(context, R.raw.vocab_correct, 1)
    private val wrong = pool.load(context, R.raw.vocab_wrong, 1)
    private val click = pool.load(context, R.raw.click2, 1)
    private val cues = mutableMapOf<String, Int>()
    /** The answer cue playing now: the next one replaces it instead of overlapping. */
    private var answerStream = 0

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(loaded) { loaded += sampleId } }
    }

    override fun playCorrect() = playAnswer(correct)
    override fun playWrong() = playAnswer(wrong)
    override fun playClick() { play(click) }

    override fun prepareCues(names: List<String>) {
        for (name in names) {
            if (name in cues) continue
            val resource = rawResource(name)
            cues[name] = if (resource != 0) pool.load(context, resource, 1) else 0
        }
    }

    override fun playCue(name: String) {
        prepareCues(listOf(name))
        cues[name]?.takeIf { it != 0 }?.let(::play)
    }

    private fun playAnswer(sound: Int) {
        if (answerStream != 0) pool.stop(answerStream)
        answerStream = play(sound)
    }

    private fun play(sound: Int): Int {
        if (synchronized(loaded) { sound !in loaded }) return 0
        return pool.play(sound, 1f, 1f, 1, 0, 1f)
    }

    /** Cues are named like iOS (`celebrate_flip_land`), so they are looked up by name. */
    @SuppressLint("DiscouragedApi")
    private fun rawResource(name: String) = context.resources.getIdentifier(name, "raw", context.packageName)
}
