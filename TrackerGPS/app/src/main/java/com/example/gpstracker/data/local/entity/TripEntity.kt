package com.example.gpstracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val movingTimeMillis: Long = 0
)
