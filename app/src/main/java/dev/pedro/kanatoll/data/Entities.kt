package dev.pedro.kanatoll.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class Script { HIRAGANA, KATAKANA }

/** AUDIO_TO_KANA é treino: não conta para o portão. */
enum class Direction { KANA_TO_ROMAJI, ROMAJI_TO_KANA, AUDIO_TO_KANA }

/** Estados do FSRS. */
enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

enum class Source { SESSION, NOTIFICATION, TOLL }

/**
 * Um cartão = (script, kana, direção). Todos os cartões do script são semeados de uma vez;
 * `introducedAt == null` significa "ainda não apresentado". Tempos em epoch ms.
 * A ordem de introdução é a ordem do `id` (semear na ordem da KanaTable).
 */
@Serializable
@Entity(
    tableName = "card",
    indices = [Index(value = ["script", "kana", "direction"], unique = true), Index("due")],
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val script: Script,
    val kana: String,
    val direction: Direction,
    val state: CardState = CardState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastCorrect: Boolean = false,
    val introducedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "review_log",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId")],
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val ts: Long,
    val correct: Boolean,
    val given: String,
    val elapsedMs: Long,
    val source: Source,
    /** Re-pergunta de um erro na mesma sessão: registrada, mas não é uma revisão do FSRS. */
    val retry: Boolean = false,
)

/** "Errou [expected] respondendo [given]". */
@Serializable
@Entity(tableName = "confusion", primaryKeys = ["expected", "given"])
data class Confusion(
    val expected: String,
    val given: String,
    val count: Int = 0,
)

/** Existir a linha = portão aberto. Nunca é apagada. */
@Serializable
@Entity(tableName = "gate_state")
data class GateState(
    @PrimaryKey val script: Script,
    val openedAt: Long,
)
