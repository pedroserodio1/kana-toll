package dev.pedro.kanatoll.notif

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pedro.kanatoll.AppContainer
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.domain.summaryText
import dev.pedro.kanatoll.fsrs.StudyDay
import dev.pedro.kanatoll.widget.KanaWidget
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Roda a cada ~15 min (o mínimo do WorkManager). Atualiza o widget e, se o quiz estiver ligado,
 * dentro da janela do dia, passada 1 hora do último e com algum cartão kana → rōmaji devido,
 * posta a pergunta.
 */
class QuizWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KanaApp).container
        KanaWidget().updateAll(applicationContext) // o número de devidos muda com o passar do tempo
        postSummaryIfDue(container)

        val settings = container.settings
        if (!settings.quizEnabled || !QuizNotifier.canNotify(applicationContext)) return Result.success()
        val hour = LocalTime.now(container.clock).hour
        if (hour < settings.quizStartHour || hour >= settings.quizEndHour) return Result.success()
        val now = container.clock.millis()
        if (now - settings.lastQuizAt < ONE_HOUR_MS) return Result.success()
        val card = container.reviews.nextQuizCard() ?: return Result.success()

        QuizNotifier.postQuestion(applicationContext, card)
        settings.lastQuizAt = now
        return Result.success()
    }

    /** Depois das [SUMMARY_HOUR]h, no máximo um resumo por dia de estudo, se estiver ligado. */
    private suspend fun postSummaryIfDue(container: AppContainer) {
        val settings = container.settings
        if (!settings.summaryEnabled || !QuizNotifier.canNotify(applicationContext)) return
        if (LocalTime.now(container.clock).hour < SUMMARY_HOUR) return
        val today = StudyDay.of(container.clock.millis(), container.clock.zone).toString()
        if (settings.lastSummaryDay == today) return
        val answers = container.reviews.todayAnswers()
        QuizNotifier.postSummary(applicationContext, summaryText(answers.total, answers.correct, settings.dailyGoal))
        settings.lastSummaryDay = today
    }

    companion object {
        private const val SUMMARY_HOUR = 21
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private const val UNIQUE_NAME = "quiz"

        /** Idempotente: pode ser chamado a cada abertura do app. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<QuizWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
