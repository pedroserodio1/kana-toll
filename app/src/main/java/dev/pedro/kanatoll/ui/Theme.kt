package dev.pedro.kanatoll.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pedro.kanatoll.R

/** Letra de caderno escolar: kana, números grandes e títulos. */
val KanaFont = FontFamily(Font(R.font.klee_one_semibold, FontWeight.SemiBold))

/** Sans arredondada: o resto da interface. Só há Medium e Bold; os outros pesos caem no mais próximo. */
val UiFont = FontFamily(
    Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
    Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = KanaFont),
    displayMedium = base.displayMedium.copy(fontFamily = KanaFont),
    displaySmall = base.displaySmall.copy(fontFamily = KanaFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = KanaFont),
    headlineMedium = base.headlineMedium.copy(fontFamily = KanaFont),
    headlineSmall = base.headlineSmall.copy(fontFamily = KanaFont),
    titleLarge = base.titleLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.Medium),
    bodyMedium = base.bodyMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.Medium),
    bodySmall = base.bodySmall.copy(fontFamily = UiFont, fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold),
)

// Direção F (Enso + Matcha): pedra e musgo, amarelo como destaque, argila para o erro.
// Papéis: surfaceVariant = cartões; secondary = bloco amarelo; tertiary = erro (argila); primary = musgo/ação.
private val Light = lightColorScheme(
    primary = Color(0xFF3A7A2A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E8C7), onPrimaryContainer = Color(0xFF17370F),
    secondary = Color(0xFFF2C14E), onSecondary = Color(0xFF2B2100),
    secondaryContainer = Color(0xFFFBE7AE), onSecondaryContainer = Color(0xFF3B2D00),
    tertiary = Color(0xFFA8452D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4DDD2), onTertiaryContainer = Color(0xFF7A3422),
    error = Color(0xFFA8452D), onError = Color.White,
    errorContainer = Color(0xFFF4DDD2), onErrorContainer = Color(0xFF7A3422),
    background = Color(0xFFE9EDE6), onBackground = Color(0xFF26302A),
    surface = Color(0xFFE9EDE6), onSurface = Color(0xFF26302A),
    surfaceVariant = Color(0xFFF8FAF4), onSurfaceVariant = Color(0xFF4C5A4F),
    outline = Color(0xFF8B978B), outlineVariant = Color(0xFFC9D3C2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FCB7B), onPrimary = Color(0xFF10240B),
    primaryContainer = Color(0xFF28441D), onPrimaryContainer = Color(0xFFD2E8C7),
    secondary = Color(0xFFF2C14E), onSecondary = Color(0xFF2B2100),
    secondaryContainer = Color(0xFF5A4710), onSecondaryContainer = Color(0xFFFBE7AE),
    tertiary = Color(0xFFE8A28E), onTertiary = Color(0xFF4A1B0E),
    tertiaryContainer = Color(0xFF5A2A1D), onTertiaryContainer = Color(0xFFF4DDD2),
    error = Color(0xFFE8A28E), onError = Color(0xFF4A1B0E),
    errorContainer = Color(0xFF5A2A1D), onErrorContainer = Color(0xFFF4DDD2),
    background = Color(0xFF151A16), onBackground = Color(0xFFE3EADF),
    surface = Color(0xFF151A16), onSurface = Color(0xFFE3EADF),
    surfaceVariant = Color(0xFF212922), onSurfaceVariant = Color(0xFFB9C6BA),
    outline = Color(0xFF6F7C70), outlineVariant = Color(0xFF3A463B),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

/** Escuro ou claro conforme a escolha do usuário; no modo "sistema", conforme o aparelho. */
@Composable
fun useDarkTheme(mode: Int): Boolean = when (mode) {
    THEME_LIGHT -> false
    THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun KanaTheme(mode: Int = THEME_SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDarkTheme(mode)) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
