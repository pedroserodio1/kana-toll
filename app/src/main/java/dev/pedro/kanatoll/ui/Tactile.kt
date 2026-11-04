package dev.pedro.kanatoll.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Versão mais escura de uma cor, para a borda inferior dos botões e cartões. */
fun Color.darker(amount: Float = 0.18f): Color = lerp(this, Color.Black, amount)

private fun readableOn(fill: Color): Color = if (fill.luminance() > 0.5f) Color(0xFF26302A) else Color.White

/**
 * Botão "tátil": a face e uma base mais escura que aparece embaixo ([lift]). Ao apertar, a face
 * desce até cobrir a base. Com [border] > 0 vira um botão de contorno com a borda inferior mais grossa.
 * O tamanho vem do chamador (altura e largura no [modifier]).
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    edge: Color = fill.darker(),
    contentColor: Color = readableOn(fill),
    radius: Dp = 28.dp,
    border: Dp = 0.dp,
    lift: Dp = 6.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bottom by animateDpAsState(if (pressed) 0.dp else lift, label = "lift")
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(radius)).background(edge))
        Box(
            Modifier
                .matchParentSize()
                .padding(start = border, top = border, end = border, bottom = border + bottom)
                .clip(RoundedCornerShape((radius - border).coerceAtLeast(0.dp)))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) { CompositionLocalProvider(LocalContentColor provides contentColor) { content() } }
    }
}

/** Cartão com o mesmo desenho do botão tátil, sem clique. Ajusta a altura ao conteúdo. */
@Composable
fun TactileCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    fill: Color = MaterialTheme.colorScheme.surfaceVariant,
    edge: Color = MaterialTheme.colorScheme.outlineVariant,
    contentColor: Color = contentColorFor(fill).let { if (it == Color.Unspecified) readableOn(fill) else it },
    radius: Dp = 28.dp,
    border: Dp = 3.dp,
    lift: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val outer = if (onClick == null) modifier else modifier.clip(RoundedCornerShape(radius)).clickable(role = Role.Button, onClick = onClick)
    Box(outer) {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(radius)).background(edge))
        Box(
            Modifier
                .padding(start = border, top = border, end = border, bottom = border + lift)
                .clip(RoundedCornerShape((radius - border).coerceAtLeast(0.dp)))
                .background(fill),
        ) { CompositionLocalProvider(LocalContentColor provides contentColor) { content() } }
    }
}

/**
 * Anel aberto (ensō). [progress] de 0 a 1 em sentido horário a partir de [startAngle].
 * Com [track] transparente e progresso < 1, fica só o traço aberto.
 */
@Composable
fun Ring(
    progress: Float,
    diameter: Dp,
    stroke: Dp,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    startAngle: Float = -96f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val width = stroke.toPx()
            val topLeft = Offset(width / 2, width / 2)
            val arc = Size(this.size.width - width, this.size.height - width)
            if (track != Color.Transparent) drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(width))
            if (progress > 0f) {
                drawArc(color, startAngle, 360f * progress.coerceIn(0f, 1f), false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Círculo com um X ou um visto, desenhado à mão (sem biblioteca de ícones). */
@Composable
fun StatusIcon(correct: Boolean, background: Color, tint: Color, modifier: Modifier = Modifier, diameter: Dp = 52.dp) {
    Canvas(modifier.size(diameter)) {
        drawCircle(background)
        val s = this.size.width
        val line = Stroke(s * 0.075f, cap = StrokeCap.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            if (correct) {
                moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.67f); lineTo(s * 0.73f, s * 0.35f)
            } else {
                moveTo(s * 0.32f, s * 0.32f); lineTo(s * 0.68f, s * 0.68f)
                moveTo(s * 0.68f, s * 0.32f); lineTo(s * 0.32f, s * 0.68f)
            }
        }
        drawPath(path, tint, style = line)
    }
}
