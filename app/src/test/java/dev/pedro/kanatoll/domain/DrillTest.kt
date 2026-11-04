package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Confusion
import dev.pedro.kanatoll.data.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillTest {
    @Test fun `sem trocas registradas o treino e vazio`() {
        assertTrue(buildDrill(emptyList()).isEmpty())
        assertTrue(buildDrill(listOf(Confusion("つ", "し", 0))).isEmpty())
    }

    @Test fun `o script vem do primeiro caractere`() {
        assertEquals(Script.HIRAGANA, scriptOf("し"))
        assertEquals(Script.HIRAGANA, scriptOf("きゃ"))
        assertEquals(Script.KATAKANA, scriptOf("ソ"))
        assertEquals(Script.KATAKANA, scriptOf("キャ"))
    }

    @Test fun `um par pergunta dos dois lados e no maximo 4 vezes`() {
        val drill = buildDrill(listOf(Confusion("つ", "し", 5)))
        assertEquals(4, drill.size)
        assertEquals(2, drill.count { it.target == "つ" && it.other == "し" })
        assertEquals(2, drill.count { it.target == "し" && it.other == "つ" })
    }

    @Test fun `limita a 12 perguntas e usa so as 10 trocas mais frequentes`() {
        val many = listOf("あ" to "お", "い" to "り", "く" to "へ", "さ" to "ち", "は" to "ほ", "る" to "ろ", "ぬ" to "め", "ね" to "れ", "わ" to "れ", "ま" to "も", "こ" to "に")
            .mapIndexed { i, (a, b) -> Confusion(a, b, 20 - i) }
        val drill = buildDrill(many)
        assertEquals(12, drill.size)
        // a 11ª troca (a de menor contagem) fica de fora
        assertTrue(drill.none { it.target == "こ" || it.other == "こ" })
    }

    @Test fun `ignora glifos desconhecidos e pares iguais`() {
        val drill = buildDrill(listOf(Confusion("x", "し", 9), Confusion("し", "し", 9), Confusion("ソ", "ン", 3)))
        assertEquals(setOf("ソ", "ン"), drill.map { it.target }.toSet())
        assertTrue(drill.all { it.script == Script.KATAKANA })
    }
}
