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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.pedro.kanatoll.Destinations
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.HomeStats

/** Widget "Meta do dia": as respostas de hoje contra a meta, os dias seguidos e quantos cartões esperam. Tocar abre uma sessão. */
class KanaGoalWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as KanaApp).container.reviews
        val home = repo.homeStats()
        val streak = repo.stats().streak
        provideContent { GoalContent(home, streak) }
    }
}

class KanaGoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KanaGoalWidget()
}

@Composable
private fun GoalContent(s: HomeStats, streak: Int) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Bg)
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(actionStartActivity(Destinations.intent(context, Destinations.SESSION))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text("Hoje", style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.Bottom) {
            Text("${s.todayAnswers}", style = TextStyle(color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold))
            Text(
                if (s.goal > 0) "  de ${s.goal}" else "  respostas",
                modifier = GlanceModifier.padding(bottom = 7.dp),
                style = TextStyle(color = Muted, fontSize = 14.sp),
            )
        }
        if (s.goal > 0) {
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = (s.todayAnswers.toFloat() / s.goal).coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth(),
                color = Accent,
                backgroundColor = Track,
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        Text(
            if (streak == 1) "1 dia seguido" else "$streak dias seguidos",
            style = TextStyle(color = if (streak > 0) Accent else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        Text("${s.due} para revisar", style = TextStyle(color = Muted, fontSize = 12.sp))
    }
}
