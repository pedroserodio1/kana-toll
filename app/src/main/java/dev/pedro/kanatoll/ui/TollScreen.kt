package dev.pedro.kanatoll.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pedro.kanatoll.toll.TollController

/**
 * A rodada do Pedágio, desenhada dentro do overlay. Reaproveita os cartões da sessão
 * ([AskingView] e [FeedbackView]); só o topo é próprio. "Agora não" fica sempre visível.
 */
@Composable
fun TollScreen(controller: TollController, appLabel: String, onSkip: () -> Unit) {
    val ui by controller.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Antes de abrir $appLabel", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val position = when (val s = ui) {
                    is SessionUi.Asking -> controller.total - s.remaining + 1
                    is SessionUi.Feedback -> controller.total - s.remaining + 1
                    else -> 1
                }
                Text(
                    "Cartão $position de ${controller.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillButton("Agora não", onSkip)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = ui) {
                is SessionUi.Asking -> AskingView(s, controller::submit, onSpeak = {}) // o Pedágio não usa cartões de áudio
                is SessionUi.Feedback -> FeedbackView(s, controller::next)
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}
