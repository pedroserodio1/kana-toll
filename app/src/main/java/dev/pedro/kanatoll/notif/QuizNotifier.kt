package dev.pedro.kanatoll.notif

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.MainActivity
import dev.pedro.kanatoll.R
import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Script
import dev.pedro.kanatoll.domain.KanaTable

/**
 * Quiz na notificação: uma pergunta kana → rōmaji que você responde digitando na própria
 * notificação (RemoteInput), sem abrir o app. Uma notificação só (mesmo id): a resposta
 * substitui a pergunta.
 */
object QuizNotifier {
    private const val CHANNEL = "quiz"
    private const val NOTIF_ID = 1001
    private const val SUMMARY_CHANNEL = "summary"
    private const val SUMMARY_ID = 1002
    const val ACTION_ANSWER = "dev.pedro.kanatoll.QUIZ_ANSWER"
    const val EXTRA_CARD = "card_id"
    const val EXTRA_POSTED = "posted_at"
    const val KEY_ANSWER = "answer"

    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Quiz de kana")
                .setDescription("Uma pergunta por vez, respondida na própria notificação.")
                .build(),
        )
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    @SuppressLint("MissingPermission") // checada em canNotify
    fun postQuestion(context: Context, card: Card) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val reply = RemoteInput.Builder(KEY_ANSWER).setLabel("rōmaji").build()
        val intent = Intent(context, QuizReceiver::class.java)
            .setAction(ACTION_ANSWER)
            .putExtra(EXTRA_CARD, card.id)
            .putExtra(EXTRA_POSTED, System.currentTimeMillis())
        // Mutável de propósito: o sistema preenche a resposta digitada dentro deste intent.
        val pending = PendingIntent.getBroadcast(
            context, card.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_notification, "Responder", pending)
            .addRemoteInput(reply)
            .setAllowGeneratedReplies(false)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Como se lê  ${card.kana} ?")
            .setContentText("Responda em rōmaji, sem abrir o app.")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(action)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    /** Substitui a pergunta pelo resultado (isso também tira o "carregando" da caixa de resposta). */
    @SuppressLint("MissingPermission")
    fun postResult(context: Context, card: Card, response: String, correct: Boolean, gateOpened: Script?) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val summary = buildString {
            append("${card.kana} = ${KanaTable.find(card.script, card.kana).romaji}")
            if (!correct) append(" · você respondeu ${response.trim()}")
        }
        val detail = if (gateOpened == Script.HIRAGANA) "$summary\nPortão do hiragana aberto! Katakana liberado." else summary
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (correct) "Certo!" else "Não foi dessa vez")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(20_000)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    /** Resumo do dia: uma notificação discreta (sem som) com o que foi respondido e a situação da meta. */
    @SuppressLint("MissingPermission")
    fun postSummary(context: Context, text: String) {
        if (!canNotify(context)) return
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(SUMMARY_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Resumo do dia")
                .setDescription("Quanto você estudou hoje e a meta diária.")
                .build(),
        )
        val notification = NotificationCompat.Builder(context, SUMMARY_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Resumo do dia")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, notification)
    }

    /** Botão "Enviar notificação de teste": ignora a janela do dia e o limite de 1 por hora. */
    suspend fun postTest(context: Context): String {
        if (!canNotify(context)) return "Sem permissão de notificações."
        val reviews = (context.applicationContext as KanaApp).container.reviews
        val card = reviews.nextQuizCard() ?: reviews.anyQuizCard()
            ?: return "Estude uma sessão primeiro: ainda não há kana apresentados."
        postQuestion(context, card)
        return "Notificação enviada."
    }
}
