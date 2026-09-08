package com.example.gpstracker.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gpstracker.util.BatteryOptimizationHelper
import com.example.gpstracker.util.PermissionPrefs

private enum class OnboardingStep {
    FINE_LOCATION, FINE_LOCATION_DENIED_FOREVER,
    BACKGROUND_LOCATION,
    NOTIFICATIONS, NOTIFICATIONS_DENIED_FOREVER,
    BATTERY, DONE
}

/**
 * На Note 20 / One UI 5.1 (Android 13) разрешения запрашиваются строго по
 * одному и в этом порядке — background location физически нельзя запросить
 * одновременно с fine location, а диалог батареи полагается показывать
 * отдельным явным действием пользователя.
 *
 * Задача 6: если пользователь отказал с галочкой "Больше не спрашивать",
 * системный диалог повторно не появится — экран переключается в explain-режим
 * с кнопкой перехода в системные настройки приложения.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var step by remember { mutableStateOf(nextStep(context, activity)) }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        PermissionPrefs.markAsRequested(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        step = when {
            granted -> nextStep(context, activity)
            activity != null && PermissionPrefs.isPermanentlyDenied(activity, Manifest.permission.ACCESS_FINE_LOCATION) ->
                OnboardingStep.FINE_LOCATION_DENIED_FOREVER
            else -> OnboardingStep.FINE_LOCATION
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Фоновое разрешение не блокирует базовый функционал (foreground-трекинг
        // продолжит работать), поэтому окончательный отказ здесь не показываем
        // отдельным экраном — просто идём дальше по цепочке.
        step = nextStep(context, activity)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PermissionPrefs.markAsRequested(context, Manifest.permission.POST_NOTIFICATIONS)
        step = when {
            granted -> nextStep(context, activity)
            activity != null && PermissionPrefs.isPermanentlyDenied(activity, Manifest.permission.POST_NOTIFICATIONS) ->
                OnboardingStep.NOTIFICATIONS_DENIED_FOREVER
            else -> OnboardingStep.NOTIFICATIONS
        }
    }

    if (step == OnboardingStep.DONE) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    // Пользователь мог включить разрешение вручную в системных настройках и
    // вернуться назад — без этого шаг PermanentlyDenied завис бы навсегда,
    // даже если разрешение уже выдано.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                step = nextStep(context, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            OnboardingStep.FINE_LOCATION -> OnboardingCard(
                icon = Icons.Filled.LocationOn,
                title = "Доступ к геолокации",
                description = "Приложению нужен точный доступ к местоположению, чтобы записывать маршрут.",
                buttonText = "Разрешить",
                onClick = {
                    PermissionPrefs.markAsRequested(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    fineLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            OnboardingStep.FINE_LOCATION_DENIED_FOREVER -> PermanentlyDeniedCard(
                title = "Геолокация отключена навсегда",
                description = "Вы отказали в доступе к местоположению с пометкой \"Больше не спрашивать\". " +
                        "Без этого разрешения приложение не может записывать маршрут. " +
                        "Включите его вручную в настройках приложения.",
                onOpenSettings = { openAppSettings(context) }
            )

            OnboardingStep.BACKGROUND_LOCATION -> OnboardingCard(
                icon = Icons.Filled.LocationOn,
                title = "Трекинг при заблокированном экране",
                description = "На следующем экране выберите «Разрешать всегда» — иначе запись " +
                        "маршрута остановится, как только вы заблокируете телефон.",
                buttonText = "Продолжить",
                onClick = { backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            )

            OnboardingStep.NOTIFICATIONS -> OnboardingCard(
                icon = Icons.Filled.Notifications,
                title = "Уведомление о записи",
                description = "Android требует показывать уведомление, пока идёт фоновая запись маршрута.",
                buttonText = "Разрешить",
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )

            OnboardingStep.NOTIFICATIONS_DENIED_FOREVER -> PermanentlyDeniedCard(
                title = "Уведомления отключены навсегда",
                description = "Без уведомления Android может значительно быстрее останавливать " +
                        "фоновую запись маршрута. Рекомендуем включить уведомления вручную в настройках.",
                onOpenSettings = { openAppSettings(context) },
                allowSkip = true,
                onSkip = { step = nextStep(context, activity, skipNotifications = true) }
            )

            OnboardingStep.BATTERY -> OnboardingCard(
                icon = Icons.Filled.BatteryAlert,
                title = "Отключить оптимизацию батареи",
                description = "На Samsung Device Care по умолчанию \"усыпляет\" приложения в фоне " +
                        "и может остановить запись поездки. Разрешите работу без ограничений, а если " +
                        "трекинг всё равно будет обрываться — откройте Device Care → Батарея → " +
                        "Ограничения фона и уберите приложение из списка \"Спящие\".",
                buttonText = "Открыть настройки",
                onClick = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    step = OnboardingStep.DONE
                },
                secondaryButtonText = "Открыть Device Care вручную",
                onSecondaryClick = { BatteryOptimizationHelper.openAppSettings(context) }
            )

            OnboardingStep.DONE -> Unit
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private fun nextStep(context: Context, activity: Activity?, skipNotifications: Boolean = false): OnboardingStep {
    val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!fineGranted) {
        return if (activity != null && PermissionPrefs.isPermanentlyDenied(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            OnboardingStep.FINE_LOCATION_DENIED_FOREVER
        } else {
            OnboardingStep.FINE_LOCATION
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val backgroundGranted = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!backgroundGranted) return OnboardingStep.BACKGROUND_LOCATION
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !skipNotifications) {
        val notifGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!notifGranted) {
            return if (activity != null && PermissionPrefs.isPermanentlyDenied(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                OnboardingStep.NOTIFICATIONS_DENIED_FOREVER
            } else {
                OnboardingStep.NOTIFICATIONS
            }
        }
    }

    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) return OnboardingStep.BATTERY

    return OnboardingStep.DONE
}

@Composable
private fun OnboardingCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp))
    Spacer(modifier = Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(8.dp))
    Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
    if (secondaryButtonText != null && onSecondaryClick != null) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSecondaryClick, modifier = Modifier.fillMaxWidth()) {
            Text(secondaryButtonText)
        }
    }
}

/** Экран объяснения для окончательного отказа (Задача 6) — ведёт в системные настройки приложения. */
@Composable
private fun PermanentlyDeniedCard(
    title: String,
    description: String,
    onOpenSettings: () -> Unit,
    allowSkip: Boolean = false,
    onSkip: (() -> Unit)? = null
) {
    Icon(Icons.Filled.SettingsSuggest, contentDescription = null, modifier = Modifier.size(64.dp))
    Spacer(modifier = Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(8.dp))
    Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Text("Открыть настройки приложения")
    }
    if (allowSkip && onSkip != null) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Продолжить без уведомлений")
        }
    }
}
