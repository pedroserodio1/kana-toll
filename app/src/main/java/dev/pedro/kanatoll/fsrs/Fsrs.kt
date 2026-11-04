package dev.pedro.kanatoll.fsrs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Só AGAIN (erro) e GOOD (acerto) são usados pelo app; HARD e EASY ficam por fidelidade ao algoritmo. */
enum class Rating(val grade: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** Estado de memória do FSRS. (0, 0) = cartão nunca revisado. */
data class Memory(val stability: Double, val difficulty: Double) {
    val isNew: Boolean get() = stability == 0.0 && difficulty == 0.0

    companion object {
        val NEW = Memory(0.0, 0.0)
    }
}

/**
 * Porte do FSRS-6 (fórmulas de `ts-fsrs`, models/fsrs-6/algorithm.ts), no modo de longo prazo:
 * sem passos de aprendizado e sem fuzz. Toda revisão devolve um intervalo em dias.
 * O arredondamento em 8 casas replica o da referência, para os resultados baterem nos vetores de teste.
 *
 * ponytail: o ramo de estabilidade de curto prazo (revisão no mesmo dia com short-term ligado) foi
 * omitido. A re-pergunta de um erro na mesma sessão não é uma revisão do FSRS. Se um dia houver
 * passos de aprendizado, portar `next_short_term_stability` (w[17..19]).
 */
class Fsrs(weights: DoubleArray = DEFAULT_WEIGHTS, val desiredRetention: Double = 0.9) {
    private val w = weights.copyOf()
    private val decay = -w[20]
    private val factor = round8(exp(ln(0.9) / decay) - 1.0)

    init {
        require(w.size == 21) { "FSRS-6 exige 21 pesos, recebeu ${w.size}" }
        require(desiredRetention > 0.0 && desiredRetention <= 1.0) { "retenção deve estar em (0, 1]" }
    }

    /** Probabilidade de lembrar depois de [elapsedDays] dias. */
    fun retrievability(elapsedDays: Int, stability: Double): Double =
        round8((1 + factor * elapsedDays / stability).pow(decay))

    /** Próximo intervalo em dias (mínimo 1) para a retenção desejada. */
    fun intervalDays(stability: Double): Int {
        val raw = stability / factor * (desiredRetention.pow(1 / decay) - 1)
        return clamp(jsRound(raw), 1.0, MAX_INTERVAL).toInt()
    }

    fun next(memory: Memory, elapsedDays: Int, rating: Rating): Memory {
        require(elapsedDays >= 0) { "elapsedDays negativo: $elapsedDays" }
        val g = rating.grade
        if (memory.isNew) {
            return Memory(initStability(g), clamp(initDifficulty(g), 1.0, 10.0))
        }
        val (s, d) = memory.stability to memory.difficulty
        require(d >= 1.0 && s >= S_MIN) { "estado de memória inválido: $memory" }
        val r = retrievability(elapsedDays, s)
        val newS = if (g == 1) {
            // Com short-term desligado, o piso é a própria estabilidade: s / exp(0).
            clamp(round8(s), S_MIN, forgetStability(d, s, r))
        } else {
            recallStability(d, s, r, g)
        }
        return Memory(newS, nextDifficulty(d, g))
    }

    private fun initStability(g: Int) = clamp(w[g - 1], S_MIN, S_MAX)

    private fun initDifficulty(g: Int) = round8(w[4] - exp((g - 1) * w[5]) + 1)

    private fun nextDifficulty(d: Double, g: Int): Double {
        val deltaD = -w[6] * (g - 3)
        val nextD = d + round8(deltaD * (10 - d) / 9) // amortecimento linear
        return clamp(meanReversion(initDifficulty(Rating.EASY.grade), nextD), 1.0, 10.0)
    }

    private fun meanReversion(init: Double, current: Double) = round8(w[7] * init + (1 - w[7]) * current)

    private fun recallStability(d: Double, s: Double, r: Double, g: Int): Double {
        val hardPenalty = if (g == Rating.HARD.grade) w[15] else 1.0
        val easyBonus = if (g == Rating.EASY.grade) w[16] else 1.0
        return round8(
            clamp(
                s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus),
                S_MIN, S_MAX,
            ),
        )
    }

    private fun forgetStability(d: Double, s: Double, r: Double) = round8(
        clamp(w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14]), S_MIN, S_MAX),
    )

    companion object {
        const val S_MIN = 0.001
        const val S_MAX = 36500.0
        const val MAX_INTERVAL = 36500.0

        /** Pesos padrão do FSRS-6 (ts-fsrs FSRS6_DEFAULT_WEIGHTS); w[20] é o decay. */
        val DEFAULT_WEIGHTS = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        // Math.round do JS arredonda .5 para cima; kotlin.math.round arredonda para o par.
        private fun jsRound(x: Double) = Math.floor(x + 0.5)
        private fun round8(x: Double) = Math.floor(x * 1e8 + 0.5) / 1e8
        private fun clamp(x: Double, lo: Double, hi: Double) = min(max(x, lo), hi)
    }
}
