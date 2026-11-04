package dev.pedro.kanatoll.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTest {
    @Test fun `sem estudo e sem meta`() {
        assertEquals("Hoje você ainda não estudou.", summaryText(0, 0, 0))
    }

    @Test fun `sem estudo e com meta`() {
        assertEquals("Hoje você ainda não estudou. Faltam 20 para a meta de 20.", summaryText(0, 0, 20))
    }

    @Test fun `estudou e falta para a meta`() {
        assertEquals("Hoje: 12 respostas, 75% de acerto. Faltam 8 para a meta de 20.", summaryText(12, 9, 20))
    }

    @Test fun `meta batida, inclusive ultrapassada`() {
        assertEquals("Hoje: 20 respostas, 100% de acerto. Meta de 20 batida.", summaryText(20, 20, 20))
        assertEquals("Hoje: 31 respostas, 94% de acerto. Meta de 20 batida.", summaryText(31, 29, 20))
    }

    @Test fun `singular e sem meta`() {
        assertEquals("Hoje: 1 resposta, 0% de acerto.", summaryText(1, 0, 0))
    }
}
