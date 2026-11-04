package dev.pedro.kanatoll.data

import androidx.room.withTransaction
import dev.pedro.kanatoll.domain.GATE_STABILITY
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.domain.AccuracyCount
import dev.pedro.kanatoll.domain.StatsSummary
import dev.pedro.kanatoll.domain.computeStats
import dev.pedro.kanatoll.domain.applyAnswer
import dev.pedro.kanatoll.domain.wrongKana
import dev.pedro.kanatoll.fsrs.Fsrs
import dev.pedro.kanatoll.fsrs.StudyDay
import java.time.Clock

/**
 * [intervalDays] é `null` numa re-pergunta (o cartão não mudou).
 * [gateOpened] é o script cujo portão abriu com esta resposta, se abriu.
 */
data class AnswerResult(val card: Card, val intervalDays: Int?, val gateOpened: Script?)

data class GateProgress(val passed: Int, val total: Int)

/** [newLeft] = kana novos que ainda podem ser apresentados hoje. [katakana] só existe com o portão do hiragana aberto. */
data class HomeStats(
    val due: Int,
    val newLeft: Int,
    val hiragana: GateProgress,
    val hiraganaOpen: Boolean,
    val katakana: GateProgress?,
    /** Respostas de primeira de hoje e a meta diária (0 = sem meta). */
    val todayAnswers: Int,
    val goal: Int,
)

/**
 * Único caminho de revisão: sessão, notificação e Pedágio chamam [answer]. Assim a lógica de
 * FSRS, confusões e portão não diverge entre as superfícies.
 */
class ReviewRepository(
    private val db: AppDatabase,
    private val settings: Settings,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    // Lidas a cada uso, para uma mudança nas Configurações valer na hora.
    private val fsrs get() = Fsrs(desiredRetention = settings.retention)
    private val newKanaPerDay get() = settings.newKanaPerDay

    private val cards = db.cards()
    private val logs = db.reviewLogs()
    private val confusions = db.confusions()
    private val gates = db.gates()

    /** Cria os 321 cartões do script, uma vez. Idempotente. */
    suspend fun seed(script: Script) {
        if (cards.gateTotal(script) == 0) cards.insertAll(KanaTable.cards(script))
    }

    /**
     * Registra uma resposta. [retry] marca a re-pergunta de um erro na mesma sessão: entra no log
     * e nas confusões, mas não mexe no cartão nem no FSRS.
     */
    suspend fun answer(
        cardId: Long,
        correct: Boolean,
        given: String,
        elapsedMs: Long,
        source: Source,
        retry: Boolean = false,
    ): AnswerResult = db.withTransaction {
        val card = cards.get(cardId) ?: error("cartão $cardId não existe")
        val now = clock.millis()
        logs.insert(ReviewLog(cardId = cardId, ts = now, correct = correct, given = given, elapsedMs = elapsedMs, source = source, retry = retry))
        if (!correct) wrongKana(card, given)?.let { confusions.bump(card.kana, it) }
        if (retry) return@withTransaction AnswerResult(card, null, null)

        val fsrs = fsrs
        val updated = applyAnswer(card, correct, now, clock.zone, fsrs)
        cards.update(updated)
        // Só um acerto que cruza o limiar pode ter acabado de abrir o portão.
        val opened = if (correct && updated.stability >= GATE_STABILITY) openGateIfReady(card.script, now) else null
        AnswerResult(updated, fsrs.intervalDays(updated.stability), opened)
    }

    /** Portão: todo cartão de leitura e produção com estabilidade ≥ 7 e última resposta correta. Nunca fecha. */
    private suspend fun openGateIfReady(script: Script, now: Long): Script? {
        if (gates.isOpen(script)) return null
        val total = cards.gateTotal(script)
        if (total == 0 || cards.gatePassed(script, GATE_STABILITY) != total) return null
        gates.open(GateState(script, now))
        if (script == Script.HIRAGANA) seed(Script.KATAKANA)
        return script
    }

    suspend fun isGateOpen(script: Script) = gates.isOpen(script)

    suspend fun gateProgress(script: Script) =
        GateProgress(cards.gatePassed(script, GATE_STABILITY), cards.gateTotal(script))

    suspend fun dueCards(limit: Int = 50): List<Card> = cards.due(clock.millis(), limit)

    suspend fun dueCount(): Int = cards.dueCount(clock.millis())

    /** Os próximos [n] cartões a vencer, para estudar à frente quando não há nada devido. */
    suspend fun aheadCards(n: Int): List<Card> = cards.upcoming(clock.millis(), n)

    /** Respostas de primeira de hoje (dia de estudo, que vira às 04:00) e quantas foram certas. */
    suspend fun todayAnswers(): AccuracyCount {
        val since = StudyDay.startOf(clock.millis(), clock.zone)
        return AccuracyCount(logs.answersSince(since), logs.correctSince(since))
    }

    /** Estatísticas calculadas sobre todo o histórico (cartões, respostas e confusões). */
    suspend fun stats(): StatsSummary =
        computeStats(cards.all(), logs.all(), confusions.all(), clock.millis(), clock.zone)

    /** O cartão kana → rōmaji mais atrasado: o único tipo que dá para responder digitando na notificação. */
    suspend fun nextQuizCard(): Card? =
        cards.dueByDirection(clock.millis(), Direction.KANA_TO_ROMAJI, 1).firstOrNull()

    /**
     * Os [n] cartões de uma rodada do Pedágio: devidos primeiro; se faltar, os de menor estabilidade.
     * Vazio se você ainda não estudou nenhum kana (então não há o que cobrar).
     */
    suspend fun tollCards(n: Int = 3): List<Card> {
        val due = cards.dueSilent(clock.millis(), n)
        val fill = if (due.size < n) cards.weakestSilent(due.map { it.id }, n - due.size) else emptyList()
        return (due + fill).shuffled()
    }

    /** Kana que o modo Ritmo pode usar: leitura com estabilidade ≥ 7 dias (o mesmo limiar do portão). */
    suspend fun rhythmPool(): List<Card> = cards.stableReading(GATE_STABILITY)

    /** Para a notificação de teste: um cartão kana → rōmaji qualquer, se nenhum estiver devido. */
    suspend fun anyQuizCard(): Card? = cards.randomIntroduced(Direction.KANA_TO_ROMAJI)

    suspend fun introducedKana(script: Script): List<String> = cards.introducedKana(script)

    /** Kana que você mais trocou por [kana], para reforçar o par nas opções. */
    suspend fun confusedWith(kana: String, limit: Int): List<String> = confusions.topGiven(kana, limit)

    private suspend fun newLeftToday(script: Script): Int {
        val left = newKanaPerDay - cards.introducedKanaSince(script, StudyDay.startOf(clock.millis(), clock.zone))
        return if (left <= 0) 0 else cards.nextNewKana(script, left).size
    }

    suspend fun homeStats(): HomeStats {
        seed(Script.HIRAGANA)
        val open = gates.isOpen(Script.HIRAGANA)
        return HomeStats(
            due = dueCount(),
            newLeft = newLeftToday(if (open) Script.KATAKANA else Script.HIRAGANA),
            hiragana = gateProgress(Script.HIRAGANA),
            hiraganaOpen = open,
            katakana = if (open) gateProgress(Script.KATAKANA) else null,
            todayAnswers = todayAnswers().total,
            goal = settings.dailyGoal,
        )
    }

    /**
     * Apresenta os kana novos do dia (até [newKanaPerDay] por dia de estudo, 3 cartões cada) e os
     * devolve. Hiragana até o portão abrir; depois, katakana.
     */
    suspend fun introduceNew(): List<String> = db.withTransaction {
        val script = if (gates.isOpen(Script.HIRAGANA)) Script.KATAKANA else Script.HIRAGANA
        seed(script)
        val now = clock.millis()
        val left = newKanaPerDay - cards.introducedKanaSince(script, StudyDay.startOf(now, clock.zone))
        if (left <= 0) return@withTransaction emptyList()
        cards.nextNewKana(script, left).also { if (it.isNotEmpty()) cards.introduce(script, it, now) }
    }
}
