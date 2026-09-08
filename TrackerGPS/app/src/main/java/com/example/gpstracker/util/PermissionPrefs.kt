package com.example.gpstracker.util

import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat

/**
 * Android не даёt прямого API "разрешение отклонено навсегда" — это выводится
 * косвенно: shouldShowRequestPermissionRationale() возвращает false ПОСЛЕ
 * повторного отказа с галочкой "Больше не спрашивать", но также возвращает
 * false и ДО первого запроса. Различить эти два случая можно только храня
 * собственный флаг "этот permission уже запрашивался раньше".
 */
object PermissionPrefs {
    private const val PREFS_NAME = "permission_prefs"

    fun markAsRequested(context: Context, permission: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(permission), true)
            .apply()
    }

    private fun wasRequestedBefore(context: Context, permission: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key(permission), false)

    /**
     * true, если пользователь уже отказывал в этом разрешении раньше И система
     * больше не показывает rationale — это и есть перманентный отказ
     * ("Больше не спрашивать" / "Deny & don't ask again").
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        val requestedBefore = wasRequestedBefore(activity, permission)
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        return requestedBefore && !shouldShowRationale
    }

    private fun key(permission: String) = "asked_$permission"
}
