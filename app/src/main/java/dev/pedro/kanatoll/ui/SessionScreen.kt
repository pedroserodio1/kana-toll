package dev.pedro.kanatoll.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.domain.KanaTable
import dev.pedro.kanatoll.domain.wrongKana
import kotlin.math.roundToInt

@Composable
fun SessionScreen(
    vm: SessionLike,
    onExit: () -> Unit,
    onMore: (() -> Unit)? = null,
    moreLabel: String = "Estudar mais 10 cartões",
    emptyText: String = "Nada para revisar agora.",
    emptyHint: String = "Volte mais tarde.",
) {
    val ui by vm.ui.collectAsState()
    val progress by vm.progress.collectAsState()
    // Sair da tela (por qualquer caminho) grava a resposta que ficou pendente.
    DisposableEffect(vm) { onDispose { vm.flush() } }
    Column(Modifier.fillMaxSize()) {
        if (ui is SessionUi.Asking || ui is SessionUi.Feedback) TopBar(progress, onExit) else Spacer(Modifier.height(70.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = ui) {
                SessionUi.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                SessionUi.Empty -> EmptyView(onExit, onMore, moreLabel, emptyText, emptyHint)
                is SessionUi.Asking -> AskingView(s, vm::submit, vm::speak)
                is SessionUi.Feedback -> FeedbackView(s, vm::next, vm::override)
                is SessionUi.Done -> DoneView(s, onExit, onMore, moreLabel)
            }
        }
    }
}

@Composable
private fun TopBar(p: SessionProgress, onExit: () -> Unit) {
    val animated by animateFloatAsState(p.fraction, label = "progress")
    Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Ring(animated, 42.dp, 6.dp, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.outlineVariant, startAngle = -90f)
        Spacer(Modifier.size(10.dp))
        Text("${p.done} de ${p.total}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        PillButton("Sair", onExit)
    }
}

@Composable
internal fun AskingView(s: SessionUi.Asking, onSubmit: (String) -> Unit, onSpeak: (String) -> Unit) {
    val card = s.q.card
    val prompt = when (card.direction) {
        Direction.KANA_TO_ROMAJI -> "Como se lê?"
        Direction.ROMAJI_TO_KANA -> "Qual é o kana?"
        Direction.AUDIO_TO_KANA -> "Qual kana você ouviu?"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text(prompt, fontFamily = KanaFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
        if (s.q.retry) Text("mais uma vez", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        // O círculo ocupa o espaço que sobra e encolhe quando o teclado abre; o kana acompanha.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth * 0.78f, maxHeight).coerceAtLeast(110.dp)
            TactileCard(
                Modifier.size(d),
                onClick = if (card.direction == Direction.AUDIO_TO_KANA) ({ onSpeak(card.kana) }) else null,
                radius = d / 2,
                lift = 7.dp,
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    when (card.direction) {
                        Direction.KANA_TO_ROMAJI -> KanaText(card.kana, (d.value * 0.5f).sp)
                        Direction.ROMAJI_TO_KANA -> KanaText(s.kana.romaji, (d.value * 0.4f).sp)
                        Direction.AUDIO_TO_KANA -> {
                            LaunchedEffect(s) { onSpeak(card.kana) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔊", fontSize = (d.value * 0.34f).sp)
                                Text("toque para ouvir de novo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (card.direction == Direction.KANA_TO_ROMAJI) TypedAnswer(onSubmit) else OptionGrid(s.options, onSubmit)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TypedAnswer(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val container = MaterialTheme.colorScheme.surfaceVariant
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = {
            Text("rōmaji", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, textAlign = TextAlign.Center, fontFamily = UiFont, fontWeight = FontWeight.Bold),
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit(text) }),
    )
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Responder", { onSubmit(text) }, enabled = text.isNotBlank())
}

/** Opções em linhas de 3 botões iguais; a última linha incompleta ganha espaçadores para manter o tamanho. */
@Composable
private fun OptionGrid(options: List<String>, onPick: (String) -> Unit) {
    options.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEach { k ->
                TactileButton(
                    onClick = { onPick(k) },
                    modifier = Modifier.weight(1f).height(98.dp),
                    fill = MaterialTheme.colorScheme.surfaceVariant,
                    edge = MaterialTheme.colorScheme.outlineVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    radius = 32.dp,
                    border = 3.dp,
                    lift = 4.dp,
                ) { KanaText(k, 44.sp) }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun FeedbackView(s: SessionUi.Feedback, onNext: () -> Unit, onOverride: (() -> Unit)? = null) {
    val card = s.q.card
    val scheme = MaterialTheme.colorScheme
    val strong = if (s.correct) scheme.primary else scheme.tertiary
    val onStrong = if (s.correct) scheme.onPrimary else scheme.onTertiary
    val tint = if (s.correct) scheme.primaryContainer else scheme.tertiaryContainer
    val onTint = if (s.correct) scheme.onPrimaryContainer else scheme.onTertiaryContainer
    val detail = if (s.correct) {
        "${card.kana} = ${s.kana.romaji}"
    } else {
        // O que você deu no lugar: o kana confundido e o rōmaji dele.
        val other = wrongKana(card, s.response)
        if (other != null) "Você respondeu $other = ${KanaTable.find(card.script, other).romaji}" else "Você respondeu ${s.response}"
    }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth * 0.8f, maxHeight - 40.dp).coerceAtLeast(120.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Anel aberto de argila no erro; fechado e verde no acerto.
                Ring(if (s.correct) 1f else 0.83f, d, 14.dp, strong, Color.Transparent, startAngle = -72f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KanaText(card.kana, (d.value * 0.46f).sp)
                        KanaText(s.kana.romaji, (d.value * 0.12f).sp, color = scheme.onSurfaceVariant)
                    }
                }
                if (!s.correct) {
                    Spacer(Modifier.height(12.dp))
                    Text("RESPOSTA CERTA", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(tint)
                .padding(start = 26.dp, top = 24.dp, end = 26.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(s.correct, background = strong, tint = onStrong)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        if (s.correct) "Certo!" else "Não foi dessa vez",
                        fontFamily = KanaFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = onTint,
                    )
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = onTint)
                }
            }
            if (s.canOverride && onOverride != null) {
                // Parece erro de digitação: a resposta só é gravada depois de você escolher.
                Spacer(Modifier.height(14.dp))
                ActionButton("Foi erro de digitação: vale como certo", onOverride, Modifier.fillMaxWidth(), filled = false)
            }
            Spacer(Modifier.height(if (s.canOverride && onOverride != null) 10.dp else 22.dp))
            PrimaryButton("Continuar", onNext, color = strong)
        }
    }
}

@Composable
private fun EmptyView(onExit: () -> Unit, onMore: (() -> Unit)?, moreLabel: String, text: String, hint: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        onMore?.let {
            ActionButton(moreLabel, it, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(10.dp))
        }
        PrimaryButton("Voltar", onExit)
    }
}

@Composable
private fun DoneView(s: SessionUi.Done, onExit: () -> Unit, onMore: (() -> Unit)?, moreLabel: String) {
    val percent = if (s.total == 0) 0 else (100f * s.correct / s.total).roundToInt()
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        KanaText("お疲れさま", 48.sp)
        Text("Sessão concluída", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        SoftCard(containerColor = MaterialTheme.colorScheme.secondary) {
            Text("$percent%", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSecondary)
            Text("${s.correct} de ${s.total} certos de primeira", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondary)
        }
        s.gateOpened?.let {
            Spacer(Modifier.height(14.dp))
            SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    if (it == Script.HIRAGANA) "Portão do hiragana aberto!" else "Portão do katakana aberto!",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (it == Script.HIRAGANA) Text("O katakana está liberado.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.weight(1f))
        onMore?.let {
            ActionButton(moreLabel, it, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(10.dp))
        }
        PrimaryButton("Voltar", onExit)
    }
}
