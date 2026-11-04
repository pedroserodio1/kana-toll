package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Direction

/** [retry]: re-pergunta de um erro na mesma sessão (não conta como revisão do FSRS). */
data class Question(val card: Card, val retry: Boolean)

/** Fila da sessão. Um erro põe o mesmo cartão de volta no fim, como re-pergunta. */
class SessionQueue(cards: List<Card>) {
    private val queue = ArrayDeque(cards.map { Question(it, retry = false) })

    val current: Question? get() = queue.firstOrNull()
    val remaining: Int get() = queue.size

    fun answered(correct: Boolean) {
        val q = queue.removeFirst()
        if (!correct) queue.addLast(Question(q.card, retry = true))
    }
}

/** Kana → rōmaji: o texto digitado tem de estar entre as variantes aceitas. Nas outras direções, é a escolha exata. */
fun isCorrect(card: Card, response: String): Boolean =
    if (card.direction == Direction.KANA_TO_ROMAJI) {
        KanaTable.find(card.script, card.kana).accepts(response)
    } else {
        response == card.kana
    }
