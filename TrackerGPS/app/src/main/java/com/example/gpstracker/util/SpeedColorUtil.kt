package com.example.gpstracker.util

import androidx.compose.ui.graphics.Color
import com.example.gpstracker.data.local.entity.TrackPointEntity

/**
 * Границы диапазонов скорости настраиваются здесь — единая точка конфигурации
 * для всего приложения (согласно п. 2.3 ТЗ).
 */
object SpeedThresholds {
    const val LOW_KMH = 20.0
    const val MEDIUM_KMH = 60.0
}

data class ColoredSegment(
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val color: Color
)

object SpeedColorUtil {

    fun colorForSpeedKmh(speedKmh: Double): Color = when {
        speedKmh < SpeedThresholds.LOW_KMH -> Color(0xFF2E7D32)   // зелёный
        speedKmh < SpeedThresholds.MEDIUM_KMH -> Color(0xFFF9A825) // жёлтый
        else -> Color(0xFFC62828)                                  // красный
    }

    /**
     * Разбивает список точек трека на цветные сегменты между соседними точками,
     * используя среднюю скорость пары (см. п. 2.3 ТЗ: V = (Vi + Vi+1) / 2).
     */
    fun buildColoredSegments(points: List<TrackPointEntity>): List<ColoredSegment> {
        if (points.size < 2) return emptyList()

        return points.zipWithNext { p1, p2 ->
            val v1Kmh = p1.speedMps * 3.6
            val v2Kmh = p2.speedMps * 3.6
            val avgKmh = (v1Kmh + v2Kmh) / 2.0
            ColoredSegment(
                startLat = p1.latitude,
                startLng = p1.longitude,
                endLat = p2.latitude,
                endLng = p2.longitude,
                color = colorForSpeedKmh(avgKmh)
            )
        }
    }
}
