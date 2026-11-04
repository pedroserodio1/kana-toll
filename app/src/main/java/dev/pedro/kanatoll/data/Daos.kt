package dev.pedro.kanatoll.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<Card>)

    @Update
    suspend fun update(card: Card)

    @Query("SELECT * FROM card WHERE id = :id")
    suspend fun get(id: Long): Card?

    // Backup
    @Query("SELECT * FROM card ORDER BY id")
    suspend fun all(): List<Card>

    @Query("DELETE FROM card")
    suspend fun deleteAll()

    /** Cartões já apresentados e vencidos, os mais atrasados primeiro. */
    @Query("SELECT * FROM card WHERE introducedAt IS NOT NULL AND due <= :now ORDER BY due LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<Card>

    /** Para o quiz da notificação: só uma direção (kana → rōmaji). */
    @Query("SELECT * FROM card WHERE introducedAt IS NOT NULL AND due <= :now AND direction = :direction ORDER BY due LIMIT :limit")
    suspend fun dueByDirection(now: Long, direction: Direction, limit: Int): List<Card>

    /** Estudar à frente: os próximos cartões a vencer (ainda não devidos), os mais próximos primeiro. */
    @Query("SELECT * FROM card WHERE introducedAt IS NOT NULL AND due > :now ORDER BY due LIMIT :limit")
    suspend fun upcoming(now: Long, limit: Int): List<Card>

    /** Ritmo: cartões de leitura (kana → rōmaji) já estáveis, de qualquer script. */
    @Query("SELECT * FROM card WHERE direction = 'KANA_TO_ROMAJI' AND introducedAt IS NOT NULL AND stability >= :minStability")
    suspend fun stableReading(minStability: Double): List<Card>

    /** Pedágio: cartões devidos que se respondem no overlay (digitar ou escolher; sem áudio). */
    @Query("SELECT * FROM card WHERE introducedAt IS NOT NULL AND due <= :now AND direction != 'AUDIO_TO_KANA' ORDER BY due LIMIT :limit")
    suspend fun dueSilent(now: Long, limit: Int): List<Card>

    /** Pedágio sem nada devido: os cartões apresentados de menor estabilidade. */
    @Query("SELECT * FROM card WHERE introducedAt IS NOT NULL AND direction != 'AUDIO_TO_KANA' AND id NOT IN (:exclude) ORDER BY stability LIMIT :limit")
    suspend fun weakestSilent(exclude: List<Long>, limit: Int): List<Card>

    /** Qualquer cartão já apresentado da direção, sorteado: para a notificação de teste. */
    @Query("SELECT * FROM card WHERE introducedAt IS NOT NULL AND direction = :direction ORDER BY RANDOM() LIMIT 1")
    suspend fun randomIntroduced(direction: Direction): Card?

    @Query("SELECT COUNT(*) FROM card WHERE introducedAt IS NOT NULL AND due <= :now")
    suspend fun dueCount(now: Long): Int

    /** Próximos kana não apresentados, na ordem de semeadura. */
    @Query("SELECT kana FROM card WHERE script = :script AND introducedAt IS NULL GROUP BY kana ORDER BY MIN(id) LIMIT :limit")
    suspend fun nextNewKana(script: Script, limit: Int): List<String>

    /** Apresenta os 3 cartões de cada kana de uma vez. */
    @Query("UPDATE card SET introducedAt = :now, due = :now WHERE script = :script AND kana IN (:kana) AND introducedAt IS NULL")
    suspend fun introduce(script: Script, kana: List<String>, now: Long)

    /** Kana já apresentados do script: o conjunto de onde saem os distratores. */
    @Query("SELECT DISTINCT kana FROM card WHERE script = :script AND introducedAt IS NOT NULL")
    suspend fun introducedKana(script: Script): List<String>

    /** Kana apresentados desde [since] (início do dia de estudo), para o limite de novos por dia. */
    @Query("SELECT COUNT(DISTINCT kana) FROM card WHERE script = :script AND introducedAt >= :since")
    suspend fun introducedKanaSince(script: Script, since: Long): Int

    // Portão: só as direções de leitura e produção contam.
    @Query("SELECT COUNT(*) FROM card WHERE script = :script AND direction != 'AUDIO_TO_KANA'")
    suspend fun gateTotal(script: Script): Int

    @Query("SELECT COUNT(*) FROM card WHERE script = :script AND direction != 'AUDIO_TO_KANA' AND stability >= :minStability AND lastCorrect = 1")
    suspend fun gatePassed(script: Script, minStability: Double): Int
}

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLog)

    // Backup
    @Query("SELECT * FROM review_log ORDER BY id")
    suspend fun all(): List<ReviewLog>

    @Insert
    suspend fun insertAll(logs: List<ReviewLog>)

    @Query("DELETE FROM review_log")
    suspend fun deleteAll()
}

@Dao
abstract class ConfusionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(c: Confusion)

    @Query("UPDATE confusion SET count = count + 1 WHERE expected = :expected AND given = :given")
    abstract suspend fun inc(expected: String, given: String)

    @Transaction
    open suspend fun bump(expected: String, given: String) {
        insertIfAbsent(Confusion(expected, given))
        inc(expected, given)
    }

    /** Respostas mais trocadas por [expected], para reforçar o par nas opções. */
    @Query("SELECT given FROM confusion WHERE expected = :expected ORDER BY count DESC LIMIT :limit")
    abstract suspend fun topGiven(expected: String, limit: Int): List<String>

    // Backup
    @Query("SELECT * FROM confusion")
    abstract suspend fun all(): List<Confusion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(list: List<Confusion>)

    @Query("DELETE FROM confusion")
    abstract suspend fun deleteAll()
}

@Dao
interface GateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun open(state: GateState)

    @Query("SELECT COUNT(*) > 0 FROM gate_state WHERE script = :script")
    suspend fun isOpen(script: Script): Boolean

    // Backup
    @Query("SELECT * FROM gate_state")
    suspend fun all(): List<GateState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<GateState>)

    @Query("DELETE FROM gate_state")
    suspend fun deleteAll()
}
