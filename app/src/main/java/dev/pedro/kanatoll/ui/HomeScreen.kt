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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.GateProgress
import dev.pedro.kanatoll.data.HomeStats

@Composable
fun HomeScreen(
    onStudy: () -> Unit,
    onAhead: () -> Unit,
    onTrace: () -> Unit,
    onRhythm: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val repo = (LocalContext.current.applicationContext as KanaApp).container.reviews
    var stats by remember { mutableStateOf<HomeStats?>(null) }
    // A tela é recriada ao voltar da sessão, então isto recarrega os números.
    LaunchedEffect(Unit) { stats = repo.homeStats() }

    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("かな", fontFamily = KanaFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp)
            Spacer(Modifier.weight(1f))
            PillButton("Estatísticas", onStats)
            Spacer(Modifier.padding(horizontal = 4.dp))
            PillButton("Configurações", onSettings)
        }
        val s = stats
        if (s == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        // O anel mostra o portão em andamento: o do hiragana e, depois que abre, o do katakana.
        val gate = s.katakana ?: s.hiragana
        val gateName = if (s.katakana != null) "katakana" else "hiragana"
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // maxHeight é do BoxWithConstraints: lido aqui, antes do Column, que tem outro escopo de layout.
            val ring = (maxHeight * 0.42f).coerceIn(190.dp, 280.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Ring(
                    progress = gate.fraction(),
                    diameter = ring,
                    stroke = 18.dp,
                    color = MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.outlineVariant,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${s.due}", fontFamily = KanaFont, fontWeight = FontWeight.SemiBold, fontSize = (ring.value * 0.36f).sp, lineHeight = (ring.value * 0.4f).sp)
                        Text("para revisar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Bloco amarelo (secondary): kana novos de hoje.
                    TactileCard(
                        Modifier.weight(1f),
                        fill = MaterialTheme.colorScheme.secondary,
                        edge = MaterialTheme.colorScheme.secondary.darker(0.2f),
                        border = 0.dp,
                        lift = 6.dp,
                    ) { Tile("${s.newLeft}", "kana novos", MaterialTheme.colorScheme.onSecondary) }
                    TactileCard(Modifier.weight(1f)) {
                        Tile("${gate.passed}", "portão do $gateName", MaterialTheme.colorScheme.onSurface, suffix = "/${gate.total}")
                    }
                }
                if (s.goal > 0) {
                    Spacer(Modifier.height(12.dp))
                    val reached = s.todayAnswers >= s.goal
                    Text(
                        if (reached) "Meta de hoje batida: ${s.todayAnswers} respostas" else "Meta de hoje: ${s.todayAnswers} de ${s.goal} respostas",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "O anel mostra o portão. O katakana libera quando todos os cartões chegarem a 7 dias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Sem nada devido: dá para estudar os próximos cartões à frente.
        if (s.due == 0) {
            ModeButton("Estudar à frente", onAhead, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        // Modos extras: treino à parte, não contam para o portão.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ModeButton("Traçado", onTrace, Modifier.weight(1f))
            ModeButton("Ritmo", onRhythm, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Estudar", onStudy, enabled = s.due > 0 || s.newLeft > 0)
    }
}

private fun GateProgress.fraction(): Float = if (total == 0) 0f else passed.toFloat() / total

@Composable
private fun Tile(number: String, label: String, color: androidx.compose.ui.graphics.Color, suffix: String = "") {
    Column(Modifier.fillMaxWidth().padding(16.dp, 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(number, fontFamily = KanaFont, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = color)
            if (suffix.isNotEmpty()) Text(suffix, style = MaterialTheme.typography.titleSmall, color = color.copy(alpha = 0.75f))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun ModeButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    TactileButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        fill = MaterialTheme.colorScheme.surfaceVariant,
        edge = MaterialTheme.colorScheme.outlineVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        radius = 24.dp,
        border = 3.dp,
        lift = 4.dp,
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** Botão pequeno e arredondado, para os atalhos do topo. */
@Composable
fun PillButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.outlineVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge) }
}
