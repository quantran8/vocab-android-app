package com.syll.platform

import com.syll.domain.practice.RecordedAudio

/** iOS `AudioClipPlaying`: plays the plan's word clips. */
interface AudioClipPlaying {
    /** Snapshot state: a clip is sounding now (drives `SpeakerWaveIcon`). */
    val isPlaying: Boolean
    val canPlay: Boolean
    /** Returns true only if sound really started, which is what `deliveryMode: audio` claims. */
    suspend fun play(url: String): Boolean
    fun stop()
    /** Downloads clips ahead of time so their first play starts at once. Failures are left for [play] to report. */
    suspend fun preload(urls: List<String>) {}
}

/** iOS `SilentAudioClips`, for tests and previews. */
object SilentAudioClips : AudioClipPlaying {
    override val isPlaying = false
    override val canPlay = false
    override suspend fun play(url: String) = false
    override fun stop() {}
}

/** iOS `VoiceRecording`: records one take as 16 kHz mono WAV. */
interface VoiceRecording {
    /** False once the learner has denied the microphone. */
    val canRecord: Boolean
    /** Fires when the time limit stops a recording. */
    var onLimitReached: (() -> Unit)?
    /** Smoothed input loudness, 0 (silence) to 1. Polled every frame while recording. */
    fun inputLevel(): Double
    suspend fun requestAccess(): Boolean
    suspend fun start()
    /** Ends the recording and returns it; null if nothing usable was captured. */
    fun stop(): RecordedAudio?
    fun cancel()
}

/** iOS `NoVoiceRecorder`. */
object NoVoiceRecorder : VoiceRecording {
    override val canRecord = false
    override var onLimitReached: (() -> Unit)? = null
    override fun inputLevel() = 0.0
    override suspend fun requestAccess() = false
    override suspend fun start() = throw IllegalStateException("No recorder")
    override fun stop(): RecordedAudio? = null
    override fun cancel() {}
}

/** iOS `SpeechTranscribing`: recognizes a finished take on the device (free plan). */
interface SpeechTranscribing {
    /** False when recognition can't run on this device: no permission, or no on-device model for English. */
    suspend fun requestAccess(): Boolean
    /** Candidate transcripts, best first; empty when nothing was recognized. */
    suspend fun transcribe(audio: RecordedAudio): List<String>
}

/** iOS `NoSpeechTranscriber`. */
object NoSpeechTranscriber : SpeechTranscribing {
    override suspend fun requestAccess() = false
    override suspend fun transcribe(audio: RecordedAudio) = emptyList<String>()
}

/** iOS `SoundEffectsPlaying`: the answer cues and named one-shot cues. */
interface SoundEffectsPlaying {
    fun playCorrect()
    fun playWrong()
    fun playClick()
    /** Loads named one-shot cues ahead of [playCue], so they start on time. */
    fun prepareCues(names: List<String>) {}
    /** Plays a named cue; it may overlap other cues. A missing file is skipped. */
    fun playCue(name: String) {}
}

/** iOS `SilentSoundEffects`. */
object SilentSoundEffects : SoundEffectsPlaying {
    override fun playCorrect() {}
    override fun playWrong() {}
    override fun playClick() {}
}
