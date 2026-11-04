package dev.pedro.kanatoll.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Botão principal: largo, alto, tátil. [color] muda a cor (ex.: argila no erro); o padrão é o musgo. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TactileButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(66.dp),
        fill = color,
        radius = 30.dp,
        enabled = enabled,
    ) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
}

/**
 * Botão tátil de ações secundárias (dentro de cartões): [filled] usa o musgo; sem ele fica só de contorno.
 * A largura vem do chamador (`fillMaxWidth()` ou `weight(1f)` numa linha).
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    TactileButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        fill = if (filled) scheme.primary else scheme.surface,
        edge = if (filled) scheme.primary.darker() else scheme.outlineVariant,
        contentColor = if (filled) scheme.onPrimary else scheme.onSurface,
        radius = 26.dp,
        border = if (filled) 0.dp else 3.dp,
        lift = 4.dp,
        enabled = enabled,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
}

/** Cartão tátil. Em cartões coloridos a borda é a mesma cor, mais escura; no padrão é o contorno de pedra. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = if (containerColor == MaterialTheme.colorScheme.surfaceVariant) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        containerColor.darker(0.14f)
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    TactileCard(modifier.fillMaxWidth(), fill = containerColor, edge = borderColor) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** Kana em letra de caderno (Klee One), com altura de linha para não cortar. */
@Composable
fun KanaText(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = size,
        lineHeight = size * 1.25f,
        fontFamily = KanaFont,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}
