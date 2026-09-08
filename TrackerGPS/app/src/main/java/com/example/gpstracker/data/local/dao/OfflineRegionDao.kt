package com.example.gpstracker.data.local.dao

import androidx.room.*
import com.example.gpstracker.data.local.entity.OfflineRegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineRegionDao {

    @Insert
    suspend fun insert(region: OfflineRegionEntity): Long

    @Update
    suspend fun update(region: OfflineRegionEntity)

    @Delete
    suspend fun delete(region: OfflineRegionEntity)

    @Query("SELECT * FROM offline_regions ORDER BY downloadDate DESC")
    fun observeAll(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun getById(id: Long): OfflineRegionEntity?

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM offline_regions")
    fun observeTotalSizeBytes(): Flow<Long>
}
