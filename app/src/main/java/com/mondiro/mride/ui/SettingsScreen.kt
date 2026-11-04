package com.mondiro.mride.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mondiro.mride.BuildConfig
import com.mondiro.mride.R
import com.mondiro.mride.data.Prefs
import com.mondiro.mride.sync.Sync
import com.mondiro.mride.tracking.AutoDetect
import com.mondiro.mride.update.Updater
import kotlinx.coroutines.launch

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.panel)) {
        Text(
            title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.muted,
            letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 14.dp, top = 11.dp, bottom = 2.dp),
        )
        content()
    }
}

@Composable
private fun Item(title: String, sub: String?, divider: Boolean = true, trailing: @Composable () -> Unit = {}) {
    if (divider) HorizontalDivider(color = C.line, thickness = 1.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.text)
            if (sub != null) Text(sub, fontSize = 12.sp, color = C.muted)
        }
        trailing()
    }
}

@Composable
private fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(C.chrome).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, o ->
            val shape = RoundedCornerShape(6.dp)
            Box(
                Modifier.pressable(shape) { onSelect(i) }
                    .background(if (i == selected) C.selected else Color.Transparent, shape)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Text(o, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (i == selected) C.text else C.muted)
            }
        }
    }
}

@Composable
private fun RedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = C.red, checkedThumbColor = Color.White, checkedBorderColor = C.red,
            uncheckedTrackColor = C.track, uncheckedThumbColor = Color.White, uncheckedBorderColor = C.track,
        ),
    )
}

@Composable
fun SettingsScreen(wide: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val auto by Prefs.autoRecord.collectAsState()
    val stopMin by Prefs.stopMinutes.collectAsState()
    val hi by Prefs.highAccuracy.collectAsState()
    val dark by Prefs.mapDark.collectAsState()
    val email by Sync.email.collectAsState()
    val syncSt by Sync.status.collectAsState()
    val upd by Updater.state.collectAsState()
    var showNew by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(C.chrome)) {
        Column(Modifier.fillMaxSize()) {
            TopBar { Text("Cài đặt", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.text) }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 40.dp)
                    .then(if (wide) Modifier.widthIn(max = 640.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Group("Ghi hành trình") {
                    Item("Tự ghi khi đi xe", "Bắt đầu khi phát hiện đang đi xe, tự dừng khi đứng yên", divider = false) {
                        RedSwitch(auto) {
                            Prefs.setAutoRecord(it)
                            if (it) AutoDetect.register(ctx) else AutoDetect.unregister(ctx)
                        }
                    }
                    Item("Tự dừng sau khi đứng yên", "Kết thúc chuyến nếu không di chuyển") {
                        val opts = listOf(3, 5, 10)
                        Seg(opts.map { "$it'" }, opts.indexOf(stopMin).coerceAtLeast(0)) { Prefs.setStopMinutes(opts[it]) }
                    }
                    Item("Độ chính xác GPS", "Cao hơn = đường vẽ mịn hơn, tốn pin hơn") {
                        Seg(listOf("Tiết kiệm", "Cao"), if (hi) 1 else 0) { Prefs.setHighAccuracy(it == 1) }
                    }
                }

                Group("Đồng bộ") {
                    when {
                        !Sync.available() -> Item("Chưa thiết lập đồng bộ", "Hành trình đang được lưu trên máy", divider = false)
                        email == null -> Item("Đăng nhập để đồng bộ", "Dùng tài khoản Google, xem lại trên máy khác", divider = false) {
                            GhostButton("Đăng nhập", R.drawable.ic_login, height = 34) {
                                val act = ctx.findActivity() ?: return@GhostButton
                                scope.launch {
                                    Sync.signIn(act)?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                        else -> Item(
                            email ?: "", when (syncSt) {
                                Sync.Status.Synced -> "Đã đồng bộ"
                                Sync.Status.Pending -> "Đang đồng bộ…"
                                Sync.Status.Error -> "Lỗi đồng bộ, sẽ thử lại"
                                Sync.Status.Off -> "Chưa đồng bộ"
                            }, divider = false,
                        ) {
                            GhostButton("Đăng xuất", R.drawable.ic_logout, height = 34) {
                                scope.launch { Sync.signOut(ctx) }
                            }
                        }
                    }
                }

                Group("Bản đồ") {
                    Item("Kiểu bản đồ", null, divider = false) {
                        Seg(listOf("Màu", "Tối"), if (dark) 1 else 0) { Prefs.setMapDark(it == 1) }
                    }
                }

                Group("Phiên bản") {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppName()
                            Text("  v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = C.muted, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            when (val u = upd) {
                                Updater.State.Latest -> "Đã là bản mới nhất"
                                is Updater.State.Available -> "Có bản mới v${u.version}"
                                is Updater.State.Downloading -> "Đang tải bản cập nhật…"
                                Updater.State.Checking -> "Đang kiểm tra…"
                                else -> "Phiên bản đang dùng"
                            },
                            fontSize = 12.sp, color = if (upd == Updater.State.Latest) C.latest else C.muted,
                        )
                    }
                    Row(
                        Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GhostButton("What's new", R.drawable.ic_sparkles, height = 36) { showNew = true }
                        if (upd is Updater.State.Available || upd is Updater.State.Downloading) {
                            RedButton("Cập nhật", R.drawable.ic_refresh, Modifier.heightIn(max = 36.dp)) {
                                Updater.showDialog.value = true
                            }
                        } else {
                            GhostButton("Kiểm tra cập nhật", R.drawable.ic_refresh, height = 36) {
                                scope.launch {
                                    when (val r = Updater.check(showIfNew = true)) {
                                        Updater.State.Latest -> Toast.makeText(ctx, "Đã là bản mới nhất (v${com.mondiro.mride.BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                        is Updater.State.Error -> Toast.makeText(ctx, r.msg, Toast.LENGTH_SHORT).show()
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
                Credit(Modifier.align(Alignment.End))
            }
        }
    }
    if (showNew) WhatsNewDialog { showNew = false }
}

