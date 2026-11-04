package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Confusion
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.ReviewLog
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.data.Source
import dev.pedro.kanatoll.fsrs.StudyDay
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AccuracyCount(val total: Int, val correct: Int) {
    val rate: Float get() = if (total == 0) 0f else correct.toFloat() / total
}

data class DayCount(val day: LocalDate, val total: Int, val correct: Int)

/**
 * Cartões de leitura e produção de um script (os do portão) por faixa de estabilidade:
 * [fresh] ainda não apresentados, [learning] abaixo de 7 dias, [stable] de 7 a 30, [solid] 30 ou mais.
 */
data class ScriptProgress(
    val introducedKana: Int,
    val totalKana: Int,
    val fresh: Int,
    val learning: Int,
    val stable: Int,
    val solid: Int,
)

/** Faixa de estabilidade de um kana: pelo cartão mais fraco entre leitura e produção. */
enum class KanaLevel { FRESH, LEARNING, STABLE, SOLID }

data class KanaCell(val glyph: String, val level: KanaLevel)

private fun levelOf(stability: Double) = when {
    stability < 7.0 -> KanaLevel.LEARNING
    stability < 30.0 -> KanaLevel.STABLE
    else -> KanaLevel.SOLID
}

data class KanaTrouble(val glyph: String, val romaji: String, val answers: Int, val errors: Int) {
    val errorRate: Float get() = errors.toFloat() / answers
}

data class StatsSummary(
    /** Respostas de primeira (re-perguntas não entram). */
    val overall: AccuracyCount,
    val last7Days: AccuracyCount,
    /** Dias de estudo seguidos que terminam hoje (ou ontem, se hoje ainda não estudou). */
    val streak: Int,
    val studyDays: Int,
    /** Os últimos [PER_DAY_WINDOW] dias de estudo, do mais antigo ao de hoje. */
    val perDay: List<DayCount>,
    val byDirection: Map<Direction, AccuracyCount>,
    val bySource: Map<Source, Int>,
    val scripts: Map<Script, ScriptProgress>,
    /** Os 107 kana de cada script semeado, na ordem de introdução, cada um com a sua faixa. */
    val kanaMap: Map<Script, List<KanaCell>>,
    /** Cartões vencendo hoje (incluindo atrasados) e nos 6 dias seguintes. */
    val dueNext7Days: List<Int>,
    val trouble: List<KanaTrouble>,
    val confusions: List<Confusion>,
) {
    companion object {
        const val PER_DAY_WINDOW = 14
    }
}

/** Calcula as estatísticas a partir do que está no banco. Função pura: o relógio e o fuso entram por parâmetro. */
fun computeStats(
    cards: List<Card>,
    logs: List<ReviewLog>,
    confusions: List<Confusion>,
    nowMs: Long,
    zone: ZoneId,
): StatsSummary {
    val today = StudyDay.of(nowMs, zone)
    val answers = logs.filter { !it.retry }
    val byCard = cards.associateBy { it.id }
    fun count(list: List<ReviewLog>) = AccuracyCount(list.size, list.count { it.correct })

    val daysOf = answers.groupBy { StudyDay.of(it.ts, zone) }
    val perDay = (StatsSummary.PER_DAY_WINDOW - 1 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        val list = daysOf[day].orEmpty()
        DayCount(day, list.size, list.count { it.correct })
    }

    var cursor = if (today in daysOf) today else today.minusDays(1)
    var streak = 0
    while (cursor in daysOf) {
        streak++
        cursor = cursor.minusDays(1)
    }

    val scripts = Script.entries.mapNotNull { script ->
        val mine = cards.filter { it.script == script }
        if (mine.isEmpty()) return@mapNotNull null
        val gate = mine.filter { it.direction != Direction.AUDIO_TO_KANA }
        val introduced = gate.filter { it.introducedAt != null }
        script to ScriptProgress(
            introducedKana = mine.filter { it.introducedAt != null }.map { it.kana }.distinct().size,
            totalKana = mine.map { it.kana }.distinct().size,
            fresh = gate.size - introduced.size,
            learning = introduced.count { it.stability < 7.0 },
            stable = introduced.count { it.stability >= 7.0 && it.stability < 30.0 },
            solid = introduced.count { it.stability >= 30.0 },
        )
    }.toMap()

    val kanaMap = Script.entries.mapNotNull { script ->
        val gate = cards.filter { it.script == script && it.direction != Direction.AUDIO_TO_KANA }
        if (gate.isEmpty()) return@mapNotNull null
        val byGlyph = gate.groupBy { it.kana }
        script to KanaTable.all.map { kana ->
            val glyph = kana.glyph(script)
            val mine = byGlyph[glyph].orEmpty()
            val level = if (mine.all { it.introducedAt == null }) {
                KanaLevel.FRESH
            } else {
                levelOf(mine.minOf { if (it.introducedAt == null) 0.0 else it.stability })
            }
            KanaCell(glyph, level)
        }
    }.toMap()

    val dueNext7 = IntArray(7).also { buckets ->
        cards.filter { it.introducedAt != null }.forEach {
            val offset = ChronoUnit.DAYS.between(today, StudyDay.of(it.due, zone)).coerceAtLeast(0).toInt()
            if (offset < 7) buckets[offset]++
        }
    }.toList()

    val trouble = answers
        .mapNotNull { log -> byCard[log.cardId]?.let { it to log } }
        .groupBy { (card, _) -> card.script to card.kana }
        .map { (key, pairs) ->
            KanaTrouble(key.second, KanaTable.find(key.first, key.second).romaji, pairs.size, pairs.count { !it.second.correct })
        }
        .filter { it.answers >= 3 && it.errors > 0 }
        .sortedWith(compareByDescending<KanaTrouble> { it.errorRate }.thenByDescending { it.answers })
        .take(6)

    return StatsSummary(
        overall = count(answers),
        last7Days = count(answers.filter { StudyDay.of(it.ts, zone) > today.minusDays(7) }),
        streak = streak,
        studyDays = daysOf.size,
        perDay = perDay,
        byDirection = Direction.entries.associateWith { dir -> count(answers.filter { byCard[it.cardId]?.direction == dir }) },
        bySource = Source.entries.associateWith { src -> answers.count { it.source == src } },
        scripts = scripts,
        kanaMap = kanaMap,
        dueNext7Days = dueNext7,
        trouble = trouble,
        confusions = confusions.sortedByDescending { it.count }.take(5),
    )
}
