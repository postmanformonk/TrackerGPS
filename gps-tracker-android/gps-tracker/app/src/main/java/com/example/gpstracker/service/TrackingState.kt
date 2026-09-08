package com.example.gpstracker.service

data class TrackingState(
    val isRecording: Boolean = false,
    val tripId: Long? = null,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val currentSpeedKmh: Double = 0.0,
    val currentBearing: Float = 0f,
    val distanceMeters: Float = 0f,
    val elapsedMillis: Long = 0L,
    val maxSpeedKmh: Double = 0.0,
    val hasGpsSignal: Boolean = true
)
