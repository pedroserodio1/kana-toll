package dev.pedro.kanatoll.ui

import android.app.Application
import android.os.SystemClock
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.data.Source
import dev.pedro.kanatoll.domain.Kana
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.domain.Question
import dev.pedro.kanatoll.domain.SessionQueue
import dev.pedro.kanatoll.domain.buildOptions
import dev.pedro.kanatoll.domain.isCorrect
import dev.pedro.kanatoll.domain.isNearMiss
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

    /** [canOverride]: o erro parece de digitação, e a resposta só vale depois que você escolhe (continuar ou "foi erro de digitação"). */
    data class Feedback(
        val q: Question,
        val kana: Kana,
        val response: String,
        val correct: Boolean,
        val remaining: Int,
        val canOverride: Boolean = false,
    ) : SessionUi

    /** Resumo das respostas de primeira (re-perguntas não contam). */
    data class Done(val total: Int, val correct: Int, val gateOpened: Script?) : SessionUi
}

/** [done] cartões respondidos de primeira, de [total] na sessão. Re-perguntas não contam. */
data class SessionProgress(val done: Int = 0, val total: Int = 0) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** O que a tela da sessão precisa de quem a conduz: a sessão de estudo e o treino das confusões implementam. */
interface SessionLike {
    val ui: StateFlow<SessionUi>
    val progress: StateFlow<SessionProgress>
    fun submit(response: String)
    fun next()

    /** "Foi erro de digitação": vale como acerto. */
    fun override() {}

    /** Ao sair da tela: grava o que ficou pendente. */
    fun flush() {}

    fun speak(text: String) {}
}

/** Sessão de estudo: os cartões devidos, mais os kana novos do dia. */
open class SessionViewModel(app: Application) : AndroidViewModel(app), SessionLike {
    protected val container = (app as KanaApp).container
    protected val repo = container.reviews

    private val _ui = MutableStateFlow<SessionUi>(SessionUi.Loading)
    override val ui: StateFlow<SessionUi> = _ui

    private val _progress = MutableStateFlow(SessionProgress())
    override val progress: StateFlow<SessionProgress> = _progress

    private var queue = SessionQueue(emptyList())
    private var initialCount = 0
    private var shownAt = 0L
    private var total = 0
    private var correct = 0
    private var gateOpened: Script? = null
    private var lastWrite: Job? = null

    /** Resposta errada que parece erro de digitação, adiada até você escolher o que fazer. */
    private class Pending(val q: Question, val response: String, val elapsed: Long)
    private var pending: Pending? = null

    init {
        viewModelScope.launch {
            queue = SessionQueue(loadQueue())
            initialCount = queue.remaining
            _progress.value = SessionProgress(0, initialCount)
            showNext()
        }
    }

    /** Os cartões da sessão, na ordem em que serão perguntados. Quem herda troca de onde eles vêm. */
    protected open suspend fun loadQueue(): List<Card> {
        repo.seed(Script.HIRAGANA)
        repo.introduceNew()
        val audio = audioAvailable()
        val due = repo.dueCards(100).filter { audio || it.direction != Direction.AUDIO_TO_KANA }
        // Revisões primeiro (mais atrasadas antes); novos embaralhados, para não vir o mesmo kana em sequência.
        val (reviews, fresh) = due.partition { it.reps > 0 }
        return reviews + fresh.shuffled()
    }

    /** Sem voz japonesa no aparelho, os cartões de áudio ficam de fora. */
    protected suspend fun audioAvailable(): Boolean = withTimeoutOrNull(3_000) { container.speaker.ready.await() } ?: false

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

    override fun submit(response: String) {
        val a = _ui.value as? SessionUi.Asking ?: return
        val ok = isCorrect(a.q.card, response)
        val elapsed = SystemClock.elapsedRealtime() - shownAt
        if (!ok && a.q.card.direction == Direction.KANA_TO_ROMAJI && isNearMiss(a.kana, response)) {
            pending = Pending(a.q, response, elapsed)
            _ui.value = SessionUi.Feedback(a.q, a.kana, response, correct = false, remaining = queue.remaining, canOverride = true)
            return
        }
        commit(a.q, response, ok, elapsed)
        _ui.value = SessionUi.Feedback(a.q, a.kana, response, ok, queue.remaining)
    }

    /** Aplica a resposta à fila e ao banco. Escreve no escopo do app: sair da tela não interrompe a gravação. */
    private fun commit(q: Question, response: String, ok: Boolean, elapsed: Long) {
        queue.answered(ok)
        if (!q.retry) {
            total++
            if (ok) correct++
            _progress.value = SessionProgress(total, initialCount)
        }
        val previous = lastWrite
        lastWrite = container.appScope.launch {
            previous?.join() // mantém a ordem das gravações
            repo.answer(q.card.id, ok, response.trim(), elapsed, Source.SESSION, q.retry).gateOpened?.let { gateOpened = it }
            if (!q.retry) KanaWidget().updateAll(getApplication<Application>())
        }
    }

    override fun next() {
        if (_ui.value !is SessionUi.Feedback) return
        pending?.let { commit(it.q, it.response, ok = false, elapsed = it.elapsed) }
        pending = null
        viewModelScope.launch { showNext() }
    }

    override fun override() {
        val p = pending ?: return
        pending = null
        commit(p.q, p.response, ok = true, elapsed = p.elapsed)
        viewModelScope.launch { showNext() }
    }

    override fun flush() {
        val p = pending ?: return
        pending = null // saiu sem escolher: o erro vale como erro
        commit(p.q, p.response, ok = false, elapsed = p.elapsed)
    }

    override fun speak(text: String) = container.speaker.speak(text)
}

/** Estudar à frente: sem nada devido, estuda os próximos cartões a vencer. Não apresenta kana novos. */
class AheadSessionViewModel(app: Application) : SessionViewModel(app) {
    override suspend fun loadQueue(): List<Card> {
        repo.seed(Script.HIRAGANA)
        val audio = audioAvailable()
        return repo.aheadCards(AHEAD_SIZE).filter { audio || it.direction != Direction.AUDIO_TO_KANA }
    }

    private companion object {
        const val AHEAD_SIZE = 10
    }
}
