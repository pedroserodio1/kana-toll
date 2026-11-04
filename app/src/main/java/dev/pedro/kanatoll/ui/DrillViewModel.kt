package dev.pedro.kanatoll.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.domain.DrillItem
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.domain.Question
import dev.pedro.kanatoll.domain.buildDrill
import dev.pedro.kanatoll.domain.buildOptions
import dev.pedro.kanatoll.domain.isCorrect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Treino das confusões: perguntas rápidas de escolha só com os pares de kana que você mais troca.
 * É prática à parte: não grava nada, não mexe no FSRS nem nas confusões.
 */
class DrillViewModel(app: Application) : AndroidViewModel(app), SessionLike {
    private val container = (app as KanaApp).container
    private val repo = container.reviews

    private val _ui = MutableStateFlow<SessionUi>(SessionUi.Loading)
    override val ui: StateFlow<SessionUi> = _ui

    private val _progress = MutableStateFlow(SessionProgress())
    override val progress: StateFlow<SessionProgress> = _progress

    private var items: List<DrillItem> = emptyList()
    private var index = 0
    private var correct = 0

    init {
        viewModelScope.launch {
            items = buildDrill(container.db.confusions().all())
            _progress.value = SessionProgress(0, items.size)
            show()
        }
    }

    private suspend fun show() {
        if (index >= items.size) {
            _ui.value = if (items.isEmpty()) SessionUi.Empty else SessionUi.Done(items.size, correct, gateOpened = null)
            return
        }
        val item = items[index]
        // Cartão de mentira só para reaproveitar as telas de pergunta e de resposta; nunca vai ao banco.
        val card = Card(id = -1, script = item.script, kana = item.target, direction = Direction.ROMAJI_TO_KANA)
        val options = buildOptions(item.target, item.script, listOf(item.other), repo.introducedKana(item.script), count = 3)
        _ui.value = SessionUi.Asking(Question(card, retry = false), KanaTable.find(item.script, item.target), options, items.size - index)
    }

    override fun submit(response: String) {
        val asking = _ui.value as? SessionUi.Asking ?: return
        val ok = isCorrect(asking.q.card, response)
        if (ok) correct++
        index++
        _progress.value = SessionProgress(index, items.size)
        _ui.value = SessionUi.Feedback(asking.q, asking.kana, response, ok, asking.remaining)
    }

    override fun next() {
        if (_ui.value is SessionUi.Feedback) viewModelScope.launch { show() }
    }
}
