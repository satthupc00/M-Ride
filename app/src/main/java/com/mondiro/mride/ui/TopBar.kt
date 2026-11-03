package com.mondiro.mride.ui

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mondiro.mride.R
import com.mondiro.mride.sync.Sync
import com.mondiro.mride.update.Updater
import kotlinx.coroutines.launch

/** Hàng icon mỏng ở trên cùng (Android không có header logo + tên). */
@Composable
fun TopBar(modifier: Modifier = Modifier, left: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 6.dp, top = 2.dp).height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        left()
        Spacer(Modifier.weight(1f))
        SyncDot()
        UpdateButton()
    }
}

@Composable
fun SyncDot() {
    val ctx = LocalContext.current
    val st by Sync.status.collectAsState()
    val email by Sync.email.collectAsState()
    val color by animateColorAsState(
        when (st) {
            Sync.Status.Off -> C.grey
            Sync.Status.Pending -> C.warn
            Sync.Status.Synced -> C.ok
            Sync.Status.Error -> C.red
        }, label = "sync",
    )
    val label = when (st) {
        Sync.Status.Off -> if (Sync.available()) "Đăng nhập để đồng bộ" else "Chỉ lưu trên máy"
        Sync.Status.Pending -> "Đang đồng bộ…"
        Sync.Status.Synced -> "Đã đồng bộ" + (email?.let { " · $it" } ?: "")
        Sync.Status.Error -> "Lỗi đồng bộ, sẽ thử lại"
    }
    val pulse = rememberInfiniteTransition(label = "p")
    val s by pulse.animateFloat(1f, .7f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "ps")
    Box(
        Modifier.size(40.dp).pressable(CircleShape) {
            Toast.makeText(ctx, label, Toast.LENGTH_SHORT).show()
            Sync.request()
        }.semantics { contentDescription = "Đồng bộ: $label" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(20.dp).background(color.copy(alpha = .2f), CircleShape))
        Box(
            Modifier.size(12.dp).scale(if (st == Sync.Status.Pending) s else 1f)
                .background(color, CircleShape)
        )
    }
}

@Composable
fun UpdateButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Updater.state.collectAsState()
    val inf = rememberInfiniteTransition(label = "u")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    val pulse by inf.animateFloat(1f, .45f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    val tint = when (st) {
        Updater.State.Latest, is Updater.State.Available, is Updater.State.Downloading -> C.ok
        else -> C.text
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).pressable(CircleShape) {
                if (st is Updater.State.Available || st is Updater.State.Downloading) {
                    Updater.showDialog.value = true
                } else scope.launch {
                    when (val r = Updater.check(showIfNew = true)) {
                        Updater.State.Latest -> Toast.makeText(ctx, "Đã là bản mới nhất", Toast.LENGTH_SHORT).show()
                        is Updater.State.Error -> Toast.makeText(ctx, r.msg, Toast.LENGTH_SHORT).show()
                        else -> {}
                    }
                }
            }.semantics { contentDescription = "Kiểm tra cập nhật" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_refresh), null,
                Modifier.size(19.dp)
                    .rotate(if (st == Updater.State.Checking) rot else 0f)
                    .alpha(if (st is Updater.State.Available) pulse else 1f),
                tint = tint,
            )
        }
        if (st == Updater.State.Latest) {
            Text(
                "latest", color = C.latest, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(end = 4.dp)
                    .background(C.latest.copy(alpha = .14f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun RecPill(startTime: Long, now: Long) {
    val inf = rememberInfiniteTransition(label = "rec")
    val a by inf.animateFloat(1f, .35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "ra")
    Row(
        Modifier.background(C.chrome.copy(alpha = .9f), RoundedCornerShape(20.dp))
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).alpha(a).background(C.red, CircleShape))
        Text("  Đang ghi", color = C.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(" · ${Fmt.dur(now - startTime)}", color = C.muted, fontSize = 12.sp)
    }
}

@Composable
fun SmallIcon(id: Int) = Icon(painterResource(id), null, Modifier.size(14.dp))
