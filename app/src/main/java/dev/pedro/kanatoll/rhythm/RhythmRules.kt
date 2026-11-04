package dev.pedro.kanatoll.rhythm

import kotlin.math.max
import kotlin.math.min

/** Regras do modo Ritmo: o BPM sobe com os acertos e cai mais depressa com os erros. */
object RhythmRules {
    const val START_BPM = 60
    const val MIN_BPM = 40
    const val MAX_BPM = 160
    const val UP = 4
    const val DOWN = 8

    /** Rodadas por sessão (uma por batida). */
    const val ROUNDS = 32

    /** Quantos kana estáveis são necessários para jogar (a rodada tem 4 opções). */
    const val MIN_POOL = 6

    fun nextBpm(bpm: Int, hit: Boolean): Int = if (hit) min(MAX_BPM, bpm + UP) else max(MIN_BPM, bpm - DOWN)

    /** Duração de uma batida em quadros de áudio. */
    fun beatFrames(bpm: Int, sampleRate: Int): Long = 60L * sampleRate / bpm
}
