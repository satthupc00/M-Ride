package com.mondiro.mride.update

import android.content.Context
import org.json.JSONObject

data class WhatsNew(
    val version: String,
    val date: String,
    val new: List<String>,
    val improved: List<String>,
    val fixed: List<String>,
) {
    companion object {
        fun load(ctx: Context): WhatsNew? = try {
            val j = JSONObject(ctx.assets.open("whatsnew.json").bufferedReader().use { it.readText() })
            fun list(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            WhatsNew(j.optString("version"), j.optString("date"), list("new"), list("improved"), list("fixed"))
        } catch (_: Exception) {
            null
        }
    }
}
