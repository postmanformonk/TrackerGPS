package com.example.gpstracker.util

import android.location.Location
import kotlin.math.abs

/**
 * Фильтрует "шум" GPS перед тем, как точка попадает в трек:
 * - отбрасывает точки с низкой точностью (accuracy > MAX_ACCEPTABLE_ACCURACY);
 * - применяет простое экспоненциальное сглаживание координат (легковесная
 *   альтернатива полному фильтру Калмана — этого достаточно для сглаживания
 *   "зубьев" при пешей/автомобильной скорости и не требует лишних зависимостей).
 */
class LocationFilter(
    private val maxAcceptableAccuracy: Float = 15f,
    private val smoothingFactor: Double = 0.35
) {
    private var lastSmoothed: Location? = null

    fun accept(raw: Location): Location? {
        if (raw.accuracy > maxAcceptableAccuracy) return null

        val previous = lastSmoothed
        val smoothed = if (previous == null) {
            raw
        } else {
            val dtSeconds = (raw.time - previous.time) / 1000.0
            // Если точки пришли слишком редко (например, после разрыва сигнала),
            // сглаживание не применяем — доверяем новой точке целиком.
            if (dtSeconds > 30) {
                raw
            } else {
                Location(raw).apply {
                    latitude = lerp(previous.latitude, raw.latitude, smoothingFactor)
                    longitude = lerp(previous.longitude, raw.longitude, smoothingFactor)
                }
            }
        }

        // Отбрасываем "телепортацию" — нереалистичный скачок за короткое время
        previous?.let {
            val dtSeconds = (raw.time - it.time) / 1000.0
            if (dtSeconds > 0) {
                val jumpSpeed = it.distanceTo(raw) / dtSeconds
                if (jumpSpeed > 90.0 && abs(dtSeconds) < 3) return null // > 324 км/ч за <3с — явный сбой
            }
        }

        lastSmoothed = smoothed
        return smoothed
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    fun reset() {
        lastSmoothed = null
    }
}
