package com.example.gpstracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OfflineRegionType { RADIUS_100KM, CUSTOM_REGION }

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: OfflineRegionType,
    val minZoom: Int,
    val maxZoom: Int,
    val bboxSouthWestLat: Double,
    val bboxSouthWestLng: Double,
    val bboxNorthEastLat: Double,
    val bboxNorthEastLng: Double,
    // ID региона внутри MapLibre OfflineManager — по нему находим/удаляем/обновляем
    // реальные скачанные тайлы (сами тайлы физически лежат в БД MapLibre, не Room).
    val maplibreRegionId: Long,
    val sizeBytes: Long = 0,
    val downloadDate: Long,
    val isComplete: Boolean = false
)
