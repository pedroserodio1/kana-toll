package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.CardState
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.fsrs.Fsrs
import dev.pedro.kanatoll.fsrs.Memory
import dev.pedro.kanatoll.fsrs.Rating
import dev.pedro.kanatoll.fsrs.StudyDay
import java.time.ZoneId

/** O portão de um script abre quando todo cartão de leitura e produção chega a esta estabilidade (dias). */
const val GATE_STABILITY = 7.0

/**
 * Aplica uma resposta ao cartão (função pura). Teste ativo: acerto = GOOD, erro = AGAIN.
 * No modo de longo prazo todo cartão revisado fica em REVIEW; o estado RELEARNING não é usado.
 */
fun applyAnswer(card: Card, correct: Boolean, nowMs: Long, zone: ZoneId, fsrs: Fsrs): Card {
    val elapsed = card.lastReview?.let { StudyDay.elapsedDays(it, nowMs, zone) } ?: 0
    val next = fsrs.next(
        Memory(card.stability, card.difficulty),
        elapsed,
        if (correct) Rating.GOOD else Rating.AGAIN,
    )
    return card.copy(
        state = CardState.REVIEW,
        stability = next.stability,
        difficulty = next.difficulty,
        due = StudyDay.dueAt(nowMs, fsrs.intervalDays(next.stability), zone),
        lastReview = nowMs,
        reps = card.reps + 1,
        lapses = card.lapses + if (!correct && card.state != CardState.NEW) 1 else 0,
        lastCorrect = correct,
    )
}

/**
 * Qual kana foi dado no lugar de [card] num erro, para registrar o par confundido.
 * Nas direções de escolha, [given] já é o kana. Em kana → rōmaji, [given] é o texto digitado:
 * vira o kana que ele nomearia, ou `null` se não nomear nenhum.
 */
fun wrongKana(card: Card, given: String): String? {
    val kana =
        if (card.direction == Direction.KANA_TO_ROMAJI) {
            KanaTable.all.firstOrNull { it.accepts(given) }?.glyph(card.script)
        } else {
            given
        }
    return kana?.takeIf { it != card.kana }
}
