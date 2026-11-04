package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private fun card(id: Long, kana: String = "し", dir: Direction = Direction.KANA_TO_ROMAJI) =
        Card(id = id, script = Script.HIRAGANA, kana = kana, direction = dir)

    @Test fun `acerto passa para o proximo e esvazia no fim`() {
        val q = SessionQueue(listOf(card(1), card(2)))
        assertEquals(1L, q.current!!.card.id)
        q.answered(true)
        assertEquals(2L, q.current!!.card.id)
        assertEquals(1, q.remaining)
        q.answered(true)
        assertNull(q.current)
    }

    @Test fun `erro volta ao fim como re-pergunta ate acertar`() {
        val q = SessionQueue(listOf(card(1), card(2)))
        q.answered(false) // 1 errou
        assertEquals(2L, q.current!!.card.id)
        assertFalse(q.current!!.retry)
        q.answered(true) // 2 acertou
        assertEquals(1L, q.current!!.card.id)
        assertTrue(q.current!!.retry)
        q.answered(false) // errou de novo na re-pergunta
        assertEquals(1, q.remaining)
        assertTrue(q.current!!.retry)
        q.answered(true)
        assertNull(q.current)
    }

    @Test fun `correcao do rōmaji digitado usa as variantes`() {
        val c = card(1)
        assertTrue(isCorrect(c, "shi"))
        assertTrue(isCorrect(c, "SI "))
        assertFalse(isCorrect(c, "ji"))
    }

    @Test fun `correcao nas direcoes de escolha e a igualdade do glifo`() {
        assertTrue(isCorrect(card(1, "ぬ", Direction.ROMAJI_TO_KANA), "ぬ"))
        assertFalse(isCorrect(card(1, "ぬ", Direction.AUDIO_TO_KANA), "め"))
    }
}
