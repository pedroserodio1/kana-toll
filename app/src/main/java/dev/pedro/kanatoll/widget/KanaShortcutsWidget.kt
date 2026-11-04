package dev.pedro.kanatoll.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.pedro.kanatoll.Destinations

/** Widget "Atalhos": quatro botões que abrem o app direto no estudo, no traçado, no ritmo ou nas estatísticas. Não tem números, então não precisa de atualização. */
class KanaShortcutsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Row(modifier = GlanceModifier.fillMaxSize().padding(2.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                ShortcutButton("Estudar", Destinations.SESSION, primary = true)
                ShortcutButton("Traçado", Destinations.TRACE)
                ShortcutButton("Ritmo", Destinations.RHYTHM)
                ShortcutButton("Stats", Destinations.STATS)
            }
        }
    }
}

class KanaShortcutsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KanaShortcutsWidget()
}

@Composable
private fun RowScope.ShortcutButton(label: String, destination: String, primary: Boolean = false) {
    val context = LocalContext.current
    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(3.dp)) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(if (primary) Accent else CardFill)
                .cornerRadius(18.dp)
                .clickable(actionStartActivity(Destinations.intent(context, destination))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = TextStyle(color = if (primary) OnAccent else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}
