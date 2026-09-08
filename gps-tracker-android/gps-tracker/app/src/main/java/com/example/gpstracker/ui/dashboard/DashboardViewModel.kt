package com.example.gpstracker.ui.dashboard

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstracker.service.TrackingService
import com.example.gpstracker.service.TrackingState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    val trackingState: StateFlow<TrackingState> = TrackingService.state

    fun startTracking() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        ctx.startForegroundService(intent)
    }

    fun stopTracking() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        ctx.startService(intent)
    }
}
