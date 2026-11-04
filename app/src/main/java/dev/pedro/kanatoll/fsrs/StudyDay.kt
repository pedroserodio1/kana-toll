package dev.pedro.kanatoll.fsrs

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * "Dia de estudo" vira às 04:00 local. Um cartão com intervalo de N dias vence às 04:00 do dia
 * (lógico) da revisão + N, e não 24h × N depois: revisar às 22h não adia o cartão para a noite seguinte.
 */
object StudyDay {
    const val ROLLOVER_HOUR = 4L

    fun of(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).minusHours(ROLLOVER_HOUR).toLocalDate()

    /** Instante (epoch ms) em que começou o dia de estudo de [epochMs]. */
    fun startOf(epochMs: Long, zone: ZoneId): Long =
        of(epochMs, zone).atStartOfDay(zone).plusHours(ROLLOVER_HOUR).toInstant().toEpochMilli()

    /** Dias de estudo inteiros entre duas revisões; entrada do `elapsedDays` do FSRS. */
    fun elapsedDays(lastReviewMs: Long, nowMs: Long, zone: ZoneId): Int =
        ChronoUnit.DAYS.between(of(lastReviewMs, zone), of(nowMs, zone)).toInt().coerceAtLeast(0)

    fun dueAt(reviewMs: Long, intervalDays: Int, zone: ZoneId): Long =
        of(reviewMs, zone).plusDays(intervalDays.toLong()).atStartOfDay(zone)
            .plusHours(ROLLOVER_HOUR).toInstant().toEpochMilli()
}
