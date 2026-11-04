package dev.pedro.kanatoll.toll

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnóstico do Pedágio: conexões e desconexões do serviço e cada rodada (mostrada, paga, pulada).
 * Vai para um arquivo, para sobreviver a reboot e mostrar quando a One UI derrubou o serviço.
 */
object TollLog {
    private const val MAX_LINES = 60
    private val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
    private lateinit var file: File

    /** Mais recente primeiro. Observável pelo Compose. */
    val lines = mutableStateListOf<String>()

    fun init(context: Context) {
        file = File(context.filesDir, "toll.log")
        if (file.exists()) lines.addAll(file.readLines().takeLast(MAX_LINES).reversed())
    }

    fun add(message: String) {
        Log.d("KanaToll", message) // também no Logcat (filtro: tag:KanaToll)
        val line = "${LocalDateTime.now().format(fmt)}  $message"
        runCatching { file.appendText(line + "\n") }
        lines.add(0, line)
        while (lines.size > MAX_LINES) lines.removeAt(lines.lastIndex)
    }
}
