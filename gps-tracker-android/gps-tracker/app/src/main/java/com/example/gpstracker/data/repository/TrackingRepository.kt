package com.example.gpstracker.data.repository

import androidx.room.withTransaction
import com.example.gpstracker.data.local.AppDatabase
import com.example.gpstracker.data.local.entity.TrackPointEntity
import com.example.gpstracker.data.local.entity.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TrackingRepository(private val db: AppDatabase) {

    suspend fun startNewTrip(startTime: Long): Long =
        db.tripDao().insert(TripEntity(startTime = startTime))

    suspend fun finishTrip(trip: TripEntity) = db.tripDao().update(trip)

    /**
     * Запись точки "на лету" (Задача 1 аудита): выполняется в явной транзакции
     * Room и оборачивается в NonCancellable — если serviceScope сервиса
     * отменяется в момент убийства процесса системой, эта конкретная запись
     * всё равно долетит до диска, а не потеряется вместе с корутиной.
     */
    suspend fun savePoint(point: TrackPointEntity) = withContext(NonCancellable + Dispatchers.IO) {
        db.withTransaction {
            db.trackPointDao().insert(point)
        }
    }

    fun observeAllTrips(): Flow<List<TripEntity>> = db.tripDao().observeAllTrips()

    suspend fun getTrip(tripId: Long): TripEntity? = db.tripDao().getTripById(tripId)

    suspend fun getPointsForTrip(tripId: Long): List<TrackPointEntity> =
        db.trackPointDao().getPointsForTrip(tripId)

    fun observePointsForTrip(tripId: Long): Flow<List<TrackPointEntity>> =
        db.trackPointDao().observePointsForTrip(tripId)
}
