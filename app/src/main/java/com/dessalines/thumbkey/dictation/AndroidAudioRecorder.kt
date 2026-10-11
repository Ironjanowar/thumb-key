package com.dessalines.thumbkey.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

interface PcmSource {
    fun start()

    fun read(buffer: ShortArray): Int

    fun stop()

    fun release()
}

data class RecordedAudio(
    val samples: ShortArray,
    val hasSpeech: Boolean,
)

class PcmCaptureLoop(
    private val source: PcmSource,
    private val monotonicNanos: () -> Long,
    private val maxSeconds: Int = AndroidAudioRecorder.MAX_SECONDS,
    private val sampleRate: Int = AndroidAudioRecorder.SAMPLE_RATE,
    private val shouldStop: () -> Boolean = { false },
) {
    fun record(
        cancelled: AtomicBoolean,
        onSecond: (Int) -> Unit,
    ): RecordedAudio {
        if (cancelled.get()) return RecordedAudio(ShortArray(0), false)
        val deadline = monotonicNanos() + maxSeconds * NANOS_PER_SECOND
        val maximumSamples = sampleRate * maxSeconds
        var samples = ShortArray(minOf(sampleRate, maximumSamples))
        var size = 0
        var energy = 0.0
        var peak = 0
        var lastSecond = -1
        try {
            if (cancelled.get()) return RecordedAudio(ShortArray(0), false)
            source.start()
            while (!cancelled.get() && !shouldStop() && size < maximumSamples && monotonicNanos() < deadline) {
                val buffer = ShortArray(minOf(2048, maximumSamples - size))
                val count =
                    try {
                        source.read(buffer)
                    } catch (error: Throwable) {
                        if (cancelled.get() || shouldStop()) break
                        throw error
                    }
                if (cancelled.get() || shouldStop()) break
                if (count < 0) break
                if (count == 0) continue
                if (size + count > samples.size) samples = samples.copyOf(minOf(maximumSamples, maxOf(size + count, samples.size * 2)))
                buffer.copyInto(samples, size, 0, count)
                repeat(count) { index ->
                    val value = kotlin.math.abs(buffer[index].toInt())
                    peak = maxOf(peak, value)
                    energy += value.toDouble() * value
                }
                size += count
                val second = size / sampleRate
                if (second != lastSecond) {
                    lastSecond = second
                    onSecond(second)
                }
            }
        } finally {
            runCatching { source.stop() }
            source.release()
        }
        val rms = if (size == 0) 0.0 else sqrt(energy / size)
        return RecordedAudio(samples.copyOf(size), size >= MIN_SPEECH_SAMPLES && peak >= MIN_PEAK && rms >= MIN_RMS)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MIN_SPEECH_SAMPLES = 160
        const val MIN_PEAK = 600
        const val MIN_RMS = 250.0
    }
}

class AndroidAudioRecorder(
    private val context: Context,
) {
    private val cancelled = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)

    @Volatile private var source: PcmSource? = null

    fun record(onSecond: (Int) -> Unit): RecordedAudio {
        if (cancelled.get()) return RecordedAudio(ShortArray(0), false)
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is not granted"
        }
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBytes > 0) { "Audio capture is unavailable" }
        val audio =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBytes * 2,
            )
        check(audio.state == AudioRecord.STATE_INITIALIZED) {
            audio.release()
            "Could not initialize microphone"
        }
        val local = AndroidPcmSource(audio)
        source = local
        if (cancelled.get()) {
            local.release()
            source = null
            return RecordedAudio(ShortArray(0), false)
        }
        return try {
            PcmCaptureLoop(local, { SystemClock.elapsedRealtimeNanos() }, shouldStop = stopRequested::get).record(cancelled, onSecond)
        } finally {
            source = null
        }
    }

    fun stop() {
        stopRequested.set(true)
        runCatching { source?.stop() }
    }

    fun cancel() {
        cancelled.set(true)
        runCatching { source?.stop() }
    }

    private class AndroidPcmSource(
        private val audio: AudioRecord,
    ) : PcmSource {
        override fun start() = audio.startRecording()

        override fun read(buffer: ShortArray): Int {
            val count = audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            check(count >= 0) { "Microphone read failed ($count)" }
            return count
        }

        override fun stop() {
            if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop()
        }

        override fun release() = audio.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 60
    }
}
