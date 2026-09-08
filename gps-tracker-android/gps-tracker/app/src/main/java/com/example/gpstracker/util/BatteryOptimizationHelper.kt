package com.example.gpstracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * One UI (Samsung) применяет собственный слой энергосбережения поверх стандартного
 * Doze/App Standby: список "Спящих приложений" в Device Care может останавливать
 * foreground-сервисы куда агрессивнее чистого Android. Системный запрос
 * IGNORE_BATTERY_OPTIMIZATIONS решает это частично — Samsung дополнительно
 * требует, чтобы пользователь вручную снял приложение со сна в Device Care
 * (см. openSamsungBatterySettings ниже, т.к. прямого API для этого нет).
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Системный диалог "Разрешить работу в фоне без ограничений". */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    /**
     * У Samsung нет публичного API, чтобы программно снять приложение со "сна".
     * Максимум, что можно сделать — открыть экран настроек приложения, откуда
     * пользователь сам переходит в Device Care → Батарея → Ограничения фона.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
