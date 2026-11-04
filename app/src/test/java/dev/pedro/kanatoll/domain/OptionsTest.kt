package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Script
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsTest {
    private val hira = KanaTable.all.map { it.glyph(Script.HIRAGANA) }
    private val firstTen = hira.take(10)

    @Test fun `opcoes - 5 unicas, com a certa, so do script`() {
        repeat(50) { seed ->
            val o = buildOptions("か", Script.HIRAGANA, emptyList(), firstTen, random = Random(seed))
            assertEquals(5, o.size)
            assertEquals(5, o.toSet().size)
            assertTrue("か" in o)
            assertTrue(o.all { it in hira })
        }
    }

    @Test fun `confusao aprendida entra sempre`() {
        repeat(50) { seed ->
            val o = buildOptions("つ", Script.HIRAGANA, listOf("し"), hira, random = Random(seed))
            assertTrue("し" in o)
        }
    }

    @Test fun `parecidos vistos tem prioridade sobre aleatorios`() {
        // pool grande, mas só し e そ (parecidos de つ) foram vistos entre os parecidos
        val o = buildOptions("つ", Script.HIRAGANA, emptyList(), hira, count = 3, random = Random(1))
        assertEquals(3, o.size)
        assertTrue(o.filter { it != "つ" }.all { it in similarKana("つ", Script.HIRAGANA) })
    }

    @Test fun `com pool pequeno completa com outros kana`() {
        val o = buildOptions("あ", Script.HIRAGANA, emptyList(), listOf("あ"), random = Random(3))
        assertEquals(5, o.size)
        assertEquals(5, o.toSet().size)
    }

    @Test fun `katakana usa glifos de katakana`() {
        val kata = KanaTable.all.map { it.glyph(Script.KATAKANA) }
        val o = buildOptions("ソ", Script.KATAKANA, emptyList(), kata.take(10), random = Random(2))
        assertTrue(o.all { it in kata })
    }

    @Test fun `grupos so citam kana que existem na tabela`() {
        for (script in Script.entries) {
            val all = KanaTable.all.map { it.glyph(script) }.toSet()
            // similarKana de cada kana simples nunca devolve algo fora da tabela
            all.filter { it.length == 1 }.forEach { g -> assertTrue(all.containsAll(similarKana(g, script))) }
        }
    }

    @Test fun `parecidos de yoon sao yoon`() {
        val s = similarKana("きゃ", Script.HIRAGANA)
        assertTrue("きゅ" in s && "ぎゃ" in s && "しゃ" in s)
        assertTrue(s.all { it.length == 2 })
    }
}
