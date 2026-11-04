package dev.pedro.kanatoll.ui

import android.app.Application
import android.os.SystemClock
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.data.Source
import dev.pedro.kanatoll.domain.Kana
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.domain.Question
import dev.pedro.kanatoll.domain.SessionQueue
import dev.pedro.kanatoll.domain.buildOptions
import dev.pedro.kanatoll.domain.isCorrect
import dev.pedro.kanatoll.widget.KanaWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SessionUi {
    data object Loading : SessionUi
    data object Empty : SessionUi

    /** [options] só existe nas direções de escolha. */
    data class Asking(val q: Question, val kana: Kana, val options: List<String>, val remaining: Int) : SessionUi

    data class Feedback(val q: Question, val kana: Kana, val response: String, val correct: Boolean, val remaining: Int) : SessionUi

    /** Resumo das respostas de primeira (re-perguntas não contam). */
    data class Done(val total: Int, val correct: Int, val gateOpened: Script?) : SessionUi
}

/** [done] cartões respondidos de primeira, de [total] na sessão. Re-perguntas não contam. */
data class SessionProgress(val done: Int = 0, val total: Int = 0) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as KanaApp).container
    private val repo = container.reviews

    private val _ui = MutableStateFlow<SessionUi>(SessionUi.Loading)
    val ui: StateFlow<SessionUi> = _ui

    private val _progress = MutableStateFlow(SessionProgress())
    val progress: StateFlow<SessionProgress> = _progress

    private var queue = SessionQueue(emptyList())
    private var initialCount = 0
    private var shownAt = 0L
    private var total = 0
    private var correct = 0
    private var gateOpened: Script? = null
    private var lastWrite: Job? = null

    init {
        viewModelScope.launch {
            repo.seed(Script.HIRAGANA)
            repo.introduceNew()
            val audio = withTimeoutOrNull(3_000) { container.speaker.ready.await() } ?: false
            val due = repo.dueCards(100).filter { audio || it.direction != Direction.AUDIO_TO_KANA }
            // Revisões primeiro (mais atrasadas antes); novos embaralhados, para não vir o mesmo kana em sequência.
            val (reviews, fresh) = due.partition { it.reps > 0 }
            queue = SessionQueue(reviews + fresh.shuffled())
            initialCount = queue.remaining
            _progress.value = SessionProgress(0, initialCount)
            showNext()
        }
    }

    private suspend fun showNext() {
        val q = queue.current
        if (q == null) {
            lastWrite?.join()
            _ui.value = if (total == 0) SessionUi.Empty else SessionUi.Done(total, correct, gateOpened)
            return
        }
        val card = q.card
        val options =
            if (card.direction == Direction.KANA_TO_ROMAJI) {
                emptyList()
            } else {
                buildOptions(card.kana, card.script, repo.confusedWith(card.kana, 2), repo.introducedKana(card.script))
            }
        shownAt = SystemClock.elapsedRealtime()
        _ui.value = SessionUi.Asking(q, KanaTable.find(card.script, card.kana), options, queue.remaining)
    }

    fun submit(response: String) {
        val a = _ui.value as? SessionUi.Asking ?: return
        val ok = isCorrect(a.q.card, response)
        val elapsed = SystemClock.elapsedRealtime() - shownAt
        queue.answered(ok)
        if (!a.q.retry) {
            total++
            if (ok) correct++
            _progress.value = SessionProgress(total, initialCount)
        }
        _ui.value = SessionUi.Feedback(a.q, a.kana, response, ok, queue.remaining)
        lastWrite = viewModelScope.launch {
            repo.answer(a.q.card.id, ok, response.trim(), elapsed, Source.SESSION, a.q.retry)
                .gateOpened?.let { gateOpened = it }
            if (!a.q.retry) KanaWidget().updateAll(getApplication<Application>())
        }
    }

    fun next() {
        if (_ui.value is SessionUi.Feedback) viewModelScope.launch { showNext() }
    }

    fun speak(text: String) = container.speaker.speak(text)
}
