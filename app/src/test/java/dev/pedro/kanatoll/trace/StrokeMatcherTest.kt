package dev.pedro.kanatoll.trace

import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.domain.KanaTable
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeMatcherTest {
    // Os dados reais do app: o teste roda na pasta do módulo (app/).
    private val strokes = KanaStrokes.parse(File("src/main/assets/kana_strokes.json").readText())

    private val allGlyphs: List<Pair<Script, String>> =
        Script.entries.flatMap { s -> KanaTable.all.map { s to it.glyph(s) } }

    @Test fun `todos os 214 kana tem traços nos dados do KanjiVG`() {
        assertEquals(214, allGlyphs.size)
        for ((script, glyph) in allGlyphs) {
            val g = strokes.layout(glyph)
            assertNotNull("sem traços para $glyph ($script)", g)
            assertTrue("$glyph sem traços", g!!.strokes.isNotEmpty())
            assertTrue("$glyph com traços demais", g.strokes.size in 1..10)
            assertEquals(if (glyph.length == 2) 2 else 1, g.cells)
        }
    }

    @Test fun `cada traço de referência é aceito, na ordem, contra si mesmo`() {
        for ((_, glyph) in allGlyphs) {
            val g = strokes.layout(glyph)!!
            g.strokes.forEachIndexed { i, ref ->
                assertEquals("$glyph traço ${i + 1}", Verdict.OK, StrokeMatcher.judge(ref, g.strokes, i))
            }
        }
    }

    @Test fun `traço de trás para frente nunca é MISS`() {
        for ((_, glyph) in allGlyphs) {
            val g = strokes.layout(glyph)!!
            g.strokes.forEachIndexed { i, ref ->
                if (StrokeMatcher.length(ref) < 25f) return@forEachIndexed
                val verdict = StrokeMatcher.judge(ref.reversed(), g.strokes, i)
                assertTrue("$glyph traço ${i + 1}: $verdict", verdict == Verdict.WRONG_DIRECTION || verdict == Verdict.OK)
            }
        }
    }

    @Test fun `traço longe do lugar e MISS`() {
        val g = strokes.layout("し")!!
        val far = g.strokes[0].map { Pt(it.x + 60f, it.y + 60f) }
        assertEquals(Verdict.MISS, StrokeMatcher.judge(far, g.strokes, 0))
    }

    @Test fun `tremida pequena ainda e aceita`() {
        val g = strokes.layout("き")!!
        val wobbly = g.strokes[2].mapIndexed { i, p -> Pt(p.x + (if (i % 2 == 0) 3f else -3f), p.y + (if (i % 3 == 0) 2f else -2f)) }
        assertEquals(Verdict.OK, StrokeMatcher.judge(wobbly, g.strokes, 2))
    }

    @Test fun `traço de um kana fora de ordem e WRONG_ORDER`() {
        val g = strokes.layout("ち")!!
        // ち tem 2 traços de formatos bem diferentes: desenhar o 2º quando se espera o 1º
        val verdict = StrokeMatcher.judge(g.strokes[1], g.strokes, 0)
        assertEquals(Verdict.WRONG_ORDER, verdict)
    }

    @Test fun `um toque curto onde se espera um traço longo e MISS`() {
        val g = strokes.layout("き")!!
        val tap = listOf(g.strokes[0].first(), Pt(g.strokes[0].first().x + 1f, g.strokes[0].first().y))
        assertEquals(Verdict.MISS, StrokeMatcher.judge(tap, g.strokes, 0))
    }

    @Test fun `reamostrar mantém o início e o fim e o número de pontos`() {
        val line = listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(100f, 0f))
        val r = StrokeMatcher.resample(line, 11)
        assertEquals(11, r.size)
        assertEquals(0f, r.first().x, 1e-3f)
        assertEquals(100f, r.last().x, 1e-3f)
        assertEquals(50f, r[5].x, 1e-3f) // uniforme por comprimento, não por vértice
    }

    @Test fun `yoon tem a segunda casa deslocada e menor`() {
        val g = strokes.layout("きゃ")!!
        val first = strokes.layout("き")!!
        assertEquals(first.strokes.size + strokes.layout("ゃ")!!.strokes.size, g.strokes.size)
        val smallXs = g.strokes.drop(first.strokes.size).flatten().map { it.x }
        assertTrue(smallXs.all { it > TraceGlyph.CELL })
    }
}
