package com.example.gpstracker.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.gpstracker.R
import com.example.gpstracker.data.local.AppDatabase
import com.example.gpstracker.data.local.entity.OfflineRegionEntity
import com.example.gpstracker.data.local.entity.OfflineRegionType
import com.example.gpstracker.util.BoundingBox
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

/**
 * Реализация "OfflineDownloadWorker" из п. 5.3 доп. ТЗ. Докачка при обрыве
 * связи обеспечивается самим MapLibre OfflineManager (он умеет докачивать
 * недостающие тайлы повторным вызовом), а WorkManager отвечает за
 * планирование задачи с учётом сети и вывод прогресса в уведомления.
 */
class OfflineDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "offline_download_channel"
        const val NOTIFICATION_ID = 2001

        const val KEY_TITLE = "title"
        const val KEY_TYPE = "type"
        const val KEY_SW_LAT = "sw_lat"
        const val KEY_SW_LNG = "sw_lng"
        const val KEY_NE_LAT = "ne_lat"
        const val KEY_NE_LNG = "ne_lng"
        const val KEY_MIN_ZOOM = "min_zoom"
        const val KEY_MAX_ZOOM = "max_zoom"

        fun buildRequest(
            title: String,
            type: OfflineRegionType,
            bbox: BoundingBox,
            minZoom: Double = 1.0,
            maxZoom: Double = 16.0,
            wifiOnly: Boolean = true
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val data = workDataOf(
                KEY_TITLE to title,
                KEY_TYPE to type.name,
                KEY_SW_LAT to bbox.southWestLat,
                KEY_SW_LNG to bbox.southWestLng,
                KEY_NE_LAT to bbox.northEastLat,
                KEY_NE_LNG to bbox.northEastLng,
                KEY_MIN_ZOOM to minZoom,
                KEY_MAX_ZOOM to maxZoom
            )

            return OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0)

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Регион"
        val type = OfflineRegionType.valueOf(inputData.getString(KEY_TYPE) ?: OfflineRegionType.CUSTOM_REGION.name)
        val bbox = BoundingBox(
            southWestLat = inputData.getDouble(KEY_SW_LAT, 0.0),
            southWestLng = inputData.getDouble(KEY_SW_LNG, 0.0),
            northEastLat = inputData.getDouble(KEY_NE_LAT, 0.0),
            northEastLng = inputData.getDouble(KEY_NE_LNG, 0.0)
        )
        val minZoom = inputData.getDouble(KEY_MIN_ZOOM, 1.0)
        val maxZoom = inputData.getDouble(KEY_MAX_ZOOM, 16.0)

        setForeground(createForegroundInfo(0))

        val manager = OfflineMapManager(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)

        return try {
            var finalRegionId: Long? = null
            var finalSize = 0L

            manager.downloadRegion(bbox, minZoom, maxZoom).collect { progress ->
                when (progress) {
                    is DownloadProgress.InProgress -> {
                        setForeground(createForegroundInfo(progress.percentage))
                        setProgress(workDataOf("percentage" to progress.percentage))
                    }
                    is DownloadProgress.Complete -> {
                        finalRegionId = progress.regionId
                        finalSize = progress.totalBytes
                    }
                    is DownloadProgress.Error -> {
                        throw IllegalStateException(progress.message)
                    }
                }
            }

            db.offlineRegionDao().insert(
                OfflineRegionEntity(
                    title = title,
                    type = type,
                    minZoom = minZoom.toInt(),
                    maxZoom = maxZoom.toInt(),
                    bboxSouthWestLat = bbox.southWestLat,
                    bboxSouthWestLng = bbox.southWestLng,
                    bboxNorthEastLat = bbox.northEastLat,
                    bboxNorthEastLng = bbox.northEastLng,
                    maplibreRegionId = finalRegionId ?: -1L,
                    sizeBytes = finalSize,
                    downloadDate = System.currentTimeMillis(),
                    isComplete = true
                )
            )

            Result.success()
        } catch (e: Exception) {
            // Недостаток места на диске (StorageException из ТЗ) и сетевые обрывы —
            // WorkManager сам повторит задачу согласно Constraints при retry().
            Result.retry()
        }
    }

    private fun createForegroundInfo(percentage: Int): ForegroundInfo {
        createChannelIfNeeded()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Загрузка офлайн-карты")
            .setContentText("$percentage%")
            .setSmallIcon(R.drawable.ic_tracking)
            .setProgress(100, percentage, false)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Загрузка офлайн-карт", NotificationManager.IMPORTANCE_LOW
            )
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
