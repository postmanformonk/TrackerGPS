package com.example.gpstracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpstracker.data.local.entity.TripEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onTripClick: (Long) -> Unit
) {
    val trips by viewModel.trips.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(trips, key = { it.id }) { trip ->
            TripRow(trip = trip, onClick = { onTripClick(trip.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun TripRow(trip: TripEntity, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val durationMin = ((trip.movingTimeMillis) / 1000 / 60)

    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        headlineContent = { Text(dateFormat.format(Date(trip.startTime))) },
        supportingContent = {
            Text(
                text = String.format(
                    Locale.getDefault(),
                    "%.2f км · %d мин",
                    trip.totalDistanceMeters / 1000.0,
                    durationMin
                )
            )
        }
    )
}
