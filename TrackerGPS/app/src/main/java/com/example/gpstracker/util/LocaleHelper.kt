package com.example.gpstracker.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Централизованное переключение языка приложения независимо от системной локали.
 * Используется на экране настроек: LocaleHelper.setAppLocale(context, "ru")
 */
object LocaleHelper {

    fun setAppLocale(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: системный API для локали конкретного приложения
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(languageTag)
        } else {
            // Совместимость через AndroidX AppCompat (работает на всех версиях из ТЗ, minSdk 26)
            val localeList = LocaleListCompat.forLanguageTags(languageTag)
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    fun getCurrentLocaleTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "en" else locales[0]?.language ?: "en"
    }
}
