package dev.pedro.kanatoll.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBackupTest {
    private fun names(vararg days: String) = days.map { "kana-backup-$it.json" }

    @Test fun `mantem os 4 mais recentes e apaga os mais antigos`() {
        val all = names("2026-10-01", "2026-10-08", "2026-10-15", "2026-10-22", "2026-10-29", "2026-11-05")
        assertEquals(names("2026-10-08", "2026-10-01"), backupsToDelete(all))
    }

    @Test fun `a ordem da lista nao importa, so a data no nome`() {
        val shuffled = names("2026-11-05", "2026-10-01", "2026-10-22", "2026-10-08", "2026-10-29", "2026-10-15")
        assertEquals(names("2026-10-08", "2026-10-01"), backupsToDelete(shuffled))
    }

    @Test fun `nunca apaga arquivos que nao sao backups`() {
        val all = names("2026-10-01", "2026-10-08", "2026-10-15", "2026-10-22", "2026-10-29") +
            listOf("foto.jpg", "kana-backup-notas.txt", "outro.json", "kana-backup-2026-09-01.json.bak")
        assertEquals(names("2026-10-01"), backupsToDelete(all))
    }

    @Test fun `com poucos arquivos nao apaga nada`() {
        assertEquals(emptyList<String>(), backupsToDelete(names("2026-11-05", "2026-10-29")))
        assertEquals(emptyList<String>(), backupsToDelete(emptyList()))
    }
}
