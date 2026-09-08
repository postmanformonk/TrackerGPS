package com.example.gpstracker.offline

import android.content.Context
import com.example.gpstracker.util.BoundingBox
import com.example.gpstracker.util.MapConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class DownloadProgress {
    data class InProgress(val percentage: Int, val completedBytes: Long) : DownloadProgress()
    data class Complete(val regionId: Long, val totalBytes: Long) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

class OfflineMapManager(context: Context) {

    private val offlineManager = OfflineManager.getInstance(context)

    /**
     * Запускает скачивание bbox-региона и эмитит прогресс через Flow.
     * Это прямой аналог "OfflineDownloadWorker" из ТЗ, но выполняется через
     * нативный офлайн-движок MapLibre, а не ручным скачиванием файлов —
     * так рекомендует сам ТЗ для Варианта А (встроенный Bounding Box downloader).
     */
    fun downloadRegion(
        bbox: BoundingBox,
        minZoom: Double,
        maxZoom: Double,
        styleUrl: String = MapConfig.styleUrl()
    ): Flow<DownloadProgress> = callbackFlow {
        val bounds = LatLngBounds.from(
            bbox.northEastLat, bbox.northEastLng,
            bbox.southWestLat, bbox.southWestLng
        )
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl, bounds, minZoom, maxZoom,
            /* pixelRatio = */ 1.0f
        )
        val metadata = ByteArray(0)

        offlineManager.createOfflineRegion(
            definition, metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                trySend(DownloadProgress.Complete(offlineRegion.id, status.completedResourceSize))
                                close()
                            } else {
                                val percentage = if (status.requiredResourceCount > 0) {
                                    (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt()
                                } else 0
                                trySend(DownloadProgress.InProgress(percentage, status.completedResourceSize))
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            trySend(DownloadProgress.Error(error.message))
                            close()
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            trySend(DownloadProgress.Error("Превышен лимит тайлов: $limit"))
                            close()
                        }
                    })
                }

                override fun onError(error: String) {
                    trySend(DownloadProgress.Error(error))
                    close()
                }
            }
        )

        awaitClose { /* MapLibre продолжает докачку в фоне даже если экран закрыт —
                       остановка происходит явно через pauseRegion/deleteRegion. */ }
    }

    suspend fun listRegions(): List<OfflineRegion> = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                cont.resume(offlineRegions?.toList() ?: emptyList())
            }

            override fun onError(error: String) {
                cont.resumeWithException(IllegalStateException(error))
            }
        })
    }

    /** Находит нативный объект MapLibre-региона по ID, сохранённому в Room. */
    suspend fun findRegionById(maplibreRegionId: Long): OfflineRegion? =
        listRegions().firstOrNull { it.id == maplibreRegionId }

    /**
     * Полное каскадное удаление: сносит физические тайлы из mbgl-offline.db.
     * Бросает исключение, если регион не найден или SDK вернул ошибку —
     * вызывающая сторона (ViewModel) должна удалять запись Room только
     * после успешного завершения этого метода (Задача 1).
     */
    suspend fun deleteRegionById(maplibreRegionId: Long) {
        val region = findRegionById(maplibreRegionId)
            ?: throw IllegalStateException("Офлайн-регион с id=$maplibreRegionId не найден в MapLibre")
        deleteRegion(region)
    }

    suspend fun deleteRegion(region: OfflineRegion) = suspendCancellableCoroutine<Unit> { cont ->
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = cont.resume(Unit)
            override fun onError(error: String) = cont.resumeWithException(IllegalStateException(error))
        })
    }

    fun pauseRegion(region: OfflineRegion) {
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
    }
}
