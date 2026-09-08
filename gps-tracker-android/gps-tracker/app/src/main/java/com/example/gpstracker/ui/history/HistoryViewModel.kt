package com.example.gpstracker.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstracker.data.local.AppDatabase
import com.example.gpstracker.data.local.entity.TrackPointEntity
import com.example.gpstracker.data.local.entity.TripEntity
import com.example.gpstracker.data.repository.TrackingRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrackingRepository(AppDatabase.getInstance(application))

    val trips: StateFlow<List<TripEntity>> = repository.observeAllTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadPointsForTrip(tripId: Long): List<TrackPointEntity> =
        repository.getPointsForTrip(tripId)
}
