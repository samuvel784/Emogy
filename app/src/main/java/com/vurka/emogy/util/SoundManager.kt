package com.vurka.emogy.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Emogy's sound engine.
 *
 * Every effect is *synthesised deterministically* once (sine/triangle/bell voices with ADSR-style
 * envelopes plus filtered noise), written as 16-bit PCM WAVs into the app cache and played through
 * [SoundPool]. That gives short, low-latency, musically related cues with no third-party audio
 * assets, no licensing risk, no runtime downloads and no extra permissions.
 *
 * Two rules keep it pleasant:
 *  - the same effect cannot retrigger within [MIN_REPEAT_MS], so rapid taps never machine-gun;
 *  - the victory fanfare replaces the match chime on the final pair instead of stacking on it.
 */
object SoundManager {

    enum class Sfx(val fileName: String) {
        Tap("tap_v1"),
        Flip("flip_v1"),
        Match("match_v1"),
        Mismatch("mismatch_v1"),
        Hint("hint_v1"),
        Shuffle("shuffle_v1"),
        Combo("combo_v1"),
        Coin("coin_v1"),
        Achievement("achievement_v1"),
        Victory("victory_v1"),
        Defeat("defeat_v1"),
        Pause("pause_v1"),
        Star("star_v1"),
        Tick("tick_v1"),
        Whoosh("whoosh_v1"),
        Boss("boss_v1"),
        Chest("chest_v1"),
    }

    private const val SAMPLE_RATE = 44_100
    private const val MAX_STREAMS = 4
    private const val MIN_REPEAT_MS = 45L
    private const val CACHE_DIR = "emogy_sfx_v2"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EmogyAudio").apply { isDaemon = true }
    }

    private val loadedIds = ConcurrentHashMap<Sfx, Int>()
    private val lastPlayedAt = ConcurrentHashMap<Sfx, Long>()

    @Volatile
    private var pool: SoundPool? = null

    @Volatile
    private var preparing = false

    /** Bumped by [release]; a background init that finishes late must not resurrect a dead pool. */
    @Volatile
    private var generation = 0

    // Deliberately silent until MainActivity applies the persisted sliders. This guarantees that a
    // player who muted the app never hears a cue during the first frames after a cold start.
    @Volatile
    private var masterVolume = 0f

    @Volatile
    private var effectsVolume = AudioMix.DEFAULT_EFFECTS

    /** Effects are muted while music focus is lost or the app is in the background. */
    @Volatile
    private var audioFocusGranted = true

    @Volatile
    private var appForeground = true

    /** Applies the master/effects sliders. Safe to call from the UI on every preference change. */
    fun setVolumes(master: Float, effects: Float) {
        masterVolume = master.coerceIn(0f, 1f)
        effectsVolume = effects.coerceIn(0f, 1f)
    }

    /** Music requests audio focus; losing it must silence effects too. */
    fun setAudioFocus(granted: Boolean) {
        audioFocusGranted = granted
    }

    /** No cue may play while the app is in the background. */
    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
    }

    /**
     * Prepares the sound pool on a background thread. Safe to call repeatedly; does nothing after
     * the first successful initialisation (or while one is in progress for the current generation).
     */
    fun init(context: Context) {
        val myGeneration: Int
        synchronized(this) {
            if (pool != null || preparing) return
            preparing = true
            myGeneration = generation
        }
        val appContext = context.applicationContext
        executor.execute {
            try {
                val soundPool = SoundPool.Builder()
                    .setMaxStreams(MAX_STREAMS)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()

                val dir = File(appContext.cacheDir, CACHE_DIR)
                if (!dir.exists()) dir.mkdirs()

                if (myGeneration != generation) {
                    soundPool.release()
                    return@execute
                }

                Sfx.entries.forEach { sfx ->
                    val file = File(dir, "${sfx.fileName}.wav")
                    if (!file.exists() || file.length() < 128) {
                        file.writeBytes(renderWav(sfx))
                    }
                    val id = soundPool.load(file.absolutePath, 1)
                    if (id != 0) loadedIds[sfx] = id
                }
                synchronized(this) {
                    if (myGeneration == generation) {
                        pool = soundPool
                    } else {
                        loadedIds.clear()
                        soundPool.release()
                    }
                }
            } catch (_: Throwable) {
                // Audio is a nicety: if the device cannot do it, the game stays fully playable.
                synchronized(this) {
                    if (myGeneration == generation) {
                        pool = null
                    }
                }
            } finally {
                // Reset preparing only if this task still matches the current generation.
                // If release() was called mid-flight, release() already cleared preparing = false,
                // allowing a new init() call for the new generation to proceed unimpeded.
                synchronized(this) {
                    if (myGeneration == generation) {
                        preparing = false
                    }
                }
            }
        }
    }

    private fun play(sfx: Sfx, enabled: Boolean, volume: Float = 1f, rate: Float = 1f) {
        if (!enabled || !audioFocusGranted || !appForeground) return
        if (!AudioMix.isAudible(masterVolume)) return

        val gain = AudioMix.gain(masterVolume, effectsVolume) * volume.coerceIn(0f, 1f)
        if (gain <= AudioMix.SILENCE_THRESHOLD) return

        val now = System.currentTimeMillis()
        val previous = lastPlayedAt[sfx] ?: 0L
        if (now - previous < MIN_REPEAT_MS) return
        lastPlayedAt[sfx] = now

        val soundPool = pool ?: return
        val id = loadedIds[sfx] ?: return
        try {
            soundPool.play(id, gain, gain, 1, 0, rate.coerceIn(0.5f, 2f))
        } catch (_: Throwable) {
            // Never let audio break gameplay.
        }
    }

    // ---- Public event mapping -------------------------------------------------------------

    fun playTap(enabled: Boolean) = play(Sfx.Tap, enabled, 0.65f)
    fun playFlip(enabled: Boolean) = play(Sfx.Flip, enabled, 0.85f)

    /** Match chime whose pitch climbs the pentatonic ladder as the combo grows. */
    fun playMatch(combo: Int, enabled: Boolean) =
        play(Sfx.Match, enabled, 0.9f, AudioMix.comboRate(combo))

    /** Replays the neutral match chime (previews, legacy call sites). */
    fun playMatch(enabled: Boolean) = playMatch(0, enabled)

    fun playMismatch(enabled: Boolean) = play(Sfx.Mismatch, enabled, 0.85f)
    fun playHint(enabled: Boolean) = play(Sfx.Hint, enabled, 0.8f)
    fun playShuffle(enabled: Boolean) = play(Sfx.Shuffle, enabled, 0.7f)
    fun playCombo(enabled: Boolean) = play(Sfx.Combo, enabled, 0.8f)
    fun playCoin(enabled: Boolean) = play(Sfx.Coin, enabled, 0.9f)
    fun playAchievement(enabled: Boolean) = play(Sfx.Achievement, enabled, 0.9f)
    fun playVictory(enabled: Boolean) = play(Sfx.Victory, enabled, 0.95f)
    fun playDefeat(enabled: Boolean) = play(Sfx.Defeat, enabled, 0.85f)
    fun playPause(enabled: Boolean) = play(Sfx.Pause, enabled, 0.55f)

    /** One rising bell per earned star; [index] is 0-based so the three stars climb together. */
    fun playStar(index: Int, enabled: Boolean) =
        play(Sfx.Star, enabled, 0.8f, 1f + 0.12f * index.coerceIn(0, 2))

    /** Timer warning. [urgent] also lifts the pitch and volume for the final three seconds. */
    fun playTick(urgent: Boolean, enabled: Boolean) =
        play(Sfx.Tick, enabled, if (urgent) 0.62f else 0.38f, if (urgent) 1.12f else 1f)

    fun playWhoosh(enabled: Boolean) = play(Sfx.Whoosh, enabled, 0.55f)
    fun playBoss(enabled: Boolean) = play(Sfx.Boss, enabled, 0.9f)
    fun playChest(enabled: Boolean) = play(Sfx.Chest, enabled, 0.9f)

    /** Frees native audio resources. Safe to call from the Activity's onDestroy. */
    fun release() {
        synchronized(this) {
            generation++
            preparing = false
            try {
                pool?.release()
            } catch (_: Throwable) {
            }
            pool = null
            loadedIds.clear()
            lastPlayedAt.clear()
        }
    }

    // ---- Synthesis -------------------------------------------------------------------------

    private enum class Wave { SINE, TRIANGLE, BELL }

    private fun emptyBuffer(durationSec: Double) = FloatArray((durationSec * SAMPLE_RATE).toInt())

    private fun samplesToSeconds(samples: Int) = samples.toDouble() / SAMPLE_RATE

    /**
     * Adds one voice with a soft attack and exponential decay. [attackSec] keeps transients from
     * clicking; the decay constant is tied to the note length so every voice fades out naturally.
     */
    private fun addTone(
        buffer: FloatArray,
        startSec: Double,
        durationSec: Double,
        freq: Double,
        amplitude: Double,
        wave: Wave = Wave.SINE,
        attackSec: Double = 0.008,
        vibrato: Double = 0.0,
    ) {
        val start = (startSec * SAMPLE_RATE).toInt().coerceAtLeast(0)
        val count = (durationSec * SAMPLE_RATE).toInt()
        if (count <= 0) return
        val decay = durationSec * 0.32
        for (i in 0 until count) {
            val index = start + i
            if (index >= buffer.size) break
            val t = i.toDouble() / SAMPLE_RATE
            val attack = if (attackSec <= 0.0) 1.0 else min(1.0, t / attackSec)
            val envelope = attack * exp(-t / decay)
            val phase = freq * t + if (vibrato > 0.0) 0.0025 * sin(2.0 * PI * 5.5 * t) * vibrato else 0.0
            val angle = 2.0 * PI * phase
            val sample = when (wave) {
                Wave.SINE -> sin(angle)
                Wave.TRIANGLE -> 2.0 / PI * asin(sin(angle))
                Wave.BELL -> sin(angle) + 0.33 * sin(2 * angle) + 0.14 * sin(3 * angle)
            }
            buffer[index] += (sample * amplitude * envelope).toFloat()
        }
    }

    /** Adds a deterministic filtered noise burst: card flicks, rustles, whooshes. */
    private fun addNoise(
        buffer: FloatArray,
        startSec: Double,
        durationSec: Double,
        amplitude: Double,
        seed: Int,
        cutoffHz: Double,
    ) {
        val start = (startSec * SAMPLE_RATE).toInt().coerceAtLeast(0)
        val count = (durationSec * SAMPLE_RATE).toInt()
        if (count <= 0) return
        val random = Random(seed)
        val alpha = 1.0 - exp(-2.0 * PI * cutoffHz / SAMPLE_RATE)
        var lowPass = 0.0
        for (i in 0 until count) {
            val index = start + i
            if (index >= buffer.size) break
            val white = random.nextDouble(-1.0, 1.0)
            lowPass += alpha * (white - lowPass)
            val t = i.toDouble() / SAMPLE_RATE
            // Fast attack, smooth fade: reads as "air" rather than a click.
            val attack = min(1.0, t / 0.006)
            val envelope = attack * exp(-t / (durationSec * 0.38))
            buffer[index] += (lowPass * amplitude * envelope).toFloat()
        }
    }

    private fun renderSfx(sfx: Sfx): ShortArray {
        val buffer = when (sfx) {
            Sfx.Tap -> emptyBuffer(0.07).also { b ->
                addTone(b, 0.0, 0.07, 1180.0, 0.16, Wave.SINE, attackSec = 0.003)
            }

            Sfx.Flip -> emptyBuffer(0.13).also { b ->
                addNoise(b, 0.0, 0.085, 0.30, seed = 11, cutoffHz = 3200.0)
                addTone(b, 0.012, 0.075, 880.0, 0.10, Wave.TRIANGLE, attackSec = 0.004)
            }

            Sfx.Match -> emptyBuffer(0.36).also { b ->
                addTone(b, 0.0, 0.20, 659.25, 0.24, Wave.BELL)      // E5
                addTone(b, 0.085, 0.26, 987.77, 0.20, Wave.BELL)    // B5
            }

            Sfx.Mismatch -> emptyBuffer(0.24).also { b ->
                addTone(b, 0.0, 0.10, 233.08, 0.26, Wave.TRIANGLE, attackSec = 0.014)  // Bb3
                addTone(b, 0.075, 0.14, 196.00, 0.20, Wave.TRIANGLE, attackSec = 0.014) // G3
            }

            Sfx.Hint -> emptyBuffer(0.32).also { b ->
                addTone(b, 0.0, 0.16, 1567.98, 0.13, Wave.SINE, attackSec = 0.004)   // G6
                addTone(b, 0.045, 0.16, 2093.00, 0.11, Wave.SINE, attackSec = 0.004) // C7
                addTone(b, 0.090, 0.18, 2637.02, 0.09, Wave.SINE, attackSec = 0.004) // E7
            }

            Sfx.Shuffle -> emptyBuffer(0.32).also { b ->
                addNoise(b, 0.0, 0.26, 0.26, seed = 23, cutoffHz = 2400.0)
                addNoise(b, 0.05, 0.20, 0.16, seed = 29, cutoffHz = 5200.0)
            }

            Sfx.Combo -> emptyBuffer(0.28).also { b ->
                addTone(b, 0.0, 0.10, 783.99, 0.16, Wave.TRIANGLE, attackSec = 0.005)  // G5
                addTone(b, 0.07, 0.17, 1046.50, 0.16, Wave.TRIANGLE, attackSec = 0.005) // C6
            }

            Sfx.Coin -> emptyBuffer(0.36).also { b ->
                addTone(b, 0.0, 0.14, 1046.50, 0.20, Wave.BELL)   // C6
                addTone(b, 0.075, 0.24, 1567.98, 0.18, Wave.BELL) // G6
            }

            Sfx.Achievement -> emptyBuffer(0.68).also { b ->
                val notes = listOf(523.25, 659.25, 783.99, 1046.50) // C5 E5 G5 C6
                notes.forEachIndexed { index, freq ->
                    addTone(b, index * 0.085, 0.32, freq, 0.15, Wave.TRIANGLE, attackSec = 0.006)
                }
            }

            Sfx.Victory -> emptyBuffer(1.10).also { b ->
                addTone(b, 0.00, 0.26, 523.25, 0.18, Wave.BELL)  // C5
                addTone(b, 0.13, 0.26, 659.25, 0.18, Wave.BELL)  // E5
                addTone(b, 0.26, 0.30, 783.99, 0.20, Wave.BELL)  // G5
                addTone(b, 0.40, 0.68, 1046.50, 0.22, Wave.BELL) // C6
            }

            Sfx.Defeat -> emptyBuffer(0.64).also { b ->
                addTone(b, 0.00, 0.20, 392.00, 0.18, Wave.TRIANGLE, attackSec = 0.012) // G4
                addTone(b, 0.15, 0.22, 329.63, 0.16, Wave.TRIANGLE, attackSec = 0.012) // E4
                addTone(b, 0.30, 0.32, 261.63, 0.15, Wave.TRIANGLE, attackSec = 0.012) // C4
            }

            Sfx.Pause -> emptyBuffer(0.17).also { b ->
                addTone(b, 0.0, 0.15, 440.0, 0.10, Wave.SINE, attackSec = 0.035)
            }

            Sfx.Star -> emptyBuffer(0.30).also { b ->
                addTone(b, 0.0, 0.26, 1046.50, 0.24, Wave.BELL)                    // C6
                addTone(b, 0.02, 0.20, 1567.98, 0.10, Wave.SINE, attackSec = 0.004) // G6 sparkle
            }

            Sfx.Tick -> emptyBuffer(0.09).also { b ->
                addTone(b, 0.0, 0.055, 1320.0, 0.12, Wave.SINE, attackSec = 0.002)
                addNoise(b, 0.0, 0.02, 0.05, seed = 71, cutoffHz = 6000.0)
            }

            Sfx.Whoosh -> emptyBuffer(0.26).also { b ->
                addNoise(b, 0.0, 0.22, 0.22, seed = 37, cutoffHz = 1100.0)
                addNoise(b, 0.06, 0.16, 0.10, seed = 41, cutoffHz = 4200.0)
                addTone(b, 0.03, 0.18, 392.0, 0.05, Wave.SINE, attackSec = 0.02)
            }

            Sfx.Boss -> emptyBuffer(0.62).also { b ->
                addTone(b, 0.0, 0.32, 82.41, 0.34, Wave.SINE, attackSec = 0.006) // E2
                addTone(b, 0.0, 0.50, 55.00, 0.22, Wave.SINE, attackSec = 0.010) // A1
                addNoise(b, 0.0, 0.14, 0.12, seed = 53, cutoffHz = 420.0)
            }

            Sfx.Chest -> emptyBuffer(0.78).also { b ->
                val notes = listOf(1046.50, 1318.51, 1567.98, 2093.00) // C6 E6 G6 C7
                notes.forEachIndexed { index, freq ->
                    addTone(b, index * 0.09, 0.34, freq, 0.17, Wave.BELL, attackSec = 0.004)
                }
                addNoise(b, 0.05, 0.50, 0.04, seed = 61, cutoffHz = 7000.0)
            }
        }
        return toPcm16(buffer)
    }

    /** Normalises to a comfortable peak, applies a 3 ms fade-out, and converts to 16-bit PCM. */
    private fun toPcm16(buffer: FloatArray): ShortArray {
        var peak = 0f
        for (value in buffer) peak = max(peak, abs(value))
        val gain = if (peak > 0.0001f) 0.85f / peak else 1f

        val out = ShortArray(buffer.size)
        val fadeSamples = min(buffer.size, (0.003 * SAMPLE_RATE).toInt())
        for (i in buffer.indices) {
            var value = buffer[i] * gain
            if (i >= buffer.size - fadeSamples) {
                val remaining = (buffer.size - i).toFloat() / fadeSamples
                value *= remaining
            }
            out[i] = (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun renderWav(sfx: Sfx): ByteArray {
        val samples = renderSfx(sfx)
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                          // PCM chunk size
            putShort(1)                         // PCM format
            putShort(1)                         // mono
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2)             // byte rate
            putShort(2)                         // block align
            putShort(16)                        // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        val body = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            samples.forEach { putShort(it) }
        }
        return header.array() + body.array()
    }

    /** Exposed for unit tests: length of a rendered effect in seconds. */
    internal fun renderedDuration(sfx: Sfx): Double = samplesToSeconds(renderSfx(sfx).size)

    /** Exposed for unit tests: peak sample value, so a silent/broken effect fails loudly. */
    internal fun renderedPeak(sfx: Sfx): Int =
        renderSfx(sfx).maxOfOrNull { abs(it.toInt()) } ?: 0
}
