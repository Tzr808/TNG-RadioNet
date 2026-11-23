package com.armanmaurya.internetradio.service.playback.engine

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manages and broadcasts real-time audio amplitude (RMS) calculated from the stream audio sink.
 * Decouples the audio processing pipeline from PlayerController and eliminates the
 * circular client-server dependency between PlaybackService and PlayerController.
 *
 * The audio sink produces a new value for every decoded buffer (tens per second). Publishing
 * each one would rebuild the whole player UI state for every buffer, so updates are rate
 * limited to [MIN_EMIT_INTERVAL_MS] and ignore imperceptible changes.
 */
@Singleton
class AudioAmplitudeManager @Inject constructor() {

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var lastEmitUptimeMs = 0L

    fun updateAmplitude(rms: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitUptimeMs < MIN_EMIT_INTERVAL_MS) return
        if (abs(rms - _amplitude.value) < MIN_DELTA) return
        lastEmitUptimeMs = now
        _amplitude.value = rms
    }

    fun reset() {
        lastEmitUptimeMs = SystemClock.elapsedRealtime()
        _amplitude.value = 0f
    }

    private companion object {
        const val MIN_EMIT_INTERVAL_MS = 100L
        const val MIN_DELTA = 0.01f
    }
}
