package dev.pedro.kanatoll

import android.app.Application
import dev.pedro.kanatoll.notif.QuizWorker
import dev.pedro.kanatoll.toll.TollLog

class KanaApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        TollLog.init(this)
        QuizWorker.ensureScheduled(this) // atualiza o widget e posta o quiz, se ligado
    }
}
