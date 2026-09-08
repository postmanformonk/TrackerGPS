package com.example.gpstracker.offline

import com.example.gpstracker.util.BoundingBox
import kotlinx.serialization.Serializable

@Serializable
data class RegionCatalogEntry(
    val id: String,
    val country: String,
    val name: String,
    val southWestLat: Double,
    val southWestLng: Double,
    val northEastLat: Double,
    val northEastLng: Double,
    val approxSizeMb: Int
) {
    fun toBoundingBox() = BoundingBox(southWestLat, southWestLng, northEastLat, northEastLng)
}
