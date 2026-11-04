package dev.pedro.kanatoll.backup

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.CardState
import dev.pedro.kanatoll.data.Confusion
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.GateState
import dev.pedro.kanatoll.data.ReviewLog
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.data.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupJsonTest {
    private val sample = Backup(
        schemaVersion = 1,
        exportedAt = 1_762_000_000_000,
        cards = listOf(
            Card(1, Script.HIRAGANA, "あ", Direction.KANA_TO_ROMAJI, CardState.REVIEW, 12.5, 4.25, 1_762_100_000_000, 1_762_000_000_000, 5, 1, true, 1_761_000_000_000),
            Card(2, Script.HIRAGANA, "あ", Direction.ROMAJI_TO_KANA),
        ),
        reviewLogs = listOf(ReviewLog(1, 1, 1_762_000_000_000, true, "a", 840, Source.TOLL, retry = true)),
        confusions = listOf(Confusion("つ", "し", 3)),
        gates = listOf(GateState(Script.HIRAGANA, 1_762_000_000_000)),
    )

    @Test fun `ida e volta preserva tudo`() {
        assertEquals(sample, BackupJson.decode(BackupJson.encode(sample)))
    }

    @Test fun `ignora campos desconhecidos`() {
        val extra = BackupJson.encode(sample).replaceFirst("{", """{"futuro":"campo novo",""")
        assertEquals(sample, BackupJson.decode(extra))
    }

    @Test fun `recusa versao mais nova que o app`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupJson.decode(BackupJson.encode(sample.copy(schemaVersion = 99)))
        }
    }

    @Test fun `recusa lixo`() {
        assertThrows(Exception::class.java) { BackupJson.decode("isto não é json") }
    }
}
