package com.example.gpstracker.ui.maps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gpstracker.util.ColoredSegment
import com.example.gpstracker.util.MapConfig
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val TRACK_SOURCE_ID = "track-source"
private const val TRACK_LAYER_ID = "track-layer"

/**
 * Заменяет GoogleMap+Marker+Polyline. Текущая позиция теперь рисуется через
 * нативный LocationComponent (Задача 4) с RenderMode.COMPASS — это даёт
 * puck со стрелкой направления вместо простого кружка. Компонент НЕ использует
 * свой внутренний location engine (мы уже получаем точки от TrackingService
 * с собственной фильтрацией) — координаты прокидываются вручную через
 * forceLocationUpdate() в LaunchedEffect ниже.
 */
@SuppressLint("MissingPermission") // разрешение проверяется на уровне онбординга перед стартом трекинга
@Composable
fun MapLibreTrackMap(
    modifier: Modifier = Modifier,
    currentLocation: Location?,
    segments: List<ColoredSegment>,
    cameraZoom: Double = 16.0,
    onMapReady: (MapLibreMap) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = rememberMapViewWithLifecycle(lifecycleOwner)
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var locationComponentReady by remember { mutableStateOf(false) }
    // Задача 3 (аудит): последний "стабильный" азимут — используется, пока
    // скорость слишком мала, чтобы курсу можно было доверять (GPS на стоянке
    // выдаёт случайный bearing, из-за чего стрелка puck-а дёргается).
    var stableBearing by remember { mutableStateOf(0f) }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                map.setStyle(MapConfig.styleUrl()) { style ->
                    addTrackLayer(style)
                    activateLocationComponent(context, map, style)
                    mapLibreMap = map
                    locationComponentReady = true
                    onMapReady(map)
                }
            }
            mapView
        }
    )

    // Каждая новая точка от TrackingService форсированно прокидывается в puck —
    // без включения встроенного GPS-движка LocationComponent (у нас уже есть
    // свой фильтрованный поток координат, дублировать источник не нужно).
    LaunchedEffect(currentLocation, locationComponentReady) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val location = currentLocation ?: return@LaunchedEffect
        if (!locationComponentReady) return@LaunchedEffect

        // Порог ~1 м/с (~3.6 км/ч): ниже него bearing из GPS ненадёжен (шум
        // на месте может показывать разворот на 180° между двумя соседними
        // фиксами). Замораживаем последний стабильный курс вместо того, чтобы
        // отдавать в puck "прыгающее" значение.
        val effectiveBearing = if (location.speed >= 1.0f && location.hasBearing()) {
            location.bearing
        } else {
            stableBearing
        }
        stableBearing = effectiveBearing

        val stabilizedLocation = Location(location).apply { bearing = effectiveBearing }
        map.locationComponent.forceLocationUpdate(stabilizedLocation)
        map.cameraPosition = CameraPosition.Builder()
            .target(org.maplibre.android.geometry.LatLng(location.latitude, location.longitude))
            .zoom(cameraZoom)
            .bearing(effectiveBearing.toDouble())
            .build()
    }

    LaunchedEffect(segments, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (segments.isEmpty()) return@LaunchedEffect
        val features = segments.map { seg ->
            val line = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(seg.startLng, seg.startLat),
                    Point.fromLngLat(seg.endLng, seg.endLat)
                )
            )
            Feature.fromGeometry(line).apply {
                addStringProperty("color", String.format("#%06X", 0xFFFFFF and seg.color.toArgb()))
            }
        }
        map.style?.getSourceAs<GeoJsonSource>(TRACK_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}

private fun addTrackLayer(style: Style) {
    style.addSource(GeoJsonSource(TRACK_SOURCE_ID))
    style.addLayer(
        LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).withProperties(
            lineColor(get("color")),
            lineWidth(4f),
            lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND)
        )
    )
}

/**
 * RenderMode.COMPASS поворачивает стрелку puck-а по направлению движения
 * (bearing из location.bearing, который мы передаём через forceLocationUpdate).
 * useDefaultLocationEngine(false) — принципиально: у нас уже есть свой
 * фильтрованный поток координат из TrackingService/LocationFilter, включать
 * второй, встроенный в LocationComponent GPS-движок избыточно и расходует батарею.
 * CameraMode.NONE — камерой управляем сами через map.cameraPosition, чтобы
 * не конфликтовать с автоследованием LocationComponent.
 */
@SuppressLint("MissingPermission")
private fun activateLocationComponent(context: Context, map: MapLibreMap, style: Style) {
    val locationComponentOptions = LocationComponentOptions.builder(context)
        .pulseEnabled(true)
        .build()

    val activationOptions = LocationComponentActivationOptions.builder(context, style)
        .locationComponentOptions(locationComponentOptions)
        .useDefaultLocationEngine(false)
        .build()

    with(map.locationComponent) {
        activateLocationComponent(activationOptions)
        isLocationComponentEnabled = true
        renderMode = RenderMode.COMPASS
        cameraMode = CameraMode.NONE
    }
}

/** Привязка жизненного цикла MapView к Compose lifecycle (обязательно для MapLibre/Mapbox-based SDK). */
@Composable
private fun rememberMapViewWithLifecycle(lifecycleOwner: androidx.lifecycle.LifecycleOwner): MapView {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapView = remember { createMapView(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return mapView
}

private fun createMapView(context: Context): MapView {
    org.maplibre.android.MapLibre.getInstance(context)
    return MapView(context).apply { onCreate(null) }
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
    )
