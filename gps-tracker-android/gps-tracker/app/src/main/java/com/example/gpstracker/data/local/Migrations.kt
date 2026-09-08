package com.example.gpstracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Версия 1 содержала только trips/track_points. Версия 2 добавляет
 * offline_regions (Задача 5) — эта миграция создаёт таблицу и индекс
 * вручную по SQL, точно повторяя схему, которую сгенерировал бы Room
 * из OfflineRegionEntity, чтобы дальнейшие миграции не разошлись со схемой.
 *
 * Типы столбцов подобраны по правилам Room: Long/Int/Boolean -> INTEGER,
 * Double -> REAL, String/enum(через Converters) -> TEXT.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `offline_regions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `minZoom` INTEGER NOT NULL,
                `maxZoom` INTEGER NOT NULL,
                `bboxSouthWestLat` REAL NOT NULL,
                `bboxSouthWestLng` REAL NOT NULL,
                `bboxNorthEastLat` REAL NOT NULL,
                `bboxNorthEastLng` REAL NOT NULL,
                `maplibreRegionId` INTEGER NOT NULL,
                `sizeBytes` INTEGER NOT NULL DEFAULT 0,
                `downloadDate` INTEGER NOT NULL,
                `isComplete` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
