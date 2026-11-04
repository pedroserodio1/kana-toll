package dev.pedro.kanatoll.data

import android.content.Context

/** Uma sessão do modo Ritmo: [maxBpm] alcançado, [hits] acertos em [rounds] rodadas. */
data class RhythmSession(val ts: Long, val maxBpm: Int, val hits: Int, val rounds: Int)

/**
 * Preferências do usuário (SharedPreferences: entram no Auto Backup junto com o banco).
 * A retenção é guardada em porcentagem inteira, para 90 virar exatamente 0.9 (um Float daria 0.89999998).
 */
class Settings(context: Context, name: String = "settings") {
    // [name] existe para os testes usarem um arquivo próprio e não mexerem nas suas configurações.
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** Retenção desejada do FSRS, em %. Afeta os próximos intervalos, não os já agendados. */
    var retentionPercent: Int
        get() = prefs.getInt("retention", 90)
        set(v) = prefs.edit().putInt("retention", v.coerceIn(70, 97)).apply()

    val retention: Double get() = retentionPercent / 100.0

    /** Quiz na notificação (M2). Começa desligado: ligar pede a permissão de notificações. */
    var quizEnabled: Boolean
        get() = prefs.getBoolean("quiz_enabled", false)
        set(v) = prefs.edit().putBoolean("quiz_enabled", v).apply()

    /** Janela do dia em que o quiz pode aparecer: [start, end) em horas locais. */
    var quizStartHour: Int
        get() = prefs.getInt("quiz_start", 9)
        set(v) = prefs.edit().putInt("quiz_start", v.coerceIn(0, 24)).apply()

    var quizEndHour: Int
        get() = prefs.getInt("quiz_end", 22)
        set(v) = prefs.edit().putInt("quiz_end", v.coerceIn(0, 24)).apply()

    /** Quando saiu o último quiz (epoch ms): garante no máximo 1 por hora. */
    var lastQuizAt: Long
        get() = prefs.getLong("last_quiz_at", 0L)
        set(v) = prefs.edit().putLong("last_quiz_at", v).apply()

    /** Apps que disparam o Pedágio (nomes de pacote). */
    var tollApps: Set<String>
        get() = prefs.getStringSet("toll_apps", emptySet()).orEmpty().toSet()
        set(v) = prefs.edit().putStringSet("toll_apps", v.toSet()).apply()

    /** Carência por app depois de pagar o Pedágio, em minutos. */
    var tollGraceMinutes: Int
        get() = prefs.getInt("toll_grace", 30)
        set(v) = prefs.edit().putInt("toll_grace", v.coerceIn(5, 180)).apply()

    /** O Pedágio só volta a cobrar [pkg] a partir deste instante (carência depois de pagar, ou pausa depois de "agora não"). */
    fun tollQuietUntil(pkg: String): Long = prefs.getLong("toll_until_$pkg", 0L)

    fun setTollQuietUntil(pkg: String, epochMs: Long) = prefs.edit().putLong("toll_until_$pkg", epochMs).apply()

    /** Contadores simples: o app só registra quantos pedágios foram pagos e quantos pulados. */
    var tollPaidCount: Int
        get() = prefs.getInt("toll_paid", 0)
        set(v) = prefs.edit().putInt("toll_paid", v).apply()

    var tollSkipCount: Int
        get() = prefs.getInt("toll_skips", 0)
        set(v) = prefs.edit().putInt("toll_skips", v).apply()

    /** Maior BPM já alcançado no modo Ritmo. */
    var rhythmBestBpm: Int
        get() = prefs.getInt("rhythm_best", 0)
        set(v) = prefs.edit().putInt("rhythm_best", v).apply()

    /** Últimas sessões do Ritmo (as 20 mais recentes, mais nova primeiro). Ficam à parte do FSRS. */
    fun rhythmSessions(): List<RhythmSession> =
        prefs.getString("rhythm_sessions", "").orEmpty().split(';').mapNotNull { row ->
            val f = row.split(',')
            if (f.size != 4) null else runCatching { RhythmSession(f[0].toLong(), f[1].toInt(), f[2].toInt(), f[3].toInt()) }.getOrNull()
        }

    fun addRhythmSession(session: RhythmSession) {
        val all = (listOf(session) + rhythmSessions()).take(20)
        prefs.edit().putString("rhythm_sessions", all.joinToString(";") { "${it.ts},${it.maxBpm},${it.hits},${it.rounds}" }).apply()
    }

    /** Kana novos por dia de estudo (cada um traz 3 cartões). */
    var newKanaPerDay: Int
        get() = prefs.getInt("new_per_day", 10)
        set(v) = prefs.edit().putInt("new_per_day", v.coerceIn(1, 20)).apply()
}
