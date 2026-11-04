package dev.pedro.kanatoll.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Source
import dev.pedro.kanatoll.domain.isCorrect
import dev.pedro.kanatoll.widget.KanaWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Recebe a resposta digitada na notificação e a registra pelo mesmo caminho da sessão: ReviewRepository.answer. */
class QuizReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(QuizNotifier.KEY_ANSWER)?.toString() ?: return
        val cardId = intent.getLongExtra(QuizNotifier.EXTRA_CARD, -1L)
        if (cardId < 0 || text.isBlank()) return
        val posted = intent.getLongExtra(QuizNotifier.EXTRA_POSTED, 0L)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val container = (app as KanaApp).container
                val card = container.db.cards().get(cardId) ?: return@launch
                val correct = isCorrect(card, text)
                val elapsed = (System.currentTimeMillis() - posted).coerceAtLeast(0L)
                val result = container.reviews.answer(cardId, correct, text.trim(), elapsed, Source.NOTIFICATION)
                QuizNotifier.postResult(app, card, text, correct, result.gateOpened)
                KanaWidget().updateAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
