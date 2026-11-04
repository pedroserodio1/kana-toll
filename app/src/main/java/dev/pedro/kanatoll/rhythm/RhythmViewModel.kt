package dev.pedro.kanatoll.rhythm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.RhythmSession
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.domain.buildOptions
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface RhythmUi {
    data object Loading : RhythmUi

    /** Antes de começar: quantos kana estáveis há e o histórico. */
    data class Idle(val eligible: Int, val bestBpm: Int, val recent: List<RhythmSession>) : RhythmUi

    /** [picked] é a opção tocada nesta rodada (ou null). [correct] é a resposta certa, em rōmaji. */
    data class Playing(
        val glyph: String,
        val options: List<String>,
        val correct: String,
        val picked: String?,
        val bpm: Int,
        val hits: Int,
        val round: Int,
    ) : RhythmUi

    data class Done(val maxBpm: Int, val hits: Int, val rounds: Int, val newBest: Boolean) : RhythmUi
}

/**
 * Leitura sob batida: a cada batida aparece um kana e você tem até a próxima batida para tocar o
 * rōmaji certo. Acertar sobe o BPM; errar ou deixar passar desce. Só usa kana com estabilidade ≥ 7
 * dias e não alimenta o FSRS: as sessões vão para as estatísticas à parte.
 */
class RhythmViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as KanaApp).container
    private val repo = container.reviews
    private val settings = container.settings

    private val _ui = MutableStateFlow<RhythmUi>(RhythmUi.Loading)
    val ui: StateFlow<RhythmUi> = _ui

    /** Fração da batida atual (0 a 1), em fluxo à parte porque muda a cada ~8 ms. */
    private val _phase = MutableStateFlow(0f)
    val phase: StateFlow<Float> = _phase

    private var pool: List<Card> = emptyList()
    private var clock: BeatClock? = null
    private var job: Job? = null

    // Estado da partida em andamento.
    private var bpm = RhythmRules.START_BPM
    private var maxBpm = RhythmRules.START_BPM
    private var hits = 0
    private var round = 0
    private var answered = false
    private var current: Card? = null

    init {
        viewModelScope.launch { showIdle() }
    }

    private suspend fun showIdle() {
        pool = repo.rhythmPool()
        _ui.value = RhythmUi.Idle(pool.size, settings.rhythmBestBpm, settings.rhythmSessions().take(5))
    }

    fun start() {
        if (pool.size < RhythmRules.MIN_POOL || job != null) return
        bpm = RhythmRules.START_BPM
        maxBpm = bpm
        hits = 0
        round = 0
        answered = false
        current = null
        clock = BeatClock { bpm }.also { it.start() }
        job = viewModelScope.launch { loop(clock!!) }
    }

    private suspend fun loop(clock: BeatClock) {
        while (true) {
            delay(8)
            val beats = clock.beatsStarted()
            while (round < beats) {
                if (round > 0 && !answered) bpm = RhythmRules.nextBpm(bpm, hit = false) // deixou passar: erro
                if (round >= RhythmRules.ROUNDS) {
                    finish(rounds = round)
                    return
                }
                round++
                newRound()
            }
            _phase.value = clock.beatProgress()
        }
    }

    private fun newRound() {
        val previous = current
        val card = pool.filter { it != previous }.random()
        val sameScript = pool.filter { it.script == card.script }.map { it.kana }
        val options = buildOptions(card.kana, card.script, emptyList(), sameScript, count = 4)
            .map { KanaTable.find(card.script, it).romaji }
        current = card
        answered = false
        _ui.value = RhythmUi.Playing(
            glyph = card.kana,
            options = options,
            correct = KanaTable.find(card.script, card.kana).romaji,
            picked = null,
            bpm = bpm,
            hits = hits,
            round = round,
        )
    }

    fun pick(option: String) {
        val state = _ui.value as? RhythmUi.Playing ?: return
        if (answered) return
        answered = true
        val hit = option == state.correct
        if (hit) hits++
        bpm = RhythmRules.nextBpm(bpm, hit)
        maxBpm = max(maxBpm, bpm)
        _ui.value = state.copy(picked = option, bpm = bpm, hits = hits)
    }

    /** Encerra a partida (no fim das rodadas, ou pelo botão Parar) e guarda as estatísticas se houve alguma rodada. */
    fun finish(rounds: Int = if (answered) round else max(0, round - 1)) {
        job?.cancel()
        job = null
        clock?.stop()
        clock = null
        if (rounds <= 0) {
            viewModelScope.launch { showIdle() }
            return
        }
        val newBest = maxBpm > settings.rhythmBestBpm
        if (newBest) settings.rhythmBestBpm = maxBpm
        settings.addRhythmSession(RhythmSession(container.clock.millis(), maxBpm, hits, rounds))
        _ui.value = RhythmUi.Done(maxBpm, hits, rounds, newBest)
    }

    fun again() {
        viewModelScope.launch {
            showIdle()
            start()
        }
    }

    /** Ao sair da tela: para o áudio sem guardar nada. */
    fun stop() {
        job?.cancel()
        job = null
        clock?.stop()
        clock = null
    }

    override fun onCleared() = stop()
}
