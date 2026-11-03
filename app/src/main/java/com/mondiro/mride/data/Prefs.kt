package com.mondiro.mride.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Prefs {
    private lateinit var sp: SharedPreferences
    val autoRecord = MutableStateFlow(true)
    val stopMinutes = MutableStateFlow(5)
    val highAccuracy = MutableStateFlow(true)
    val mapDark = MutableStateFlow(true)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("mride", Context.MODE_PRIVATE)
        autoRecord.value = sp.getBoolean("autoRecord", true)
        stopMinutes.value = sp.getInt("stopMinutes", 5)
        highAccuracy.value = sp.getBoolean("highAccuracy", true)
        mapDark.value = sp.getBoolean("mapDark", true)
    }

    fun setAutoRecord(v: Boolean) { autoRecord.value = v; sp.edit().putBoolean("autoRecord", v).apply() }
    fun setStopMinutes(v: Int) { stopMinutes.value = v; sp.edit().putInt("stopMinutes", v).apply() }
    fun setHighAccuracy(v: Boolean) { highAccuracy.value = v; sp.edit().putBoolean("highAccuracy", v).apply() }
    fun setMapDark(v: Boolean) { mapDark.value = v; sp.edit().putBoolean("mapDark", v).apply() }

    fun deletedUuids(): Set<String> = sp.getStringSet("deleted", emptySet()) ?: emptySet()
    fun addDeleted(uuid: String) {
        sp.edit().putStringSet("deleted", deletedUuids() + uuid).apply()
    }
}
