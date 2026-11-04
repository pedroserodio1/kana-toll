package dev.pedro.kanatoll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Source
import dev.pedro.kanatoll.domain.AccuracyCount
import dev.pedro.kanatoll.domain.StatsSummary
import dev.pedro.kanatoll.data.Script
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private fun pct(a: AccuracyCount): String = if (a.total == 0) "—" else "${(a.rate * 100).roundToInt()}%"

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as KanaApp).container
    val stats by produceState<StatsSummary?>(null) { value = container.reviews.stats() }
    val settings = container.settings

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Header("Estatísticas", onBack)
        }
        val s = stats
        if (s == null) {
            item { Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        item { Overview(s) }
        item { Last14Days(s) }
        item { Progress(s) }
        item { DueForecast(s) }
        item { ByDirection(s) }
        item { Trouble(s) }
        item {
            SoftCard {
                Text("Outros modos", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Line("Pedágio", "${settings.tollPaidCount} pagos · ${settings.tollSkipCount} pulados")
                Line("Ritmo", if (settings.rhythmBestBpm > 0) "recorde de ${settings.rhythmBestBpm} BPM" else "sem partidas ainda")
                Line(
                    "Respostas por origem",
                    "${s.bySource.getValue(Source.SESSION)} sessão · ${s.bySource.getValue(Source.NOTIFICATION)} notificação · ${s.bySource.getValue(Source.TOLL)} pedágio",
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Overview(s: StatsSummary) {
    SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth()) {
            Big("${s.overall.total}", "respostas", Modifier.weight(1f))
            Big(pct(s.overall), "de acerto", Modifier.weight(1f))
            Big(pct(s.last7Days), "últimos 7 dias", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${s.streak} ${if (s.streak == 1) "dia seguido" else "dias seguidos"} · ${s.studyDays} ${if (s.studyDays == 1) "dia estudado" else "dias estudados"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Conta só a primeira resposta de cada cartão; as re-perguntas depois de um erro ficam de fora.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Big(number: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(number, fontFamily = KanaFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val dayLabel = DateTimeFormatter.ofPattern("d")

@Composable
private fun Last14Days(s: StatsSummary) {
    val scheme = MaterialTheme.colorScheme
    SoftCard {
        Text("Últimos 14 dias", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Bars(
            s.perDay.map { d ->
                Bar(
                    label = d.day.format(dayLabel),
                    caption = if (d.total == 0) "" else "${d.total}",
                    parts = listOf(d.correct to scheme.primary, (d.total - d.correct) to scheme.tertiary),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        Legend(listOf("acertos" to scheme.primary, "erros" to scheme.tertiary))
    }
}

@Composable
private fun DueForecast(s: StatsSummary) {
    val scheme = MaterialTheme.colorScheme
    SoftCard {
        Text("Carga dos próximos dias", style = MaterialTheme.typography.titleMedium)
        Hint("Cartões vencendo em cada dia (hoje inclui os atrasados). Passando de 100 por dia, vale baixar os kana novos nas Configurações.")
        Spacer(Modifier.height(10.dp))
        Bars(
            s.dueNext7Days.mapIndexed { i, n ->
                Bar(
                    label = when (i) { 0 -> "hoje"; 1 -> "amanhã"; else -> "+$i" },
                    caption = if (n == 0) "" else "$n",
                    parts = listOf(n to (if (n > 100) scheme.tertiary else scheme.secondary)),
                )
            },
        )
    }
}

@Composable
private fun Progress(s: StatsSummary) {
    val scheme = MaterialTheme.colorScheme
    SoftCard {
        Text("Progresso", style = MaterialTheme.typography.titleMedium)
        if (s.scripts.isEmpty()) Hint("Comece uma sessão para ver o progresso.")
        s.scripts.forEach { (script, p) ->
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (script == Script.HIRAGANA) "Hiragana" else "Katakana", style = MaterialTheme.typography.titleSmall)
                Text("${p.introducedKana}/${p.totalKana} kana apresentados", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            // Faixas de estabilidade dos cartões de leitura e produção (os que contam para o portão).
            val total = p.fresh + p.learning + p.stable + p.solid
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(scheme.outlineVariant)) {
                listOf(p.solid to scheme.primary, p.stable to scheme.primary.copy(alpha = 0.6f), p.learning to scheme.secondary).forEach { (n, color) ->
                    if (n > 0 && total > 0) Box(Modifier.weight(n.toFloat()).height(14.dp).background(color))
                }
                // o resto (novos) fica com a cor de fundo da barra
                if (p.fresh > 0 && total > 0) Spacer(Modifier.weight(p.fresh.toFloat()))
            }
            Spacer(Modifier.height(6.dp))
            Legend(
                listOf(
                    "${p.solid} sólidos (30 d+)" to scheme.primary,
                    "${p.stable} estáveis (7 d+)" to scheme.primary.copy(alpha = 0.6f),
                    "${p.learning} aprendendo" to scheme.secondary,
                    "${p.fresh} novos" to scheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun ByDirection(s: StatsSummary) {
    SoftCard {
        Text("Acerto por tipo de cartão", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        listOf(
            Direction.KANA_TO_ROMAJI to "Kana → rōmaji (digitar)",
            Direction.ROMAJI_TO_KANA to "Rōmaji → kana (escolher)",
            Direction.AUDIO_TO_KANA to "Áudio → kana (treino)",
        ).forEach { (dir, name) ->
            val a = s.byDirection.getValue(dir)
            Line(name, if (a.total == 0) "sem respostas" else "${pct(a)} · ${a.correct}/${a.total}")
        }
    }
}

@Composable
private fun Trouble(s: StatsSummary) {
    val scheme = MaterialTheme.colorScheme
    SoftCard {
        Text("Onde você erra", style = MaterialTheme.typography.titleMedium)
        if (s.trouble.isEmpty()) {
            Hint("Ainda não há kana com erros e pelo menos 3 respostas.")
        } else {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.trouble.take(3).forEach { Trap(it.glyph, it.romaji, "${(it.errorRate * 100).roundToInt()}% erros", Modifier.weight(1f)) }
            }
            if (s.trouble.size > 3) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.trouble.drop(3).forEach { Trap(it.glyph, it.romaji, "${(it.errorRate * 100).roundToInt()}% erros", Modifier.weight(1f)) }
                    repeat(3 - (s.trouble.size - 3)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (s.confusions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Trocas mais comuns", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            s.confusions.forEach { Line("${it.expected} respondido como ${it.given}", "${it.count}×") }
        }
        Hint("Os kana de maior taxa de erro vão aparecer mais como opções parecidas nas perguntas de escolha.")
    }
}

@Composable
private fun Trap(glyph: String, romaji: String, rate: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KanaText(glyph, 34.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(romaji, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(rate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

private class Bar(val label: String, val caption: String, val parts: List<Pair<Int, Color>>)

/** Barras empilhadas: a altura vem do total relativo ao maior; cada parte é uma fatia colorida. */
@Composable
private fun Bars(bars: List<Bar>, height: Dp = 110.dp) {
    val max = bars.maxOf { b -> b.parts.sumOf { it.first } }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { b ->
            val total = b.parts.sumOf { it.first }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(b.caption, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.BottomCenter) {
                    if (total == 0) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    } else {
                        Column(Modifier.fillMaxWidth().height(height * (total.toFloat() / max)).clip(RoundedCornerShape(4.dp))) {
                            // a fatia de cima é a última da lista (erros), a de baixo a primeira (acertos)
                            b.parts.filter { it.first > 0 }.reversed().forEach { (n, color) ->
                                Box(Modifier.fillMaxWidth().weight(n.toFloat()).background(color))
                            }
                        }
                    }
                }
                Text(b.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (text, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(4.dp))
                Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
