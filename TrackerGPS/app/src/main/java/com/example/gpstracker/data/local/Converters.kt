package com.example.gpstracker.data.local

import androidx.room.TypeConverter
import com.example.gpstracker.data.local.entity.OfflineRegionType

class Converters {
    @TypeConverter
    fun fromRegionType(type: OfflineRegionType): String = type.name

    @TypeConverter
    fun toRegionType(value: String): OfflineRegionType = OfflineRegionType.valueOf(value)
}
