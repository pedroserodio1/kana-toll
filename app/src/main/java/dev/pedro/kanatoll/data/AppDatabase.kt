package dev.pedro.kanatoll.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Card::class, ReviewLog::class, Confusion::class, GateState::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cards(): CardDao
    abstract fun reviewLogs(): ReviewLogDao
    abstract fun confusions(): ConfusionDao
    abstract fun gates(): GateDao

    companion object {
        // Sem fallbackToDestructiveMigration: o histórico é o ativo do app.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kana.db").build()
    }
}
