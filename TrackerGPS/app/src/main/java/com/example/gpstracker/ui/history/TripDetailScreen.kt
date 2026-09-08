package com.example.gpstracker.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpstracker.R
import com.example.gpstracker.data.local.entity.TrackPointEntity
import com.example.gpstracker.ui.maps.MapLibreTrackMap
import com.example.gpstracker.util.SpeedColorUtil
import java.util.Locale

@Composable
fun TripDetailScreen(
    tripId: Long,
    viewModel: HistoryViewModel = viewModel()
) {
    var points by remember { mutableStateOf<List<TrackPointEntity>>(emptyList()) }

    LaunchedEffect(tripId) {
        points = viewModel.loadPointsForTrip(tripId)
    }

    if (points.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val segments = remember(points) { SpeedColorUtil.buildColoredSegments(points) }
    val avgSpeedKmh = points.map { it.speedMps * 3.6 }.average()
    val maxSpeedKmh = points.maxOf { it.speedMps * 3.6 }
    val totalDistanceKm = remember(points) {
        points.zipWithNext { a, b ->
            android.location.Location("").apply {
                latitude = a.latitude; longitude = a.longitude
            }.distanceTo(android.location.Location("").apply {
                latitude = b.latitude; longitude = b.longitude
            })
        }.sum() / 1000.0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MapLibreTrackMap(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            currentLocation = null,
            segments = segments
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatColumn(stringResource(R.string.distance_label), String.format(Locale.getDefault(), "%.2f км", totalDistanceKm))
            StatColumn(stringResource(R.string.avg_speed_label), String.format(Locale.getDefault(), "%.0f км/ч", avgSpeedKmh))
            StatColumn(stringResource(R.string.max_speed_label), String.format(Locale.getDefault(), "%.0f км/ч", maxSpeedKmh))
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}
