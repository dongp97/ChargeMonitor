package com.example.chargemonitor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.chargemonitor.data.entity.Phase
import com.example.chargemonitor.data.entity.Sample
import com.example.chargemonitor.data.entity.Session

@Database(
    entities = [Session::class, Sample::class, Phase::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun sampleDao(): SampleDao
    abstract fun phaseDao(): PhaseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `phase` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`start_time` INTEGER NOT NULL, " +
                        "`end_time` INTEGER NOT NULL, " +
                        "`duration_s` INTEGER NOT NULL, " +
                        "`total_mah` REAL NOT NULL, " +
                        "`avg_current_ma` REAL NOT NULL, " +
                        "`avg_power_w` REAL NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "charge_monitor.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
