package dev.pedro.kanatoll.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.trace.KanaStrokes
import dev.pedro.kanatoll.trace.Pt
import dev.pedro.kanatoll.trace.StrokeMatcher
import dev.pedro.kanatoll.trace.Verdict
import kotlinx.coroutines.delay

/**
 * Modo de traçado: você vê o rōmaji e desenha o kana, traço a traço, na ordem certa. Cada traço é
 * comparado com o do KanjiVG. É treino à parte: não conta para o portão nem para o FSRS.
 */
@Composable
fun TraceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as KanaApp).container.reviews
    val strokes = remember { KanaStrokes.load(context) }

    var pool by remember { mutableStateOf<List<Pair<Script, String>>?>(null) }
    var target by remember { mutableStateOf<Pair<Script, String>?>(null) }
    LaunchedEffect(Unit) {
        pool = Script.entries.flatMap { s -> repo.introducedKana(s).map { s to it } }
    }
    LaunchedEffect(pool) { if (target == null) target = pool?.randomOrNull() }

    var guide by remember { mutableStateOf(true) }
    var solved by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Header("Traçado", onBack)
        val list = pool
        val current = target
        when {
            list == null || (list.isNotEmpty() && current == null) ->
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("Estude uma sessão primeiro: ainda não há kana apresentados.", textAlign = TextAlign.Center)
            }
            else -> {
                val kana = checkNotNull(current)
                val glyph = remember(kana) { strokes.layout(kana.second) }
                if (glyph == null) {
                    Text("Sem traços para ${kana.second}.")
                } else {
                    TraceBoard(
                        script = kana.first,
                        kana = kana.second,
                        glyph = glyph,
                        guide = guide,
                        onGuide = { guide = it },
                        solved = solved,
                        onSolved = { solved += 1 },
                        onNext = { target = (list - kana).randomOrNull() ?: kana },
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.TraceBoard(
    script: Script,
    kana: String,
    glyph: dev.pedro.kanatoll.trace.TraceGlyph,
    guide: Boolean,
    onGuide: (Boolean) -> Unit,
    solved: Int,
    onSolved: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Estado do traçado deste kana: recomeça sempre que o kana muda.
    var done by remember(glyph) { mutableIntStateOf(0) }
    var misses by remember(glyph) { mutableIntStateOf(0) }
    var message by remember(glyph) { mutableStateOf("Comece pelo ponto verde.") }
    var flash by remember(glyph) { mutableStateOf<List<Offset>>(emptyList()) }
    val live = remember(glyph) { mutableStateListOf<Offset>() }
    val complete = done == glyph.strokes.size

    LaunchedEffect(flash) {
        if (flash.isNotEmpty()) {
            delay(600)
            flash = emptyList()
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        KanaTable.find(script, kana).romaji,
        Modifier.fillMaxWidth(),
        fontFamily = KanaFont,
        fontSize = 56.sp,
        textAlign = TextAlign.Center,
    )
    Text(
        "Trace o kana, traço a traço, na ordem certa.",
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    TactileCard(Modifier.fillMaxWidth().aspectRatio(glyph.cells.toFloat())) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(glyph, done) {
                detectDragGestures(
                    onDragStart = { live.clear(); live.add(it) },
                    onDrag = { change, _ -> live.add(change.position); change.consume() },
                    onDragCancel = { live.clear() },
                    onDragEnd = {
                        if (complete || live.size < 2) {
                            live.clear()
                        } else {
                            val scale = size.width / glyph.width
                            val drawn = live.map { Pt(it.x / scale, it.y / scale) }
                            val raw = live.toList()
                            live.clear()
                            when (StrokeMatcher.judge(drawn, glyph.strokes, done)) {
                                Verdict.OK -> {
                                    done += 1
                                    misses = 0
                                    if (done == glyph.strokes.size) {
                                        message = "Certo!"
                                        onSolved()
                                    } else {
                                        message = "Muito bem. Próximo traço."
                                    }
                                }
                                Verdict.WRONG_DIRECTION -> { misses += 1; flash = raw; message = "Direção ao contrário: comece pelo ponto verde." }
                                Verdict.WRONG_ORDER -> { misses += 1; flash = raw; message = "Ordem errada: esse traço vem depois." }
                                Verdict.MISS -> { misses += 1; flash = raw; message = "Não confere. Tente de novo." }
                            }
                        }
                    },
                )
            },
        ) {
            val s = size.width / glyph.width
            val width = 12.dp.toPx()
            fun line(points: List<Pt>) = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * s, p.y * s) else lineTo(p.x * s, p.y * s) }
            }
            fun polyline(points: List<Offset>) = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            val pen = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // com 3 erros seguidos o traço esperado aparece mesmo sem o guia
            val hint = !complete && (guide || misses >= 3)
            if (guide) for (i in done until glyph.strokes.size) drawPath(line(glyph.strokes[i]), scheme.outlineVariant, style = pen)
            if (hint) {
                drawPath(line(glyph.strokes[done]), scheme.primary.copy(alpha = 0.45f), style = pen)
                val start = glyph.strokes[done].first()
                drawCircle(scheme.primary, 9.dp.toPx(), Offset(start.x * s, start.y * s))
            }
            for (i in 0 until done) drawPath(line(glyph.strokes[i]), scheme.onSurface, style = pen)
            if (live.size > 1) drawPath(polyline(live), scheme.primary, style = pen)
            if (flash.size > 1) drawPath(polyline(flash), scheme.tertiary, style = pen)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        message,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = if (complete) scheme.primary else scheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        if (solved == 0) "" else "$solved traçado${if (solved == 1) "" else "s"} certo${if (solved == 1) "" else "s"} nesta sessão",
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = guide, onCheckedChange = onGuide)
            Text("  Guia", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = { done = 0; misses = 0; message = "Comece pelo ponto verde."; flash = emptyList() }) { Text("Limpar") }
    }
    Spacer(Modifier.height(8.dp))
    PrimaryButton(if (complete) "Próximo kana" else "Pular", onNext)
    Spacer(Modifier.height(12.dp))
}
