package dev.pedro.kanatoll

import android.content.Context
import dev.pedro.kanatoll.backup.BackupManager
import dev.pedro.kanatoll.data.AppDatabase
import dev.pedro.kanatoll.data.ReviewRepository
import dev.pedro.kanatoll.data.Settings
import dev.pedro.kanatoll.ui.Speaker
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** DI manual. Clock injetável para simular dias nos testes (portão de 7 dias, virada às 04:00). */
class AppContainer(context: Context, val clock: Clock = Clock.systemDefaultZone()) {
    /** Para gravações que não podem morrer junto com uma tela (um ViewModel que sai no meio da escrita). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy { AppDatabase.build(context) }
    val settings: Settings by lazy { Settings(context.applicationContext) }
    val reviews: ReviewRepository by lazy { ReviewRepository(db, settings, clock) }
    val backup: BackupManager by lazy { BackupManager(db, clock) }
    val speaker: Speaker by lazy { Speaker(context.applicationContext) }
}
