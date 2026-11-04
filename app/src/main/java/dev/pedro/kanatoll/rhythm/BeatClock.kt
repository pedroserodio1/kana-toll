package dev.pedro.kanatoll.rhythm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Metrônomo cujo relógio é o próprio áudio: uma thread escreve, batida a batida, um clique e o
 * silêncio até a próxima batida num AudioTrack de baixa latência. A posição de reprodução
 * ([AudioTrack.getPlaybackHeadPosition]) diz em que batida estamos, então o visual acompanha o que
 * se ouve e não um timer à parte. O BPM é lido a cada batida, então mudanças valem na seguinte.
 *
 * ponytail: AudioTrack em vez de Oboe. Com minSdk 34 o Oboe usa o mesmo caminho AAudio do
 * AudioTrack em modo de baixa latência e exigiria NDK, CMake e JNI. Se a latência medida no
 * aparelho incomodar, trocar só esta classe.
 */
class BeatClock(private val bpm: () -> Int) {
    private val sampleRate = 48_000
    private val starts = CopyOnWriteArrayList<Long>() // quadro em que cada batida começa
    private val lengths = CopyOnWriteArrayList<Long>() // duração de cada batida, em quadros
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(minBuffer)
            .build()
        track = t
        running = true
        t.play()
        thread = Thread({ writeBeats(t) }, "beat-clock").apply { start() }
    }

    private fun writeBeats(t: AudioTrack) {
        val click = click()
        val silence = ShortArray(1024)
        var frame = 0L
        while (running) {
            val length = RhythmRules.beatFrames(bpm(), sampleRate)
            starts.add(frame)
            lengths.add(length)
            t.write(click, 0, click.size)
            var remaining = length - click.size
            while (running && remaining > 0) {
                val n = min(remaining, silence.size.toLong()).toInt()
                t.write(silence, 0, n)
                remaining -= n
            }
            frame += length
        }
    }

    private fun playedFrames(): Long = (track?.playbackHeadPosition ?: 0).toLong() and 0xFFFFFFFFL

    /** Quantas batidas já começaram a tocar. */
    fun beatsStarted(): Int {
        val head = playedFrames()
        return starts.count { it <= head }
    }

    /** Fração (0 a 1) da batida atual que já passou. */
    fun beatProgress(): Float {
        val n = beatsStarted()
        if (n == 0) return 0f
        return ((playedFrames() - starts[n - 1]).toFloat() / lengths[n - 1]).coerceIn(0f, 1f)
    }

    fun stop() {
        running = false
        runCatching { track?.pause(); track?.flush() } // destrava a thread, se estiver bloqueada no write
        thread?.join(500)
        runCatching { track?.stop() }
        track?.release()
        track = null
        thread = null
    }

    /** Clique curto: 1,2 kHz com decaimento rápido, 50 ms. */
    private fun click(): ShortArray = ShortArray(sampleRate / 20) { i ->
        val t = i / sampleRate.toFloat()
        (sin(2 * PI * 1200 * t) * exp(-60.0 * t) * 0.6 * Short.MAX_VALUE).toInt().toShort()
    }
}
