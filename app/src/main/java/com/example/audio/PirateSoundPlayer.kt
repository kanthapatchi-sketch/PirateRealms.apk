package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class PirateSoundEffect {
    DICE_ROLL_TICK,
    DICE_SETTLE,
    TILE_STEP,
    TILE_LANDING,
    PURCHASE_ISLAND
}

/**
 * Lightweight synthesized PCM sound effects player for tactile board-game feedback
 * (dice roll ticks/settle, tile stepping/landing, and island purchases) without external audio assets.
 */
class PirateSoundPlayer {

    @Volatile
    var isMuted: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val diceTickBuffer: ShortArray by lazy { synthesizeDiceTick() }
    private val diceSettleBuffer: ShortArray by lazy { synthesizeDiceSettle() }
    private val tileStepBuffer: ShortArray by lazy { synthesizeTileStep() }
    private val tileLandingBuffer: ShortArray by lazy { synthesizeTileLanding() }
    private val purchaseIslandBuffer: ShortArray by lazy { synthesizePurchaseChime() }

    fun play(effect: PirateSoundEffect) {
        if (isMuted) return
        val buffer = when (effect) {
            PirateSoundEffect.DICE_ROLL_TICK -> diceTickBuffer
            PirateSoundEffect.DICE_SETTLE -> diceSettleBuffer
            PirateSoundEffect.TILE_STEP -> tileStepBuffer
            PirateSoundEffect.TILE_LANDING -> tileLandingBuffer
            PirateSoundEffect.PURCHASE_ISLAND -> purchaseIslandBuffer
        }
        scope.launch {
            playPcmBuffer(buffer)
        }
    }

    fun playDiceRollTick() = play(PirateSoundEffect.DICE_ROLL_TICK)
    fun playDiceSettle() = play(PirateSoundEffect.DICE_SETTLE)
    fun playTileStep() = play(PirateSoundEffect.TILE_STEP)
    fun playTileLanding() = play(PirateSoundEffect.TILE_LANDING)
    fun playPurchaseIsland() = play(PirateSoundEffect.PURCHASE_ISLAND)

    fun release() {
        scope.cancel()
    }

    private fun playPcmBuffer(samples: ShortArray) {
        runCatching {
            val byteSize = samples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            try {
                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L) / SAMPLE_RATE
                Thread.sleep(durationMs + 35L)
            } finally {
                runCatching {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                }
            }
        }
    }

    private fun synthesizeDiceTick(): ShortArray {
        val count = (SAMPLE_RATE * 0.042).toInt()
        val rng = Random(1337)
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 95.0)
            val woodResonance = 0.65 * sin(2.0 * PI * 510.0 * t) + 0.35 * sin(2.0 * PI * 890.0 * t)
            val transient = (rng.nextDouble() * 2.0 - 1.0) * 0.28 * exp(-t * 180.0)
            ((woodResonance * 0.72 + transient) * env * 0.42 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthesizeDiceSettle(): ShortArray {
        val count = (SAMPLE_RATE * 0.14).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val thudEnv = exp(-t * 42.0)
            val chimeEnv = exp(-t * 20.0)
            val thud = sin(2.0 * PI * 240.0 * t) * thudEnv * 0.55
            val chime = (sin(2.0 * PI * 660.0 * t) + 0.4 * sin(2.0 * PI * 990.0 * t)) * chimeEnv * 0.35
            ((thud + chime) * 0.5 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthesizeTileStep(): ShortArray {
        val count = (SAMPLE_RATE * 0.058).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 68.0)
            val freq = 580.0 - 170.0 * (i.toDouble() / count)
            val wave = sin(2.0 * PI * freq * t) + 0.25 * sin(2.0 * PI * (freq * 2.0) * t)
            (wave * env * 0.38 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthesizeTileLanding(): ShortArray {
        val count = (SAMPLE_RATE * 0.22).toInt()
        val split = (count * 0.42).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val sample = if (i < split) {
                val env = exp(-t * 22.0)
                (sin(2.0 * PI * 523.25 * t) + 0.3 * sin(2.0 * PI * 1046.5 * t)) * env
            } else {
                val lt = (i - split).toDouble() / SAMPLE_RATE
                val env = exp(-lt * 16.0)
                (sin(2.0 * PI * 783.99 * lt) + 0.3 * sin(2.0 * PI * 1567.98 * lt)) * env
            }
            (sample * 0.38 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthesizePurchaseChime(): ShortArray {
        val notes = doubleArrayOf(659.25, 830.61, 987.77, 1318.51) // E5 -> G#5 -> B5 -> E6
        val noteDur = 0.072
        val totalSamples = (SAMPLE_RATE * (notes.size * noteDur + 0.08)).toInt()
        val out = DoubleArray(totalSamples)
        notes.forEachIndexed { idx, freq ->
            val startSample = (idx * noteDur * SAMPLE_RATE).toInt()
            val len = (SAMPLE_RATE * 0.16).toInt()
            for (k in 0 until len) {
                val pos = startSample + k
                if (pos >= totalSamples) break
                val lt = k.toDouble() / SAMPLE_RATE
                val env = exp(-lt * 19.0)
                out[pos] += (sin(2.0 * PI * freq * lt) + 0.25 * sin(2.0 * PI * (freq * 2.0) * lt)) * env
            }
        }
        return ShortArray(totalSamples) { i ->
            (out[i] * 0.32 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 22050
    }
}
