package dev.pedro.kanatoll.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pedro.kanatoll.backup.Backup
import dev.pedro.kanatoll.backup.BackupJson
import dev.pedro.kanatoll.backup.BackupManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Relógio que o teste avança à mão, para simular dias de estudo. */
private class TestClock(var now: Instant, private val zone: ZoneId = ZoneId.of("UTC")) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
    override fun instant(): Instant = now
    fun nextDay() { now = now.plus(1, ChronoUnit.DAYS) }
}

/** Banco Room em memória + relógio falso: não toca no seu banco nem nas suas configurações reais. */
@RunWith(AndroidJUnit4::class)
class ReviewRepositoryTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefsName = "test-${UUID.randomUUID()}"
    private lateinit var db: AppDatabase
    private lateinit var clock: TestClock
    private lateinit var repo: ReviewRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        clock = TestClock(Instant.parse("2026-11-04T10:00:00Z"))
        repo = ReviewRepository(db, Settings(ctx, prefsName), clock)
    }

    @After fun tearDown() {
        db.close()
        ctx.deleteSharedPreferences(prefsName)
    }

    private fun blocking(block: suspend () -> Unit) = runBlocking { block() }

    @Test fun portaoAbreEstudandoTodoDiaEoKatakanaEntra() = blocking {
        var openedOnDay = -1
        for (day in 0 until 40) {
            repo.introduceNew()
            for (c in repo.dueCards(10_000)) {
                if (repo.answer(c.id, true, "ok", 500, Source.SESSION).gateOpened == Script.HIRAGANA) openedOnDay = day
            }
            if (openedOnDay >= 0) break
            clock.nextDay()
        }
        // 107 kana a 10 por dia = 11 dias de introdução; o último kana passa na 2ª revisão, 2 dias depois.
        assertTrue("portão não abriu a tempo (dia $openedOnDay)", openedOnDay in 11..20)
        assertTrue(repo.isGateOpen(Script.HIRAGANA))
        assertEquals(GateProgress(214, 214), repo.gateProgress(Script.HIRAGANA))
        // o katakana foi semeado e é o próximo a entrar
        assertEquals(214, db.cards().gateTotal(Script.KATAKANA))
        clock.nextDay()
        assertEquals("ア", repo.introduceNew().first())

        // regressão: depois de aberto, um erro não fecha o portão
        val any = db.cards().all().first { it.script == Script.HIRAGANA }
        repo.answer(any.id, false, "x", 100, Source.SESSION)
        assertFalse(db.cards().get(any.id)!!.lastCorrect)
        assertTrue(repo.isGateOpen(Script.HIRAGANA))
    }

    @Test fun portaoSoAbreQuandoTodosOsCartoesDeLeituraEProducaoPassam() = blocking {
        repo.seed(Script.HIRAGANA)
        val now = clock.millis()
        val gate = db.cards().all().filter { it.direction != Direction.AUDIO_TO_KANA }
        val missing = gate.last()
        // todos estáveis (≥ 7 dias) e certos, menos um; os cartões de áudio ficam de fora e não contam
        gate.forEach {
            db.cards().update(
                it.copy(
                    state = CardState.REVIEW, stability = 10.0, difficulty = 5.0, due = now, lastReview = now,
                    reps = 3, lastCorrect = it.id != missing.id, introducedAt = now,
                ),
            )
        }
        assertNull(repo.answer(gate.first().id, true, "ok", 1, Source.SESSION).gateOpened)
        assertFalse(repo.isGateOpen(Script.HIRAGANA))
        assertEquals(GateProgress(213, 214), repo.gateProgress(Script.HIRAGANA))

        assertEquals(Script.HIRAGANA, repo.answer(missing.id, true, "ok", 1, Source.SESSION).gateOpened)
        assertTrue(repo.isGateOpen(Script.HIRAGANA))
    }

    @Test fun novosPorDiaRespeitaOLimiteEAOrdem() = blocking {
        val day1 = repo.introduceNew()
        assertEquals(listOf("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ"), day1)
        assertEquals(emptyList<String>(), repo.introduceNew()) // já usou o limite de hoje
        assertEquals(30, db.cards().all().count { it.introducedAt != null }) // 3 cartões por kana
        clock.nextDay()
        assertEquals(listOf("さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と"), repo.introduceNew())
    }

    @Test fun reperguntaEntraNoLogMasNaoMexeNoCartao() = blocking {
        repo.introduceNew()
        val card = repo.dueCards(1).first()
        val result = repo.answer(card.id, false, "x", 100, Source.SESSION, retry = true)
        assertNull(result.intervalDays)
        assertEquals(card, db.cards().get(card.id))
        assertEquals(listOf(true), db.reviewLogs().all().map { it.retry })
    }

    @Test fun confusaoDigitadaContaOParKanaConfundido() = blocking {
        repo.seed(Script.HIRAGANA)
        val tsu = db.cards().all().first { it.kana == "つ" && it.direction == Direction.KANA_TO_ROMAJI }
        repeat(2) { repo.answer(tsu.id, false, "shi", 100, Source.SESSION) }
        assertEquals(listOf("し"), repo.confusedWith("つ", 5))
        assertEquals(2, db.confusions().all().single().count)
    }

    @Test fun backupVoltaExatamenteAoEstadoExportado() = blocking {
        val backup = BackupManager(db, clock)
        repo.introduceNew()
        repo.dueCards(5).forEach { repo.answer(it.id, true, "ok", 10, Source.SESSION) }
        val cards = db.cards().all()
        val logs = db.reviewLogs().all()
        val json = backup.export()

        // muda o estado depois do export
        val other = db.cards().all().first { it.introducedAt != null && it.reps == 0 }
        repo.answer(other.id, false, "?", 1, Source.SESSION)
        assertNotEquals(cards, db.cards().all())

        assertEquals(cards.size, backup.import(json))
        assertEquals(cards, db.cards().all())
        assertEquals(logs, db.reviewLogs().all())
    }

    @Test fun importInvalidoNaoAlteraNada() = blocking {
        val backup = BackupManager(db, clock)
        repo.introduceNew()
        repo.dueCards(3).forEach { repo.answer(it.id, true, "ok", 10, Source.SESSION) }
        val cards = db.cards().all()
        val logs = db.reviewLogs().all()

        // 1) texto que nem é JSON
        assertTrue(runCatching { backup.import("lixo") }.isFailure)
        // 2) JSON válido, mas com um log que aponta para um cartão inexistente (quebra a chave estrangeira
        //    só depois de os dados atuais terem sido apagados: a transação tem de desfazer tudo)
        val broken = Backup(
            BackupJson.SCHEMA_VERSION, 0, emptyList(),
            listOf(ReviewLog(cardId = 999, ts = 0, correct = true, given = "x", elapsedMs = 0, source = Source.SESSION)),
            emptyList(), emptyList(),
        )
        assertTrue(runCatching { backup.import(BackupJson.encode(broken)) }.isFailure)

        assertEquals(cards, db.cards().all())
        assertEquals(logs, db.reviewLogs().all())
    }
}
