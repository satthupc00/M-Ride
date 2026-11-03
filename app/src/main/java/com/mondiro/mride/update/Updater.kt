package com.mondiro.mride.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mondiro.mride.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Tự cập nhật từ GitHub Releases của repo satthupc00/M-Ride. */
object Updater {
    private const val API = "https://api.github.com/repos/satthupc00/M-Ride/releases/latest"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object Latest : State
        data class Available(val version: String, val url: String, val notes: String) : State
        data class Downloading(val version: String, val progress: Float) : State
        data class Error(val msg: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    val showDialog = MutableStateFlow(false)
    var available: State.Available? = null
        private set

    suspend fun check(showIfNew: Boolean): State = withContext(Dispatchers.IO) {
        if (state.value is State.Downloading) return@withContext state.value
        state.value = State.Checking
        val result: State = try {
            val c = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "M-Ride-App")
            }
            if (c.responseCode == 404) {
                State.Latest
            } else {
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val j = JSONObject(body)
                val tag = j.optString("tag_name").removePrefix("v")
                val assets = j.optJSONArray("assets")
                var url = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url"); break
                    }
                }
                if (url.isNotEmpty() && newer(tag, BuildConfig.VERSION_NAME)) {
                    State.Available(tag, url, j.optString("body"))
                } else State.Latest
            }
        } catch (e: Exception) {
            State.Error("Không kiểm tra được cập nhật")
        }
        if (result is State.Available) {
            available = result
            if (showIfNew) showDialog.value = true
        }
        state.value = result
        result
    }

    fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    suspend fun downloadAndInstall(ctx: Context) {
        val a = available ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) { askInstallPermission(ctx) }
            return
        }
        val f = withContext(Dispatchers.IO) {
            try {
                val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "M-Ride-${a.version}.apk")
                val c = (URL(a.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "M-Ride-App")
                }
                val total = c.contentLengthLong
                state.value = State.Downloading(a.version, 0f)
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) state.value = State.Downloading(a.version, read.toFloat() / total)
                        }
                    }
                }
                state.value = a
                file
            } catch (e: Exception) {
                state.value = State.Error("Tải bản cập nhật bị lỗi")
                null
            }
        } ?: return
        withContext(Dispatchers.Main) { install(ctx, f) }
    }

    private fun askInstallPermission(ctx: Context) {
        Toast.makeText(ctx, "Cho phép M Ride cài ứng dụng, rồi bấm Cập nhật lại", Toast.LENGTH_LONG).show()
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
