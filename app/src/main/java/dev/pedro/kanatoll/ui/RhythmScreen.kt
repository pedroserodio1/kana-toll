package dev.pedro.kanatoll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedro.kanatoll.data.RhythmSession
import dev.pedro.kanatoll.rhythm.RhythmRules
import dev.pedro.kanatoll.rhythm.RhythmUi
import dev.pedro.kanatoll.rhythm.RhythmViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RhythmScreen(vm: RhythmViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val phase = vm.phase.collectAsState()
    // Sair da tela (por qualquer caminho) para o metrônomo.
    DisposableEffect(vm) { onDispose { vm.stop() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Header("Ritmo") { vm.stop(); onBack() }
        when (val s = ui) {
            RhythmUi.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
            is RhythmUi.Idle -> IdleView(s, vm::start)
            is RhythmUi.Playing -> PlayingView(s, phase, vm::pick) { vm.finish() }
            is RhythmUi.Done -> DoneView(s, vm::again, onBack)
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm")

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.IdleView(s: RhythmUi.Idle, onStart: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    SoftCard {
        Text("Leitura sob batida", style = MaterialTheme.typography.titleMedium)
        Hint("A cada batida aparece um kana. Toque o rōmaji certo antes da próxima batida: acertar sobe o BPM, errar ou deixar passar desce. Só entram kana com estabilidade de 7 dias ou mais, e nada aqui alimenta o FSRS.")
    }
    Spacer(Modifier.height(14.dp))
    SoftCard {
        Text("Kana disponíveis: ${s.eligible}", style = MaterialTheme.typography.titleMedium)
        if (s.eligible < RhythmRules.MIN_POOL) {
            Hint("Precisa de pelo menos ${RhythmRules.MIN_POOL}. Eles aparecem aqui quando o cartão de leitura chega a 7 dias de estabilidade.")
        }
        Spacer(Modifier.height(6.dp))
        Text(if (s.bestBpm > 0) "Recorde: ${s.bestBpm} BPM" else "Sem recorde ainda", style = MaterialTheme.typography.bodyLarge)
        s.recent.forEach { Text(sessionLine(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton("Começar", onStart, enabled = s.eligible >= RhythmRules.MIN_POOL)
    Spacer(Modifier.height(12.dp))
}

private fun sessionLine(r: RhythmSession): String =
    "${Instant.ofEpochMilli(r.ts).atZone(ZoneId.systemDefault()).format(dateFormat)}   até ${r.maxBpm} BPM · ${r.hits}/${r.rounds}"

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlayingView(
    s: RhythmUi.Playing,
    phase: State<Float>,
    onPick: (String) -> Unit,
    onStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text("${s.bpm}", fontFamily = KanaFont, fontSize = 56.sp, lineHeight = 60.sp)
        Text("  BPM", Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("${s.hits} acertos", style = MaterialTheme.typography.titleMedium)
            Text("rodada ${s.round} de ${RhythmRules.ROUNDS}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
    // O anel esvazia até a próxima batida: é o tempo que sobra para responder.
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth * 0.82f, maxHeight).coerceAtLeast(140.dp)
        Ring(
            progress = 1f - phase.value,
            diameter = d,
            stroke = 14.dp,
            color = if (s.picked == null) scheme.primary else scheme.outline,
            track = scheme.outlineVariant,
            startAngle = -90f,
        ) { KanaText(s.glyph, (d.value * 0.46f).sp) }
    }
    s.options.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { option ->
                val (fill, edge, content) = when {
                    s.picked != null && option == s.correct -> Triple(scheme.primary, scheme.primary.darker(), scheme.onPrimary)
                    option == s.picked -> Triple(scheme.tertiary, scheme.tertiary.darker(), scheme.onTertiary)
                    else -> Triple(scheme.surfaceVariant, scheme.outlineVariant, scheme.onSurface)
                }
                TactileButton(
                    onClick = { onPick(option) },
                    modifier = Modifier.weight(1f).height(84.dp),
                    fill = fill,
                    edge = edge,
                    contentColor = content,
                    radius = 28.dp,
                    border = 3.dp,
                    lift = 4.dp,
                ) { Text(option, fontFamily = KanaFont, fontSize = 30.sp) }
            }
        }
    }
    TextButton(onClick = onStop, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Parar") }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DoneView(s: RhythmUi.Done, onAgain: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.weight(1f))
    SoftCard(containerColor = MaterialTheme.colorScheme.secondary) {
        Text("Até ${s.maxBpm} BPM", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSecondary)
        Text(
            "${s.hits} acertos em ${s.rounds} rodadas",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondary,
        )
        if (s.newBest) {
            Spacer(Modifier.height(6.dp))
            Text("Novo recorde!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondary, textAlign = TextAlign.Start)
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton("Jogar de novo", onAgain)
    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Voltar") }
}
