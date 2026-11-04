package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaTableTest {
    private val all = KanaTable.all

    @Test fun `107 kana em 46 + 25 + 36`() {
        assertEquals(107, all.size)
        assertEquals(46, all.count { it.group == KanaGroup.BASIC })
        assertEquals(25, all.count { it.group == KanaGroup.DAKUTEN })
        assertEquals(36, all.count { it.group == KanaGroup.YOON })
    }

    @Test fun `hiragana e rōmaji canonico sem duplicatas`() {
        assertEquals(107, all.map { it.hira }.toSet().size)
        // canônico único: o cartão rōmaji → kana tem uma só resposta certa
        assertEquals(107, all.map { it.romaji }.toSet().size)
        assertTrue(all.all { k -> k.hira.all { it in 'ぁ'..'ゖ' } })
    }

    @Test fun `katakana derivado`() {
        fun kata(h: String) = all.first { it.hira == h }.glyph(Script.KATAKANA)
        assertEquals("ア", kata("あ"))
        assertEquals("キャ", kata("きゃ"))
        assertEquals("ヂョ", kata("ぢょ"))
        assertEquals("ン", kata("ん"))
    }

    @Test fun `variantes de digitacao`() {
        fun k(h: String) = all.first { it.hira == h }
        assertTrue(k("し").accepts(" SHI "))
        assertTrue(k("し").accepts("si"))
        assertTrue(k("を").accepts("o"))
        assertTrue(k("ぢ").accepts("ji"))
        assertTrue(k("じゃ").accepts("jya"))
        assertTrue(!k("し").accepts("ci"))
    }

    @Test fun `321 cartoes por script, 3 direcoes por kana consecutivas`() {
        val cards = KanaTable.cards(Script.KATAKANA)
        assertEquals(321, cards.size)
        assertEquals(321, cards.map { Triple(it.script, it.kana, it.direction) }.toSet().size)
        assertEquals(Direction.entries, cards.take(3).map { it.direction })
        assertEquals(1, cards.take(3).map { it.kana }.toSet().size)
    }
}
