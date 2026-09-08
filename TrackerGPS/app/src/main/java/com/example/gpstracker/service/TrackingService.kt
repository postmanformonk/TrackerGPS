package com.example.gpstracker.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gpstracker.MainActivity
import com.example.gpstracker.R
import com.example.gpstracker.data.local.AppDatabase
import com.example.gpstracker.data.local.entity.TrackPointEntity
import com.example.gpstracker.data.repository.TrackingRepository
import com.example.gpstracker.util.LocationFilter
import com.example.gpstracker.util.TrackingPrefs
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.max

class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.gpstracker.action.START"
        const val ACTION_STOP = "com.example.gpstracker.action.STOP"

        private val _state = MutableStateFlow(TrackingState())
        val state: StateFlow<TrackingState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var repository: TrackingRepository
    private val locationFilter = LocationFilter()

    private var currentTripId: Long? = null
    private var lastAcceptedLocation: Location? = null
    private var startTimeMillis: Long = 0L
    private var lastGoodSignalTime: Long = 0L
    private var gpsWatchdogJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val raw = result.lastLocation ?: return
            handleNewLocation(raw)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        repository = TrackingRepository(AppDatabase.getInstance(applicationContext))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_START -> startNewTrip()
            null -> {
                // Задача 1 (аудит): интент отсутствует — это признак того, что
                // Android перезапустил сервис сам после того, как ОС (в первую
                // очередь Samsung One UI со своим Device Care) убила процесс.
                // В этом сценарии НЕЛЬЗЯ создавать новую поездку — нужно
                // продолжить писать точки в тот же trip_id.
                resumeAfterSystemRestart()
            }
            else -> Unit
        }
        return START_STICKY
    }

    /** Явный старт новой поездки — вызывается только из UI через ACTION_START. */
    private fun startNewTrip() {
        startTimeMillis = System.currentTimeMillis()
        resetTrackingState()

        enterForeground()

        serviceScope.launch {
            val tripId = repository.startNewTrip(startTimeMillis)
            currentTripId = tripId
            // Сохраняем сразу после создания записи в Room — если процесс убьют
            // в следующую же секунду, при перезапуске будет что восстанавливать.
            TrackingPrefs.saveActiveTrip(applicationContext, tripId, startTimeMillis)
            _state.value = TrackingState(isRecording = true, tripId = tripId)
        }

        startLocationUpdates()
        startGpsWatchdog()
    }

    /**
     * Восстановление после START_STICKY (Задача 1 аудита). Продолжает писать
     * точки в УЖЕ существующий trip_id из SharedPreferences вместо создания
     * рваного нового куска поездки.
     */
    private fun resumeAfterSystemRestart() {
        val active = TrackingPrefs.getActiveTrip(applicationContext)
        if (active == null) {
            // Восстанавливать нечего — сервис остался в памяти системы уже
            // после штатной остановки записи. Просто завершаемся.
            stopSelf()
            return
        }

        currentTripId = active.tripId
        startTimeMillis = active.startTimeMillis
        resetTrackingState(preserveTripId = true)

        enterForeground()
        _state.value = TrackingState(
            isRecording = true,
            tripId = active.tripId,
            elapsedMillis = System.currentTimeMillis() - active.startTimeMillis
        )

        startLocationUpdates()
        startGpsWatchdog()
    }

    private fun resetTrackingState(preserveTripId: Boolean = false) {
        lastGoodSignalTime = System.currentTimeMillis()
        locationFilter.reset()
        lastAcceptedLocation = null
        if (!preserveTripId) currentTripId = null
    }

    private fun enterForeground() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(0f, 0.0),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(3f) // соответствует smallestDisplacement 2-5м из ТЗ
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            // Разрешение не выдано (например, отозвано пока сервис был убит) —
            // работать не можем, останавливаемся штатно.
            stopTracking()
        }
    }

    /** Отдельный таймер для контроля потери сигнала GPS (используется в UI как индикатор). */
    private fun startGpsWatchdog() {
        gpsWatchdogJob?.cancel()
        gpsWatchdogJob = serviceScope.launch {
            while (isActive && _state.value.isRecording) {
                delay(5000)
                val lost = System.currentTimeMillis() - lastGoodSignalTime > 15000
                _state.value = _state.value.copy(
                    hasGpsSignal = !lost,
                    elapsedMillis = System.currentTimeMillis() - startTimeMillis
                )
            }
        }
    }

    private fun handleNewLocation(raw: Location) {
        val accepted = locationFilter.accept(raw) ?: return
        lastGoodSignalTime = System.currentTimeMillis()

        val tripId = currentTripId ?: return
        val previous = lastAcceptedLocation
        val segmentDistance = previous?.distanceTo(accepted) ?: 0f
        lastAcceptedLocation = accepted

        val speedKmh = max(accepted.speed, 0f) * 3.6
        val current = _state.value
        val newDistance = current.distanceMeters + segmentDistance
        val newMax = max(current.maxSpeedKmh, speedKmh.toDouble())

        _state.value = current.copy(
            currentLat = accepted.latitude,
            currentLng = accepted.longitude,
            currentSpeedKmh = speedKmh.toDouble(),
            currentBearing = if (accepted.hasBearing()) accepted.bearing else current.currentBearing,
            distanceMeters = newDistance,
            maxSpeedKmh = newMax,
            hasGpsSignal = true
        )

        // Запись "на лету" в транзакции с NonCancellable — см. TrackingRepository.savePoint.
        serviceScope.launch {
            repository.savePoint(
                TrackPointEntity(
                    tripId = tripId,
                    latitude = accepted.latitude,
                    longitude = accepted.longitude,
                    speedMps = accepted.speed,
                    accuracy = accepted.accuracy,
                    timestamp = accepted.time
                )
            )
        }

        updateNotification(newDistance, speedKmh)
    }

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        gpsWatchdogJob?.cancel()
        val tripId = currentTripId
        val finalState = _state.value

        serviceScope.launch {
            if (tripId != null) {
                val trip = repository.getTrip(tripId)
                if (trip != null) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000.0
                    val avgSpeedMps = if (elapsedSeconds > 0)
                        (finalState.distanceMeters / elapsedSeconds).toFloat() else 0f

                    repository.finishTrip(
                        trip.copy(
                            endTime = System.currentTimeMillis(),
                            totalDistanceMeters = finalState.distanceMeters,
                            maxSpeedMps = (finalState.maxSpeedKmh / 3.6).toFloat(),
                            avgSpeedMps = avgSpeedMps,
                            movingTimeMillis = finalState.elapsedMillis
                        )
                    )
                }
            }
            // Поездка завершена штатно — восстанавливать больше нечего.
            TrackingPrefs.clearActiveTrip(applicationContext)
            _state.value = TrackingState()
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(distanceMeters: Float, speedKmh: Double): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val distanceKm = String.format(Locale.getDefault(), "%.2f", distanceMeters / 1000.0)
        val speedText = String.format(Locale.getDefault(), "%.0f", speedKmh)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text, distanceKm, speedText))
            .setSmallIcon(R.drawable.ic_tracking)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            // Задача 2 (аудит): CATEGORY_SERVICE + PRIORITY_LOW — системе не за что
            // "зацепиться", чтобы посчитать уведомление низкоприоритетным мусором
            // и агрессивнее ограничивать процесс на Samsung One UI.
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(distanceMeters: Float, speedKmh: Double) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(distanceMeters, speedKmh))
    }
}
