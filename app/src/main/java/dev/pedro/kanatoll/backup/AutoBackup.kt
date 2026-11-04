package dev.pedro.kanatoll.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.pedro.kanatoll.AppContainer
import dev.pedro.kanatoll.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFIX = "kana-backup-"

/** Dos arquivos da pasta, quais apagar: só os `kana-backup-*.json`, mantendo os [keep] mais recentes (a data está no nome). */
internal fun backupsToDelete(names: List<String>, keep: Int = 4): List<String> =
    names.filter { it.startsWith(PREFIX) && it.endsWith(".json") }.sortedDescending().drop(keep)

/**
 * Backup automático: uma vez por semana grava o JSON do histórico na pasta que você escolheu
 * (que pode ser uma pasta sincronizada pelo Drive) e guarda só os 4 últimos.
 */
object AutoBackup {
    private const val INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L

    /** Chamado a cada ~15 min pelo worker: só faz algo se houver pasta e a última cópia tiver mais de 7 dias. */
    suspend fun runIfDue(container: AppContainer, context: Context) {
        val settings = container.settings
        if (settings.autoBackupFolder.isEmpty()) return
        if (container.clock.millis() - settings.lastAutoBackup < INTERVAL_MS) return
        runNow(container, context)
    }

    /** Faz o backup agora. Devolve uma mensagem para mostrar (sucesso ou o motivo da falha). */
    suspend fun runNow(container: AppContainer, context: Context): String = withContext(Dispatchers.IO) {
        val settings = container.settings
        val folder = settings.autoBackupFolder.takeIf { it.isNotEmpty() } ?: return@withContext "Escolha uma pasta primeiro."
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(folder))?.takeIf { it.canWrite() }
                ?: error("sem acesso à pasta; escolha-a de novo")
            val json = container.backup.export()
            val name = "$PREFIX${LocalDate.now(container.clock)}.json"
            dir.findFile(name)?.delete() // no mesmo dia, substitui
            val file = dir.createFile("application/json", name) ?: error("não foi possível criar o arquivo")
            (context.contentResolver.openOutputStream(file.uri, "wt") ?: error("não foi possível gravar"))
                .use { it.write(json.toByteArray()) }
            val old = backupsToDelete(dir.listFiles().mapNotNull { it.name })
            dir.listFiles().filter { it.name in old }.forEach { it.delete() }
            settings.lastAutoBackup = container.clock.millis()
            "Backup salvo: $name"
        }.getOrElse { "Falha no backup automático: ${it.message}" }
    }

    private val whenFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    /** Linha de estado para a tela de Configurações. */
    fun describe(context: Context, settings: Settings): String {
        if (settings.autoBackupFolder.isEmpty()) return "Desligado. Escolha uma pasta para ligar."
        val name = DocumentFile.fromTreeUri(context, Uri.parse(settings.autoBackupFolder))?.name ?: "pasta escolhida"
        val last = settings.lastAutoBackup
        val lastText = if (last == 0L) "nenhum ainda" else Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(whenFormat)
        return "Pasta: $name · último: $lastText"
    }
}
