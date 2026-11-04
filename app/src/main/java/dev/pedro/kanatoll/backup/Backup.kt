package dev.pedro.kanatoll.backup

import androidx.room.withTransaction
import dev.pedro.kanatoll.data.AppDatabase
import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Confusion
import dev.pedro.kanatoll.data.GateState
import dev.pedro.kanatoll.data.ReviewLog
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** O histórico inteiro. [schemaVersion] sobe quando o formato muda de forma incompatível. */
@Serializable
data class Backup(
    val schemaVersion: Int,
    val exportedAt: Long,
    val cards: List<Card>,
    val reviewLogs: List<ReviewLog>,
    val confusions: List<Confusion>,
    val gates: List<GateState>,
)

object BackupJson {
    const val SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** Lança se o texto não for um backup válido ou for de uma versão mais nova que este app. */
    fun decode(text: String): Backup {
        val backup = json.decodeFromString(Backup.serializer(), text)
        require(backup.schemaVersion in 1..SCHEMA_VERSION) {
            "Backup na versão ${backup.schemaVersion}; este app lê até a $SCHEMA_VERSION"
        }
        return backup
    }
}

class BackupManager(private val db: AppDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    /** JSON do banco todo, lido numa transação (um retrato consistente). */
    suspend fun export(): String = db.withTransaction {
        BackupJson.encode(
            Backup(
                schemaVersion = BackupJson.SCHEMA_VERSION,
                exportedAt = clock.millis(),
                cards = db.cards().all(),
                reviewLogs = db.reviewLogs().all(),
                confusions = db.confusions().all(),
                gates = db.gates().all(),
            ),
        )
    }

    /**
     * Substitui todo o histórico pelo do arquivo. Atômico: se algo falhar (JSON inválido,
     * referência quebrada), o banco continua como estava. Devolve o número de cartões importados.
     */
    suspend fun import(text: String): Int {
        val backup = BackupJson.decode(text)
        db.withTransaction {
            db.reviewLogs().deleteAll()
            db.confusions().deleteAll()
            db.gates().deleteAll()
            db.cards().deleteAll()
            db.cards().insertAll(backup.cards)
            db.reviewLogs().insertAll(backup.reviewLogs)
            db.confusions().insertAll(backup.confusions)
            db.gates().insertAll(backup.gates)
        }
        return backup.cards.size
    }
}
