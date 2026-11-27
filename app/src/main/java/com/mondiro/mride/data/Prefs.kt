package com.mondiro.mride.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Prefs {
    private lateinit var sp: SharedPreferences
    val autoRecord = MutableStateFlow(true)
    val stopMinutes = MutableStateFlow(5)
    val highAccuracy = MutableStateFlow(true)
    val mapDark = MutableStateFlow(false)
    val clockElapsed = MutableStateFlow(false)
    val map3d = MutableStateFlow(false)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("mride", Context.MODE_PRIVATE)
        autoRecord.value = sp.getBoolean("autoRecord", true)
        stopMinutes.value = sp.getInt("stopMinutes", 5)
        highAccuracy.value = sp.getBoolean("highAccuracy", true)
        mapDark.value = sp.getBoolean("mapDark2", false)
        clockElapsed.value = sp.getBoolean("clockElapsed", false)
        map3d.value = sp.getBoolean("map3d", false)
    }

    fun setAutoRecord(v: Boolean) { autoRecord.value = v; sp.edit().putBoolean("autoRecord", v).apply() }
    fun setStopMinutes(v: Int) { stopMinutes.value = v; sp.edit().putInt("stopMinutes", v).apply() }
    fun setHighAccuracy(v: Boolean) { highAccuracy.value = v; sp.edit().putBoolean("highAccuracy", v).apply() }
    fun setMap3d(v: Boolean) { map3d.value = v; sp.edit().putBoolean("map3d", v).apply() }
    fun setClockElapsed(v: Boolean) { clockElapsed.value = v; sp.edit().putBoolean("clockElapsed", v).apply() }
    fun setMapDark(v: Boolean) { mapDark.value = v; sp.edit().putBoolean("mapDark2", v).apply() }

    /** Lúc người dùng tự bấm Dừng — sau đó chờ 3 phút mới tự ghi lại. */
    var manualStopAt: Long
        get() = if (::sp.isInitialized) sp.getLong("manualStopAt", 0L) else 0L
        set(v) { sp.edit().putLong("manualStopAt", v).apply() }

    fun deletedUuids(): Set<String> = sp.getStringSet("deleted", emptySet()) ?: emptySet()
    fun addDeleted(uuid: String) {
        sp.edit().putStringSet("deleted", deletedUuids() + uuid).apply()
    }
}
