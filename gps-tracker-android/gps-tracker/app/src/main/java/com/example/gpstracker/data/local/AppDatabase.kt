package com.example.gpstracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gpstracker.data.local.dao.OfflineRegionDao
import com.example.gpstracker.data.local.dao.TrackPointDao
import com.example.gpstracker.data.local.dao.TripDao
import com.example.gpstracker.data.local.entity.OfflineRegionEntity
import com.example.gpstracker.data.local.entity.TrackPointEntity
import com.example.gpstracker.data.local.entity.TripEntity

@Database(
    entities = [TripEntity::class, TrackPointEntity::class, OfflineRegionEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun offlineRegionDao(): OfflineRegionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gps_tracker.db"
                )
                    // Задача 5 (аудит): реальная миграция вместо разрушительной —
                    // апгрейд версии БД НИКОГДА не стирает trips/track_points.
                    .addMigrations(MIGRATION_1_2)
                    .apply {
                        // Разрушительный fallback разрешён ТОЛЬКО при откате версии
                        // схемы назад (downgrade) и ТОЛЬКО в DEBUG-билде — это может
                        // произойти лишь при установке более старой debug-сборки поверх
                        // новой в разработке. В релизе versionCode всегда растёт, это
                        // ветка недостижима, но убирает риск краша при локальных экспериментах.
                        if (com.example.gpstracker.BuildConfig.DEBUG) {
                            fallbackToDestructiveMigrationOnDowngrade()
                        }
                    }
                    .build().also { INSTANCE = it }
            }
    }
}
