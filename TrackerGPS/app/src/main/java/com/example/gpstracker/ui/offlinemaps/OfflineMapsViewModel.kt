package com.example.gpstracker.ui.offlinemaps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.gpstracker.data.local.AppDatabase
import com.example.gpstracker.data.local.entity.OfflineRegionEntity
import com.example.gpstracker.data.local.entity.OfflineRegionType
import com.example.gpstracker.offline.OfflineDownloadWorker
import com.example.gpstracker.offline.OfflineMapManager
import com.example.gpstracker.offline.RegionCatalogEntry
import com.example.gpstracker.offline.RegionCatalogRepository
import com.example.gpstracker.util.BoundingBox
import com.example.gpstracker.util.GeoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OfflineMapsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val workManager = WorkManager.getInstance(application)
    private val offlineMapManager = OfflineMapManager(application)
    private val regionCatalogRepository = RegionCatalogRepository(application)

    val downloadedRegions: StateFlow<List<OfflineRegionEntity>> = db.offlineRegionDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSizeBytes: StateFlow<Long> = db.offlineRegionDao()
        .observeTotalSizeBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val catalogEntries: StateFlow<List<RegionCatalogEntry>> = regionCatalogRepository
        .observeCatalog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    /** Режим "Радиус вокруг геолокации" — п. 2.1 доп. ТЗ. */
    fun downloadRadius(centerLat: Double, centerLng: Double, wifiOnly: Boolean) {
        val bbox = GeoUtils.calculateBBox(centerLat, centerLng, GeoUtils.DEFAULT_RADIUS_KM)
        enqueueDownload(
            title = "Радиус ${GeoUtils.DEFAULT_RADIUS_KM.toInt()} км",
            type = OfflineRegionType.RADIUS_100KM,
            bbox = bbox,
            wifiOnly = wifiOnly
        )
    }

    /** Режим "Выбор региона вручную" — п. 2.1 доп. ТЗ. */
    fun downloadCatalogRegion(entry: RegionCatalogEntry, wifiOnly: Boolean) {
        enqueueDownload(
            title = entry.name,
            type = OfflineRegionType.CUSTOM_REGION,
            bbox = entry.toBoundingBox(),
            wifiOnly = wifiOnly
        )
    }

    private fun enqueueDownload(title: String, type: OfflineRegionType, bbox: BoundingBox, wifiOnly: Boolean) {
        val request = OfflineDownloadWorker.buildRequest(
            title = title, type = type, bbox = bbox, wifiOnly = wifiOnly
        )
        workManager.enqueue(request)
    }

    /**
     * Каскадное удаление (Задача 1): сначала физические тайлы через нативный
     * MapLibre OfflineManager, и ТОЛЬКО при успехе — запись из Room. Если
     * удаление в MapLibre падает, запись в Room остаётся, чтобы пользователь
     * не потерял информацию о том, что место на диске всё ещё занято.
     */
    fun deleteRegion(region: OfflineRegionEntity) {
        viewModelScope.launch {
            try {
                offlineMapManager.deleteRegionById(region.maplibreRegionId)
                db.offlineRegionDao().delete(region)
            } catch (e: Exception) {
                _deleteError.value = "Не удалось удалить карту «${region.title}»: ${e.message}"
            }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }
}
