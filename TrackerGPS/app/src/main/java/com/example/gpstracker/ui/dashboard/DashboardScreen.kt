package com.example.gpstracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpstracker.R
import com.example.gpstracker.ui.maps.MapLibreTrackMap
import com.example.gpstracker.util.NetworkUtils
import com.example.gpstracker.util.SpeedColorUtil
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.trackingState.collectAsState()
    var isOnline by remember { mutableStateOf(NetworkUtils.isOnline(context)) }

    // Периодическая проверка сети — MapLibre сам подставляет закэшированные
    // офлайн-тайлы, если они скачаны через OfflineManager, здесь только
    // индикация состояния для пользователя (п. 2.3 доп. ТЗ).
    LaunchedEffect(Unit) {
        while (true) {
            isOnline = NetworkUtils.isOnline(context)
            kotlinx.coroutines.delay(5000)
        }
    }

    val trackPoints = remember { mutableStateListOf<Pair<Double, Double>>() }
    LaunchedEffect(state.currentLat, state.currentLng) {
        val lat = state.currentLat
        val lng = state.currentLng
        if (lat != null && lng != null) trackPoints.add(lat to lng)
    }

    val currentLocation = remember(state.currentLat, state.currentLng, state.currentBearing, state.currentSpeedKmh) {
        val lat = state.currentLat
        val lng = state.currentLng
        if (lat == null || lng == null) null else android.location.Location("tracking_service").apply {
            latitude = lat
            longitude = lng
            bearing = state.currentBearing
            speed = (state.currentSpeedKmh / 3.6).toFloat()
        }
    }

    val segments = remember(trackPoints.size) {
        trackPoints.zipWithNext { a, b ->
            com.example.gpstracker.util.ColoredSegment(
                startLat = a.first, startLng = a.second,
                endLat = b.first, endLng = b.second,
                color = SpeedColorUtil.colorForSpeedKmh(state.currentSpeedKmh)
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MapLibreTrackMap(
                modifier = Modifier.fillMaxSize(),
                currentLocation = currentLocation,
                segments = segments
            )

            Column(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                if (!isOnline) {
                    StatusBanner(
                        text = "Офлайн-режим: используются загруженные карты",
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
                if (!state.hasGpsSignal) {
                    StatusBanner(
                        text = "GPS сигнал потерян…",
                        color = MaterialTheme.colorScheme.errorContainer
                    )
                }
            }
        }

        DashboardMetrics(state = state)

        Button(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            onClick = {
                if (state.isRecording) {
                    viewModel.stopTracking()
                    trackPoints.clear()
                } else {
                    viewModel.startTracking()
                }
            }
        ) {
            Text(
                text = stringResource(
                    if (state.isRecording) R.string.stop_trip else R.string.start_trip
                )
            )
        }
    }
}

@Composable
private fun StatusBanner(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, modifier = Modifier.padding(bottom = 4.dp)) {
        Text(text = text, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun DashboardMetrics(state: com.example.gpstracker.service.TrackingState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MetricItem(labelRes = R.string.speed_label, value = "${state.currentSpeedKmh.roundToInt()} км/ч")
        MetricItem(labelRes = R.string.distance_label, value = String.format(Locale.getDefault(), "%.2f км", state.distanceMeters / 1000.0))
        MetricItem(labelRes = R.string.duration_label, value = formatDuration(state.elapsedMillis))
    }
}

@Composable
private fun MetricItem(labelRes: Int, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}
