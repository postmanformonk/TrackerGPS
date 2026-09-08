package com.example.gpstracker.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import java.io.IOException

private const val CATALOG_ASSET_PATH = "region_catalog.json"

/**
 * Заменяет прежнюю статическую заглушку в RegionCatalog.kt. Каталог читается
 * из assets/region_catalog.json — при необходимости обновления списка
 * регионов достаточно заменить файл ассета, без изменения кода. Если позже
 * понадобится тянуть индекс с сервера (Geofabrik/Protomaps, см. README) —
 * меняется только тело loadCatalog(), интерфейс (Flow<List<RegionCatalogEntry>>)
 * остаётся тем же.
 */
class RegionCatalogRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeCatalog(): Flow<List<RegionCatalogEntry>> = flow {
        emit(loadCatalog())
    }.flowOn(Dispatchers.IO)

    private fun loadCatalog(): List<RegionCatalogEntry> {
        return try {
            val text = context.assets.open(CATALOG_ASSET_PATH).bufferedReader().use { it.readText() }
            json.decodeFromString<List<RegionCatalogEntry>>(text)
        } catch (e: IOException) {
            emptyList()
        } catch (e: Exception) {
            // Ошибка парсинга JSON — не валим экран, просто отдаём пустой каталог.
            emptyList()
        }
    }

    fun search(entries: List<RegionCatalogEntry>, query: String): List<RegionCatalogEntry> {
        if (query.isBlank()) return entries
        val q = query.trim().lowercase()
        return entries.filter { it.name.lowercase().contains(q) || it.country.lowercase().contains(q) }
    }

    fun groupedByCountry(entries: List<RegionCatalogEntry>, query: String = ""): Map<String, List<RegionCatalogEntry>> =
        search(entries, query).groupBy { it.country }
}
