package dev.pedro.kanatoll.trace

import kotlin.math.hypot

data class Pt(val x: Float, val y: Float)

enum class Verdict {
    /** O traço bate com o esperado, na direção certa. */
    OK,

    /** O formato bate, mas foi desenhado de trás para frente. */
    WRONG_DIRECTION,

    /** É um traço do kana, mas vem mais tarde na ordem. */
    WRONG_ORDER,

    /** Não parece nenhum traço que falte. */
    MISS,
}

/**
 * Compara um traço desenhado com os traços de referência do KanjiVG (todos no mesmo sistema de
 * coordenadas, 109 unidades por casa). Reamostra os dois em [N] pontos por comprimento de arco e
 * mede a distância média ponto a ponto; isso exige formato, posição e direção.
 */
object StrokeMatcher {
    const val N = 32

    /** Distância média máxima (em unidades de 109) para aceitar um traço: cerca de 14% da casa. */
    const val TOLERANCE = 15f

    /** Traços de referência menores que isto (pontinhos do dakuten) são comparados só pela posição. */
    private const val SHORT = 14f

    fun length(points: List<Pt>): Float =
        points.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }.toFloat()

    /** [n] pontos igualmente espaçados ao longo do traço. Traço sem comprimento vira [n] cópias do ponto. */
    fun resample(points: List<Pt>, n: Int = N): List<Pt> {
        require(points.isNotEmpty())
        val total = length(points)
        if (total == 0f) return List(n) { points.first() }
        val cumulative = FloatArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        var j = 0
        return List(n) { k ->
            val target = total * k / (n - 1)
            while (j < points.size - 2 && cumulative[j + 1] < target) j++
            val seg = cumulative[j + 1] - cumulative[j]
            val t = if (seg == 0f) 0f else (target - cumulative[j]) / seg
            Pt(points[j].x + (points[j + 1].x - points[j].x) * t, points[j].y + (points[j + 1].y - points[j].y) * t)
        }
    }

    private fun meanDistance(a: List<Pt>, b: List<Pt>): Float =
        a.indices.sumOf { hypot(a[it].x - b[it].x, a[it].y - b[it].y).toDouble() }.toFloat() / a.size

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    private fun matches(user: List<Pt>, ref: List<Pt>, tolerance: Float): Boolean {
        val refLength = length(ref)
        val u = resample(user)
        val r = resample(ref)
        if (refLength < SHORT) {
            // Pontinho: basta estar no lugar e não ser um risco comprido.
            return dist(u[N / 2], r[N / 2]) <= tolerance && length(user) <= 3 * tolerance
        }
        return length(user) >= 0.4f * refLength &&
            meanDistance(u, r) <= tolerance &&
            dist(u.first(), r.first()) <= tolerance * 1.6f &&
            dist(u.last(), r.last()) <= tolerance * 1.6f
    }

    /**
     * Julga [user] contra o traço esperado ([expected], índice em [strokes]).
     * Só olha os traços que ainda faltam (do esperado em diante).
     */
    fun judge(user: List<Pt>, strokes: List<List<Pt>>, expected: Int, tolerance: Float = TOLERANCE): Verdict {
        if (user.isEmpty()) return Verdict.MISS
        if (matches(user, strokes[expected], tolerance)) return Verdict.OK
        if (matches(user.reversed(), strokes[expected], tolerance)) return Verdict.WRONG_DIRECTION
        for (i in expected + 1 until strokes.size) {
            if (matches(user, strokes[i], tolerance)) return Verdict.WRONG_ORDER
        }
        return Verdict.MISS
    }
}
