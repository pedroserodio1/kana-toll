package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.CardState
import dev.pedro.kanatoll.data.Confusion
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.ReviewLog
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.data.Source
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(day: Int, hour: Int = 12) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val now = ms(10)

    private fun card(id: Long, kana: String, dir: Direction, stability: Double = 0.0, introduced: Boolean = true, due: Long = ms(10)) =
        Card(
            id = id, script = Script.HIRAGANA, kana = kana, direction = dir,
            state = if (stability > 0) CardState.REVIEW else CardState.NEW,
            stability = stability, due = due, introducedAt = if (introduced) ms(1) else null,
        )

    private fun log(cardId: Long, day: Int, correct: Boolean, retry: Boolean = false, source: Source = Source.SESSION) =
        ReviewLog(cardId = cardId, ts = ms(day), correct = correct, given = "x", elapsedMs = 1, source = source, retry = retry)

    @Test fun `precisao so conta respostas de primeira`() {
        val cards = listOf(card(1, "あ", Direction.KANA_TO_ROMAJI))
        val logs = listOf(log(1, 10, true), log(1, 10, false), log(1, 10, false, retry = true), log(1, 10, true, retry = true))
        val s = computeStats(cards, logs, emptyList(), now, zone)
        assertEquals(AccuracyCount(2, 1), s.overall)
        assertEquals(0.5f, s.overall.rate, 1e-6f)
    }

    @Test fun `sequencia termina hoje ou ontem e quebra no primeiro dia vazio`() {
        val cards = listOf(card(1, "あ", Direction.KANA_TO_ROMAJI))
        // estudou 10, 9, 8, 6 (o dia 7 ficou vazio)
        val logs = listOf(10, 9, 8, 6).map { log(1, it, true) }
        assertEquals(3, computeStats(cards, logs, emptyList(), now, zone).streak)
        // sem estudo hoje: conta a partir de ontem
        assertEquals(2, computeStats(cards, listOf(9, 8).map { log(1, it, true) }, emptyList(), now, zone).streak)
        assertEquals(0, computeStats(cards, listOf(log(1, 7, true)), emptyList(), now, zone).streak)
    }

    @Test fun `dias de estudo e janela de 14 dias`() {
        val cards = listOf(card(1, "あ", Direction.KANA_TO_ROMAJI))
        val s = computeStats(cards, listOf(log(1, 10, true), log(1, 10, false), log(1, 5, true)), emptyList(), now, zone)
        assertEquals(2, s.studyDays)
        assertEquals(StatsSummary.PER_DAY_WINDOW, s.perDay.size)
        assertEquals(DayCount(s.perDay.last().day, 2, 1), s.perDay.last())
        assertEquals(1, s.perDay.single { it.day.dayOfMonth == 5 }.total)
    }

    @Test fun `faixas de estabilidade contam so os cartoes do portao`() {
        val cards = listOf(
            card(1, "あ", Direction.KANA_TO_ROMAJI, stability = 2.0),
            card(2, "あ", Direction.ROMAJI_TO_KANA, stability = 10.0),
            card(3, "あ", Direction.AUDIO_TO_KANA, stability = 99.0), // áudio não conta
            card(4, "い", Direction.KANA_TO_ROMAJI, stability = 45.0),
            card(5, "う", Direction.KANA_TO_ROMAJI, introduced = false),
        )
        val p = computeStats(cards, emptyList(), emptyList(), now, zone).scripts.getValue(Script.HIRAGANA)
        assertEquals(ScriptProgress(introducedKana = 2, totalKana = 3, fresh = 1, learning = 1, stable = 1, solid = 1), p)
    }

    @Test fun `carga dos proximos 7 dias inclui atrasados em hoje`() {
        val cards = listOf(
            card(1, "あ", Direction.KANA_TO_ROMAJI, due = ms(8)),  // atrasado -> hoje
            card(2, "い", Direction.KANA_TO_ROMAJI, due = ms(10)), // hoje
            card(3, "う", Direction.KANA_TO_ROMAJI, due = ms(12)), // daqui a 2 dias
            card(4, "え", Direction.KANA_TO_ROMAJI, due = ms(30)), // fora da janela
            card(5, "お", Direction.KANA_TO_ROMAJI, due = ms(10), introduced = false), // não apresentado
        )
        assertEquals(listOf(2, 0, 1, 0, 0, 0, 0), computeStats(cards, emptyList(), emptyList(), now, zone).dueNext7Days)
    }

    @Test fun `onde voce erra exige 3 respostas e ordena pela taxa de erro`() {
        val cards = listOf(card(1, "し", Direction.KANA_TO_ROMAJI), card(2, "つ", Direction.KANA_TO_ROMAJI), card(3, "あ", Direction.KANA_TO_ROMAJI))
        val logs = listOf(
            log(1, 10, false), log(1, 9, false), log(1, 8, true),   // し: 2/3 erros
            log(2, 10, false), log(2, 9, true), log(2, 8, true), log(2, 7, true), // つ: 1/4
            log(3, 10, true), log(3, 9, true), log(3, 8, true),     // あ: nenhum erro, fora
        )
        val trouble = computeStats(cards, logs, emptyList(), now, zone).trouble
        assertEquals(listOf("し", "つ"), trouble.map { it.glyph })
        assertEquals("shi", trouble.first().romaji)
        assertTrue(trouble.first().errorRate > trouble.last().errorRate)
    }

    @Test fun `por tipo de cartao e por origem`() {
        val cards = listOf(card(1, "あ", Direction.KANA_TO_ROMAJI), card(2, "あ", Direction.ROMAJI_TO_KANA))
        val logs = listOf(log(1, 10, true), log(1, 9, false), log(2, 10, true, source = Source.TOLL))
        val s = computeStats(cards, logs, emptyList(), now, zone)
        assertEquals(AccuracyCount(2, 1), s.byDirection.getValue(Direction.KANA_TO_ROMAJI))
        assertEquals(AccuracyCount(1, 1), s.byDirection.getValue(Direction.ROMAJI_TO_KANA))
        assertEquals(AccuracyCount(0, 0), s.byDirection.getValue(Direction.AUDIO_TO_KANA))
        assertEquals(2, s.bySource.getValue(Source.SESSION))
        assertEquals(1, s.bySource.getValue(Source.TOLL))
    }

    @Test fun `confusoes vem das mais frequentes`() {
        val c = (1..8).map { Confusion("a$it", "b", it) }
        val top = computeStats(emptyList(), emptyList(), c, now, zone).confusions
        assertEquals(5, top.size)
        assertEquals(8, top.first().count)
    }
}
