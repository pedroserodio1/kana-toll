package dev.pedro.kanatoll.toll

import android.os.SystemClock
import dev.pedro.kanatoll.AppContainer
import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Source
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.domain.Question
import dev.pedro.kanatoll.domain.buildOptions
import dev.pedro.kanatoll.domain.isCorrect
import dev.pedro.kanatoll.ui.SessionUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Uma rodada do Pedágio: cada cartão é respondido uma vez; errar mostra a resposta certa, grava
 * AGAIN e a rodada segue. As respostas são revisões de verdade (`Source.TOLL`). Ao responder o
 * último, chama [onFinished].
 */
class TollController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val cards: List<Card>,
    private val onFinished: () -> Unit,
) {
    private val repo = container.reviews
    private val _ui = MutableStateFlow<SessionUi>(SessionUi.Loading)
    val ui: StateFlow<SessionUi> = _ui
    val total: Int get() = cards.size

    private var index = 0
    private var shownAt = 0L

    init {
        scope.launch { show() }
    }

    private suspend fun show() {
        val card = cards[index]
        val options =
            if (card.direction == Direction.KANA_TO_ROMAJI) {
                emptyList()
            } else {
                buildOptions(card.kana, card.script, repo.confusedWith(card.kana, 2), repo.introducedKana(card.script))
            }
        shownAt = SystemClock.elapsedRealtime()
        _ui.value = SessionUi.Asking(
            Question(card, retry = false), KanaTable.find(card.script, card.kana), options, remaining = total - index,
        )
    }

    fun submit(response: String) {
        val asking = _ui.value as? SessionUi.Asking ?: return
        val card = asking.q.card
        val correct = isCorrect(card, response)
        val elapsed = SystemClock.elapsedRealtime() - shownAt
        _ui.value = SessionUi.Feedback(asking.q, asking.kana, response, correct, asking.remaining)
        scope.launch { repo.answer(card.id, correct, response.trim(), elapsed, Source.TOLL) }
    }

    fun next() {
        if (_ui.value !is SessionUi.Feedback) return
        index++
        if (index >= total) onFinished() else scope.launch { show() }
    }
}
