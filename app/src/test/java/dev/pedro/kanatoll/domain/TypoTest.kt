package dev.pedro.kanatoll.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypoTest {
    private fun kana(hira: String) = KanaTable.all.first { it.hira == hira }

    @Test fun `uma edicao`() {
        assertTrue(withinOneEdit("shi", "shii")) // a mais
        assertTrue(withinOneEdit("shi", "sh")) // a menos
        assertTrue(withinOneEdit("shi", "shu")) // trocada
        assertTrue(withinOneEdit("shi", "sih")) // vizinhas invertidas
        assertTrue(withinOneEdit("shi", "shi"))
        assertFalse(withinOneEdit("shi", "sa"))
        assertFalse(withinOneEdit("shi", "hsi" + "x"))
        assertFalse(withinOneEdit("abc", "cba")) // não vizinhas
    }

    @Test fun `erro de digitacao de um kana`() {
        assertTrue(isNearMiss(kana("し"), "shii"))
        assertTrue(isNearMiss(kana("し"), "sih"))
        assertTrue(isNearMiss(kana("つ"), "tsuu"))
        assertTrue(isNearMiss(kana("つ"), " TSUU "))
    }

    @Test fun `errar o kana nao e erro de digitacao`() {
        // "ka" é o rōmaji de か: responder isso para き é errar o kana
        assertFalse(isNearMiss(kana("き"), "ka"))
        assertFalse(isNearMiss(kana("し"), "chi")) // outro kana
        assertFalse(isNearMiss(kana("し"), "xyz"))
        assertFalse(isNearMiss(kana("し"), ""))
    }

    @Test fun `resposta aceita nao e erro, e variante aceita tambem nao`() {
        assertFalse(isNearMiss(kana("し"), "shi"))
        assertFalse(isNearMiss(kana("し"), "si")) // variante aceita: já é certo
    }

    @Test fun `rōmaji de uma letra nunca vira erro de digitacao`() {
        // "a" para あ: qualquer coisa a uma edição seria outro kana ou ruído demais
        assertFalse(isNearMiss(kana("あ"), "s"))
        assertFalse(isNearMiss(kana("あ"), "aa"))
    }
}
