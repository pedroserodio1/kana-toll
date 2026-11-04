package dev.pedro.kanatoll.fsrs

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vetores de `ts-fsrs` (legacy/__tests__/impl/long-term_scheduler.test.ts, modo longo prazo).
 * Os pesos de lá têm 19 valores; a migração do ts-fsrs acrescenta [0.0, 0.5] (decay do FSRS-5).
 * Em cada revisão o tempo avança exatamente o intervalo agendado.
 */
class FsrsTest {
    private val w19 = doubleArrayOf(
        0.4197, 1.1869, 3.0412, 15.2441, 7.1434, 0.6477, 1.0007, 0.0674, 1.6597,
        0.1712, 1.1178, 2.0225, 0.0904, 0.3025, 2.1214, 0.2498, 2.9466, 0.4891, 0.6468,
    )
    private val fsrs = Fsrs(w19 + doubleArrayOf(0.0, 0.5))

    /** [elapsedAfter]: dias entre uma revisão e a seguinte; sem ele, usa o intervalo calculado pelo próprio porte. */
    private fun run(vararg ratings: Rating, elapsedAfter: List<Int>? = null): Triple<List<Int>, List<Double>, List<Double>> {
        var m = Memory.NEW
        var elapsed = 0
        val ivl = mutableListOf<Int>()
        val s = mutableListOf<Double>()
        val d = mutableListOf<Double>()
        ratings.forEachIndexed { i, r ->
            m = fsrs.next(m, elapsed, r)
            ivl += fsrs.intervalDays(m.stability); s += m.stability; d += m.difficulty
            elapsed = elapsedAfter?.get(i) ?: ivl.last()
        }
        return Triple(ivl, s, d)
    }

    private fun assertClose(expected: List<Double>, actual: List<Double>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertEquals("índice $it", expected[it], actual[it], 1e-6) }
    }

    @Test fun `vetor 1 - acertos, duas falhas, acertos`() {
        val g = Rating.GOOD
        val a = Rating.AGAIN
        val (ivl, s, d) = run(g, g, g, g, g, g, a, a, g, g, g, g, g)
        assertEquals(listOf(3, 13, 48, 155, 445, 1158, 17, 3, 11, 37, 112, 307, 773), ivl)
        assertClose(
            listOf(
                3.0412, 13.09130698, 48.15848988, 154.93732625, 445.05562739, 1158.07779739,
                16.63063166, 3.01732209, 11.42247264, 37.37521902, 111.8752758, 306.5974569, 772.94026648,
            ),
            s,
        )
        assertClose(
            listOf(
                4.49094334, 4.26664289, 4.05746029, 3.86237659, 3.68044154, 3.51076891,
                4.69833071, 5.55956298, 5.26323756, 4.98688448, 4.72915759, 4.4888015, 4.26464541,
            ),
            d,
        )
    }

    @Test fun `vetor 2 - todas as notas`() {
        // Os intervalos do ts-fsrs aqui incluem uma regra de exibição dos 4 botões (Difícil > Errei,
        // Bom > Difícil, Fácil > Bom) que empurra alguns em +1 dia. O app só usa Errei e Bom, então não
        // portamos essa regra. Para comparar só a memória (estabilidade e dificuldade), alimentamos o
        // teste com os mesmos dias decorridos da referência.
        val referenceIntervals = listOf(1, 2, 6, 41, 4, 7, 21, 133)
        val (_, s, d) = run(
            Rating.AGAIN, Rating.HARD, Rating.GOOD, Rating.EASY,
            Rating.AGAIN, Rating.HARD, Rating.GOOD, Rating.EASY,
            elapsedAfter = referenceIntervals,
        )
        assertClose(
            listOf(0.4197, 1.0344317, 5.5356759, 41.0033667, 4.46605519, 6.67743292, 20.88868155, 132.81849454),
            s,
        )
        assertClose(
            listOf(7.1434, 7.03653841, 6.64066485, 5.92312772, 6.44779861, 6.45995078, 6.10293922, 5.36588547),
            d,
        )
    }

    @Test fun `pesos padrao FSRS-6 - primeira revisao`() {
        val f = Fsrs() // retenção 90%: intervalo = estabilidade arredondada
        val good = f.next(Memory.NEW, 0, Rating.GOOD)
        assertEquals(2.3065, good.stability, 1e-9)
        assertEquals(2, f.intervalDays(good.stability))
        val again = f.next(Memory.NEW, 0, Rating.AGAIN)
        assertEquals(0.212, again.stability, 1e-9)
        assertEquals(1, f.intervalDays(again.stability))
    }

    @Test fun `retencao a 90 por cento no intervalo da estabilidade`() {
        val f = Fsrs()
        assertEquals(0.9, f.retrievability(10, 10.0), 1e-6)
    }

    @Test fun `dia de estudo vira as 4h e o vencimento cai as 4h`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        fun ms(h: Int, day: Int) = ZonedDateTime.of(2026, 11, day, h, 0, 0, 0, zone).toInstant().toEpochMilli()
        // 03:30 ainda é o dia anterior
        assertEquals(StudyDay.of(ms(22, 3), zone), StudyDay.of(ms(3, 4), zone))
        assertEquals(1, StudyDay.elapsedDays(ms(22, 3), ms(5, 4), zone))
        assertEquals(0, StudyDay.elapsedDays(ms(22, 3), ms(3, 4), zone))
        // revisado às 22h com intervalo 1 → vence às 04:00 do dia seguinte
        assertEquals(ms(4, 4), StudyDay.dueAt(ms(22, 3), 1, zone))
    }
}
