package dev.pedro.kanatoll.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.toll.TollLog
import dev.pedro.kanatoll.toll.TollService
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

/** Apps com tela de abertura (os que aparecem na gaveta), sem o próprio Kana. */
private suspend fun launchableApps(context: Context): List<AppEntry> = withContext(Dispatchers.Default) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(pkg).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap())
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun TollSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as KanaApp).container.settings
    var selected by remember { mutableStateOf(settings.tollApps) }
    var grace by remember { mutableIntStateOf(settings.tollGraceMinutes) }
    var tick by remember { mutableIntStateOf(0) } // força reler o estado do serviço
    val enabled = remember(tick) { TollService.isEnabled(context) }
    val connected = remember(tick) { TollService.instance != null }
    val apps by produceState<List<AppEntry>?>(null) { value = launchableApps(context) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Header("Pedágio", onBack)
        }
        item {
            SoftCard {
                Text("Serviço de acessibilidade", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        connected -> "Ligado e funcionando."
                        enabled -> "Ligado nas configurações, mas ainda não conectou."
                        else -> "DESLIGADO: o Pedágio não funciona assim."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Hint("A One UI pode desligar o serviço sozinha, e reinstalar o app também desliga. Volte aqui se o Pedágio parar de aparecer.")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        "Acessibilidade",
                        { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        Modifier.weight(1f),
                    )
                    ActionButton("Atualizar", { tick++ }, Modifier.weight(1f), filled = false)
                }
            }
        }
        item {
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Carência por app", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("$grace min", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = grace.toFloat(),
                    onValueChange = { grace = (it / 5).roundToInt() * 5 },
                    onValueChangeFinished = { settings.tollGraceMinutes = grace },
                    valueRange = 5f..180f,
                    steps = 34,
                )
                Hint("Depois de pagar, o app não cobra de novo durante esse tempo. Cada abertura cobra 3 cartões devidos, que contam como revisão de verdade. \"Agora não\" é sempre permitido.")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pagos: ${settings.tollPaidCount} · Pulados: ${settings.tollSkipCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { Text("Apps com Pedágio", style = MaterialTheme.typography.titleMedium) }
        val list = apps
        if (list == null) {
            item { Text("Carregando apps...", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(list, key = { it.pkg }) { app ->
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Switch(checked = app.pkg in selected, onCheckedChange = { on ->
                            selected = if (on) selected + app.pkg else selected - app.pkg
                            settings.tollApps = selected
                        })
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Diagnóstico (mais recente primeiro)", style = MaterialTheme.typography.titleSmall)
        }
        items(TollLog.lines.take(15)) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
