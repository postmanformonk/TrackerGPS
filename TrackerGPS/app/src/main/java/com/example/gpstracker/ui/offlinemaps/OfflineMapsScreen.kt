package com.example.gpstracker.ui.offlinemaps

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpstracker.data.local.entity.OfflineRegionEntity
import com.example.gpstracker.data.local.entity.OfflineRegionType
import com.example.gpstracker.offline.RegionCatalogEntry
import com.google.android.gms.location.LocationServices
import java.util.Locale

@Composable
fun OfflineMapsScreen(viewModel: OfflineMapsViewModel = viewModel()) {
    val context = LocalContext.current
    val regions by viewModel.downloadedRegions.collectAsState()
    val totalSize by viewModel.totalSizeBytes.collectAsState()
    val catalogEntries by viewModel.catalogEntries.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()

    var wifiOnly by remember { mutableStateOf(true) }
    var showCatalog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Оффлайн-карты", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Занято на диске: ${formatSize(totalSize)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            Spacer(modifier = Modifier.width(8.dp))
            Text("Скачивать только по Wi-Fi")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        LocationServices.getFusedLocationProviderClient(context).lastLocation
                            .addOnSuccessListener { location ->
                                if (location != null) {
                                    viewModel.downloadRadius(location.latitude, location.longitude, wifiOnly)
                                }
                            }
                    }
                }
            ) { Text("Скачать радиус 50 км") }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { showCatalog = true }
            ) { Text("Выбрать регион") }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Загруженные карты", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(regions, key = { it.id }) { region ->
                DownloadedRegionRow(region = region, onDelete = { viewModel.deleteRegion(region) })
                HorizontalDivider()
            }
        }
    }

    if (showCatalog) {
        RegionCatalogDialog(
            entries = catalogEntries,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onDismiss = { showCatalog = false },
            onSelect = { entry ->
                viewModel.downloadCatalogRegion(entry, wifiOnly)
                showCatalog = false
            }
        )
    }

    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDeleteError() },
            title = { Text("Ошибка удаления") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDeleteError() }) { Text("Понятно") }
            }
        )
    }
}

@Composable
private fun DownloadedRegionRow(region: OfflineRegionEntity, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(region.title) },
        supportingContent = {
            val typeLabel = if (region.type == OfflineRegionType.RADIUS_100KM) "Радиус 50 км" else "Регион"
            Text("$typeLabel · ${formatSize(region.sizeBytes)} · зум ${region.minZoom}-${region.maxZoom}")
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить")
            }
        }
    )
}

@Composable
private fun RegionCatalogDialog(
    entries: List<RegionCatalogEntry>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (RegionCatalogEntry) -> Unit
) {
    val filtered = remember(entries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val matched = if (q.isBlank()) entries else entries.filter {
            it.name.lowercase().contains(q) || it.country.lowercase().contains(q)
        }
        matched.groupBy { it.country }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор региона") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Поиск по названию") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Text(
                        "Каталог регионов пуст или не удалось прочитать region_catalog.json",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        filtered.forEach { (country, list) ->
                            item {
                                Text(
                                    country,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(list) { entry ->
                                ListItem(
                                    headlineContent = { Text(entry.name) },
                                    supportingContent = { Text("~${entry.approxSizeMb} МБ") },
                                    modifier = Modifier.clickable { onSelect(entry) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024) String.format(Locale.getDefault(), "%.1f МБ", mb)
    else String.format(Locale.getDefault(), "%.2f ГБ", mb / 1024)
}
