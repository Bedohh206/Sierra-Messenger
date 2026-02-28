package com.btmessenger.app.util

import android.content.Context

object AlertPreferences {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_ALERT_SOUND = "alert_sound_enabled"
    private const val KEY_ALERT_VIBRATION = "alert_vibration_enabled"

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALERT_SOUND, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALERT_SOUND, enabled).apply()
    }

    fun isVibrationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALERT_VIBRATION, true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALERT_VIBRATION, enabled).apply()
    }
}
