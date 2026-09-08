package com.example.gpstracker

import android.app.Application

/**
 * Пустой класс: сохранение и восстановление выбранного языка на Android 13+
 * делает система (LocaleManager per-app language), а AppCompatDelegate на
 * более старых версиях сохраняет выбор автоматически через свой собственный
 * SharedPreferences-стор и восстанавливает его до onCreate любой Activity —
 * дополнительная инициализация здесь не требуется.
 */
class TrackerApp : Application()
