package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.CardState
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.fsrs.Fsrs
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {
    private val zone = ZoneId.of("UTC")
    private val fsrs = Fsrs()
    private fun ms(day: Int, hour: Int) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun card(dir: Direction = Direction.KANA_TO_ROMAJI, kana: String = "け") =
        Card(id = 1, script = Script.HIRAGANA, kana = kana, direction = dir, introducedAt = ms(4, 8))

    @Test fun `primeiro acerto - estabilidade inicial, vence em 2 dias as 4h`() {
        val c = applyAnswer(card(), true, ms(4, 10), zone, fsrs)
        assertEquals(CardState.REVIEW, c.state)
        assertEquals(2.3065, c.stability, 1e-9)
        assertEquals(ms(6, 4), c.due)
        assertEquals(1, c.reps)
        assertEquals(0, c.lapses)
        assertTrue(c.lastCorrect)
    }

    @Test fun `primeiro erro num cartao novo nao conta como lapso`() {
        val c = applyAnswer(card(), false, ms(4, 10), zone, fsrs)
        assertEquals(0.212, c.stability, 1e-9)
        assertEquals(0, c.lapses)
        assertEquals(ms(5, 4), c.due)
        assertTrue(!c.lastCorrect)
    }

    @Test fun `erro num cartao ja revisado conta lapso e derruba a estabilidade`() {
        val ok = applyAnswer(card(), true, ms(4, 10), zone, fsrs)
        val bad = applyAnswer(ok, false, ms(6, 10), zone, fsrs)
        assertEquals(1, bad.lapses)
        assertEquals(2, bad.reps)
        assertTrue(bad.stability < ok.stability)
        assertTrue(!bad.lastCorrect)
    }

    @Test fun `acertos seguidos crescem a estabilidade`() {
        var c = card()
        var day = 0
        var last = 0.0
        repeat(4) {
            c = applyAnswer(c, true, ms(4, 10) + day * 86_400_000L, zone, fsrs)
            assertTrue(c.stability > last)
            last = c.stability
            day += fsrs.intervalDays(c.stability)
        }
    }

    @Test fun `confusao em kana para romaji vira o kana nomeado`() {
        assertEquals("し", wrongKana(card(Direction.KANA_TO_ROMAJI, "つ"), "shi"))
        assertEquals("し", wrongKana(card(Direction.KANA_TO_ROMAJI, "つ"), " SI "))
        assertNull(wrongKana(card(Direction.KANA_TO_ROMAJI, "つ"), "xyz"))
    }

    @Test fun `confusao nas direcoes de escolha ja e o kana`() {
        assertEquals("め", wrongKana(card(Direction.ROMAJI_TO_KANA, "ぬ"), "め"))
        assertEquals("ヌ", wrongKana(card(Direction.AUDIO_TO_KANA, "ソ"), "ヌ"))
    }

    @Test fun `confusao em katakana digitado usa o glifo do script`() {
        val c = card(Direction.KANA_TO_ROMAJI, "ソ").copy(script = Script.KATAKANA)
        assertEquals("ン", wrongKana(c, "n"))
    }
}
