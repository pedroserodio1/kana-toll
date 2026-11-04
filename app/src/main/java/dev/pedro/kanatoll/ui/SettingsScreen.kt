package dev.pedro.kanatoll.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.notif.QuizNotifier
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit, onToll: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as KanaApp).container
    val settings = container.settings
    val scope = rememberCoroutineScope()

    var retention by remember { mutableIntStateOf(settings.retentionPercent) }
    var perDay by remember { mutableIntStateOf(settings.newKanaPerDay) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    var quizOn by remember { mutableStateOf(settings.quizEnabled && QuizNotifier.canNotify(context)) }
    var startHour by remember { mutableIntStateOf(settings.quizStartHour) }
    var endHour by remember { mutableIntStateOf(settings.quizEndHour) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            settings.quizEnabled = true
            quizOn = true
        } else {
            status = "Sem permissão de notificações: o quiz fica desligado."
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                status = runCatching {
                    val text = container.backup.export()
                    withContext(Dispatchers.IO) {
                        (context.contentResolver.openOutputStream(uri, "wt") ?: error("não foi possível abrir o arquivo"))
                            .use { it.write(text.toByteArray()) }
                    }
                    "Backup exportado."
                }.getOrElse { "Falha ao exportar: ${it.message}" }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Importar backup?") },
            text = { Text("Isto substitui todo o histórico atual pelo do arquivo.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        status = runCatching {
                            val text = withContext(Dispatchers.IO) {
                                (context.contentResolver.openInputStream(uri) ?: error("não foi possível abrir o arquivo"))
                                    .use { it.readBytes().decodeToString() }
                            }
                            "Backup importado: ${container.backup.import(text)} cartões."
                        }.getOrElse { "Falha ao importar (nada foi alterado): ${it.message}" }
                    }
                }) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancelar") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Header("Configurações", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            SoftCard {
                Text("Pedágio", style = MaterialTheme.typography.titleMedium)
                Hint("Antes de abrir os apps que você escolher, algumas revisões de kana. Precisa do serviço de acessibilidade ligado.")
                Spacer(Modifier.height(10.dp))
                ActionButton("Configurar o Pedágio", onToll, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Retenção desejada", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("$retention%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = retention.toFloat(),
                    onValueChange = { retention = it.roundToInt() },
                    onValueChangeFinished = { settings.retentionPercent = retention },
                    valueRange = 70f..97f,
                    steps = 26,
                )
                Hint("Vale para os próximos intervalos; os já agendados não mudam.")
            }
            Spacer(Modifier.height(16.dp))
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Kana novos por dia", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("$perDay", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = perDay.toFloat(),
                    onValueChange = { perDay = it.roundToInt() },
                    onValueChangeFinished = { settings.newKanaPerDay = perDay },
                    valueRange = 1f..20f,
                    steps = 18,
                )
                Hint("Cada kana traz 3 cartões. Se a carga passar de 100 revisões por dia, baixe para 5.")
            }
            Spacer(Modifier.height(16.dp))
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quiz na notificação", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = quizOn, onCheckedChange = { on ->
                        when {
                            !on -> { settings.quizEnabled = false; quizOn = false }
                            QuizNotifier.canNotify(context) -> { settings.quizEnabled = true; quizOn = true }
                            else -> notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                }
                Hint("Uma pergunta kana → rōmaji por vez, respondida digitando na própria notificação. No máximo 1 por hora, só quando há cartão devido.")
                Spacer(Modifier.height(10.dp))
                Text("Das ${startHour}h", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { startHour = it.roundToInt() },
                    onValueChangeFinished = { settings.quizStartHour = startHour },
                    valueRange = 0f..23f,
                    steps = 22,
                )
                Text("Até ${endHour}h", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = endHour.toFloat(),
                    onValueChange = { endHour = it.roundToInt() },
                    onValueChangeFinished = { settings.quizEndHour = endHour },
                    valueRange = 1f..24f,
                    steps = 22,
                )
                Spacer(Modifier.height(6.dp))
                ActionButton(
                    "Enviar notificação de teste",
                    { scope.launch { status = QuizNotifier.postTest(context) } },
                    Modifier.fillMaxWidth(),
                    filled = false,
                )
            }
            Spacer(Modifier.height(16.dp))
            SoftCard {
                Text("Segundo plano", style = MaterialTheme.typography.titleMedium)
                val ignoringBattery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
                Hint(
                    if (ignoringBattery) "O Android não restringe o app na bateria."
                    else "O Android pode atrasar o quiz e o widget para economizar bateria.",
                )
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    "Pedir isenção de bateria",
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                        )
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !ignoringBattery,
                    filled = false,
                )
                Spacer(Modifier.height(8.dp))
                Hint("Na One UI, adicione também o Kana em Configurações → Bateria → Limites de uso em segundo plano → Apps que nunca suspendem.")
            }
            Spacer(Modifier.height(16.dp))
            SoftCard {
                Text("Backup", style = MaterialTheme.typography.titleMedium)
                Hint("O histórico do FSRS é o que você não pode perder.")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("Exportar", { exportLauncher.launch("kana-backup-${LocalDate.now()}.json") }, Modifier.weight(1f))
                    ActionButton("Importar", { importLauncher.launch(arrayOf("*/*")) }, Modifier.weight(1f), filled = false)
                }
                status?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAbout) { Text("Sobre e fontes") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onBack) { Text("Voltar") }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
