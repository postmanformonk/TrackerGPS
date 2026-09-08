package com.example.gpstracker.util

import android.content.Context

/**
 * Если Samsung One UI убивает TrackingService и Android перезапускает его
 * через START_STICKY (обычно с Intent == null), сервис в новом процессе
 * не помнит, какой trip_id он писал. Без этого механизма перезапуск создавал
 * бы новую поездку и рвал маршрут на куски. Здесь хранится минимум,
 * достаточный для восстановления: id текущей активной поездки и время старта.
 */
object TrackingPrefs {
    private const val PREFS_NAME = "tracking_prefs"
    private const val KEY_TRIP_ID = "active_trip_id"
    private const val KEY_START_TIME = "active_trip_start_time"
    private const val NO_VALUE = -1L

    data class ActiveTrip(val tripId: Long, val startTimeMillis: Long)

    fun saveActiveTrip(context: Context, tripId: Long, startTimeMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_TRIP_ID, tripId)
            .putLong(KEY_START_TIME, startTimeMillis)
            .apply()
    }

    fun getActiveTrip(context: Context): ActiveTrip? {
        val p = prefs(context)
        val tripId = p.getLong(KEY_TRIP_ID, NO_VALUE)
        val startTime = p.getLong(KEY_START_TIME, NO_VALUE)
        return if (tripId != NO_VALUE && startTime != NO_VALUE) ActiveTrip(tripId, startTime) else null
    }

    fun clearActiveTrip(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
