package com.example.gpstracker.util

import com.example.gpstracker.BuildConfig

/**
 * Единая точка конфигурации стиля карты (Задача 2). Раньше стиль был
 * захардкожен как демо-URL MapLibre — теперь используется боевой векторный
 * провайдер (MapTiler), ключ подтягивается из BuildConfig (см. gradle.properties).
 *
 * Если понадобится Stadia Maps вместо MapTiler — достаточно поменять
 * STYLE_URL здесь, весь остальной код (OfflineMapManager, MapLibreTrackMap)
 * ссылается только на MapConfig.styleUrl() и ничего не знает про конкретного
 * провайдера.
 */
object MapConfig {

    private const val MAPTILER_STYLE_URL =
        "https://api.maptiler.com/maps/streets-v2/style.json?key=%s"

    // Fallback на публичный демо-стиль, если ключ не задан — чтобы билд без
    // ключа хотя бы запускался при локальной отладке (не для продакшена!).
    private const val FALLBACK_DEMO_STYLE_URL = "https://demotiles.maplibre.org/style.json"

    fun styleUrl(): String {
        val key = BuildConfig.MAPTILER_API_KEY
        return if (key.isNotBlank() && key != "YOUR_MAPTILER_API_KEY_HERE") {
            MAPTILER_STYLE_URL.format(key)
        } else {
            FALLBACK_DEMO_STYLE_URL
        }
    }

    fun isUsingProductionProvider(): Boolean = styleUrl() != FALLBACK_DEMO_STYLE_URL
}
