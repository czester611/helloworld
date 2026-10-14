package com.fitlab.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ustawienia w SharedPreferences `fitlab_settings`. Double zapisywany jako Long (raw bits). */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fitlab_settings", Context.MODE_PRIVATE)

    private val _activePrinterId = MutableStateFlow(prefs.getLong(KEY_PRINTER, -1L))
    private val _activeFilamentId = MutableStateFlow(prefs.getLong(KEY_FILAMENT, -1L))
    private val _sheetScaleX = MutableStateFlow(readDouble(KEY_SCALE_X, 1.0))
    private val _sheetScaleY = MutableStateFlow(readDouble(KEY_SCALE_Y, 1.0))

    val activePrinterId: StateFlow<Long> = _activePrinterId.asStateFlow()
    val activeFilamentId: StateFlow<Long> = _activeFilamentId.asStateFlow()
    val sheetScaleX: StateFlow<Double> = _sheetScaleX.asStateFlow()
    val sheetScaleY: StateFlow<Double> = _sheetScaleY.asStateFlow()

    fun setActivePrinter(id: Long) {
        prefs.edit().putLong(KEY_PRINTER, id).apply()
        _activePrinterId.value = id
    }

    fun setActiveFilament(id: Long) {
        prefs.edit().putLong(KEY_FILAMENT, id).apply()
        _activeFilamentId.value = id
    }

    fun setSheetScale(x: Double, y: Double) {
        prefs.edit()
            .putLong(KEY_SCALE_X, java.lang.Double.doubleToRawLongBits(x))
            .putLong(KEY_SCALE_Y, java.lang.Double.doubleToRawLongBits(y))
            .apply()
        _sheetScaleX.value = x
        _sheetScaleY.value = y
    }

    private fun readDouble(key: String, def: Double): Double =
        if (prefs.contains(key)) java.lang.Double.longBitsToDouble(prefs.getLong(key, 0L)) else def

    private companion object {
        const val KEY_PRINTER = "activePrinterId"
        const val KEY_FILAMENT = "activeFilamentId"
        const val KEY_SCALE_X = "sheetScaleX"
        const val KEY_SCALE_Y = "sheetScaleY"
    }
}
