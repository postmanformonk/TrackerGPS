package com.example.gpstracker.util

import kotlin.math.cos

data class BoundingBox(
    val southWestLat: Double,
    val southWestLng: Double,
    val northEastLat: Double,
    val northEastLng: Double
)

object GeoUtils {

    private const val KM_PER_DEGREE_LAT = 111.0

    /**
     * Строит прямоугольный BBox вокруг точки для заданного радиуса.
     * Формулы из п. 3.2 ТЗ: Δlat = radius/111, Δlng = radius/(111·cos(lat)).
     */
    fun calculateBBox(centerLat: Double, centerLng: Double, radiusKm: Double): BoundingBox {
        val deltaLat = radiusKm / KM_PER_DEGREE_LAT
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.01) // защита от деления около полюсов
        val deltaLng = radiusKm / (KM_PER_DEGREE_LAT * cosLat)

        return BoundingBox(
            southWestLat = centerLat - deltaLat,
            southWestLng = centerLng - deltaLng,
            northEastLat = centerLat + deltaLat,
            northEastLng = centerLng + deltaLng
        )
    }

    /** Радиус по умолчанию из ТЗ: диаметр 100 км = радиус 50 км. */
    const val DEFAULT_RADIUS_KM = 50.0
}
