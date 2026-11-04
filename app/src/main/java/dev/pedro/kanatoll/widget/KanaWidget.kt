package dev.pedro.kanatoll.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.MainActivity
import dev.pedro.kanatoll.data.HomeStats

/** Widget: quantos cartões estão para revisar e o progresso do portão. Tocar abre o app. */
class KanaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = (context.applicationContext as KanaApp).container.reviews.homeStats()
        provideContent { WidgetContent(stats) }
    }
}

class KanaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KanaWidget()
}

/** Atualiza os widgets que mostram números (o de atalhos é estático). Chamado depois de cada resposta e a cada ~15 min. */
suspend fun updateAllWidgets(context: Context) {
    KanaWidget().updateAll(context)
    KanaGoalWidget().updateAll(context)
}

// Mesmas cores do tema do app (direção F), claro e escuro.
internal val Bg = ColorProvider(day = Color(0xFFE9EDE6), night = Color(0xFF151A16))
internal val Ink = ColorProvider(day = Color(0xFF26302A), night = Color(0xFFE3EADF))
internal val Muted = ColorProvider(day = Color(0xFF4C5A4F), night = Color(0xFFB9C6BA))
internal val Accent = ColorProvider(day = Color(0xFF3A7A2A), night = Color(0xFF8FCB7B))
internal val OnAccent = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF10240B))
internal val Track = ColorProvider(day = Color(0xFFC9D3C2), night = Color(0xFF3A463B))
internal val CardFill = ColorProvider(day = Color(0xFFF8FAF4), night = Color(0xFF212922))

@Composable
private fun WidgetContent(s: HomeStats) {
    // Mostra o portão em andamento: hiragana e, depois que abre, katakana.
    val gate = s.katakana ?: s.hiragana
    val name = if (s.katakana != null) "katakana" else "hiragana"
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Bg)
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Bottom) {
            Text("${s.due}", style = TextStyle(color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                "para revisar",
                modifier = GlanceModifier.padding(bottom = 8.dp),
                style = TextStyle(color = Muted, fontSize = 14.sp),
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        Text("Portão do $name  ${gate.passed}/${gate.total}", style = TextStyle(color = Muted, fontSize = 12.sp))
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (gate.total == 0) 0f else gate.passed.toFloat() / gate.total,
            modifier = GlanceModifier.fillMaxWidth(),
            color = Accent,
            backgroundColor = Track,
        )
    }
}
