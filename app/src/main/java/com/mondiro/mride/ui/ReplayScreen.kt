package com.mondiro.mride.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mondiro.mride.R
import com.mondiro.mride.data.RideDb
import com.mondiro.mride.data.TrackPoint
import com.mondiro.mride.data.Trip
import com.mondiro.mride.sync.Sync
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Dòng thời gian ảo: các khoảng nghỉ dài giữa 2 chuyến được rút còn 2 giây. */
class ReplayData(val pts: List<TrackPoint>) {
    val geo: List<GeoPoint> = pts.map { GeoPoint(it.lat, it.lon) }
    private val vt = DoubleArray(pts.size)

    init {
        for (i in 1 until pts.size) {
            val dt = (pts[i].time - pts[i - 1].time) / 1000.0
            vt[i] = vt[i - 1] + if (dt > 120) 2.0 else dt.coerceAtLeast(0.0)
        }
    }

    val total: Double = if (pts.isEmpty()) 0.0 else vt.last()

    fun idx(t: Double): Int {
        var lo = 0
        var hi = pts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (vt[mid] <= t) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun frac(i: Int, t: Double): Double {
        if (i >= pts.size - 1) return 0.0
        val span = vt[i + 1] - vt[i]
        return if (span > 0) ((t - vt[i]) / span).coerceIn(0.0, 1.0) else 0.0
    }

    fun pos(t: Double): GeoPoint {
        val i = idx(t)
        if (i >= pts.size - 1) return geo.last()
        val f = frac(i, t)
        return GeoPoint(
            pts[i].lat + (pts[i + 1].lat - pts[i].lat) * f,
            pts[i].lon + (pts[i + 1].lon - pts[i].lon) * f,
        )
    }

    fun clock(t: Double): Long {
        val i = idx(t)
        if (i >= pts.size - 1) return pts.last().time
        return pts[i].time + ((pts[i + 1].time - pts[i].time) * frac(i, t)).toLong()
    }

    fun speed(t: Double): Float {
        val i = idx(t)
        if (i >= pts.size - 1) return pts.last().speedKmh
        val f = frac(i, t).toFloat()
        return pts[i].speedKmh + (pts[i + 1].speedKmh - pts[i].speedKmh) * f
    }

    /** Hướng đi (độ, 0 = Bắc) nhìn trước ~10 giây để mượt, null nếu gần như đứng yên. */
    fun heading(t: Double): Double? {
        val a = pos(t)
        val b = pos((t + 10).coerceAtMost(total))
        val dLat = b.latitude - a.latitude
        val dLon = (b.longitude - a.longitude) * Math.cos(Math.toRadians(a.latitude))
        if (Math.hypot(dLat, dLon) < 0.00003) return null // < ~3 m
        return (Math.toDegrees(Math.atan2(dLon, dLat)) + 360) % 360
    }

    fun progress(t: Double): List<GeoPoint> {
        val i = idx(t)
        val out = ArrayList<GeoPoint>(i + 2)
        out.addAll(geo.subList(0, i + 1))
        out.add(pos(t))
        return out
    }
}

private val RATES = listOf(1, 10, 30, 60, 120)

@Composable
private fun MapChip(text: String, icon: Int, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.pressable(shape) { onClick() }
            .background(if (on) C.red else C.chrome.copy(alpha = .92f), shape)
            .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = Color.White)
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun ReplayView(sel: ReplaySel, compact: Boolean, onBack: (() -> Unit)?, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { RideDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val loaded by produceState<Pair<List<Trip>, ReplayData>?>(null, sel) {
        val trips = dao.tripsByIds(sel.ids)
        value = trips to ReplayData(dao.pointsFor(sel.ids))
    }
    var t by remember(sel) { mutableDoubleStateOf(0.0) }
    var playing by remember(sel) { mutableStateOf(true) }
    var rate by remember { mutableIntStateOf(60) }
    var askDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var follow by remember(sel) { mutableStateOf(true) }
    var rotate by remember { mutableStateOf(false) }
    var camBearing by remember(sel) { mutableDoubleStateOf(0.0) }
    val data = loaded?.second

    LaunchedEffect(playing, rate, data) {
        val d = data ?: return@LaunchedEffect
        if (!playing || d.total <= 0) return@LaunchedEffect
        if (t >= d.total) t = 0.0
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (last > 0) t = (t + (now - last) / 1e9 * rate).coerceAtMost(d.total)
                last = now
            }
            if (t >= d.total) {
                playing = false
                break
            }
        }
    }

    // Làm mượt góc xoay bản đồ theo hướng xe
    LaunchedEffect(rotate, follow, data) {
        val d = data ?: return@LaunchedEffect
        if (!rotate || !follow || d.pts.size < 2) return@LaunchedEffect
        d.heading(t)?.let { camBearing = it }
        while (isActive) {
            withFrameNanos { }
            val target = d.heading(t) ?: continue
            var delta = (target - camBearing + 540) % 360 - 180
            if (Math.abs(delta) < 0.3) delta = 0.0
            camBearing = (camBearing + delta * 0.12 + 360) % 360
        }
    }

    val tracks = remember(data) { if (data == null || data.geo.size < 2) emptyList() else listOf(data.geo) }
    val ready = data != null && data.pts.size >= 2

    Box(Modifier.fillMaxSize()) {
        RideMap(
            Modifier.fillMaxSize(), tracks, faded = true,
            progress = if (ready) data!!.progress(t) else null,
            dot = if (ready) data!!.pos(t) else null,
            fitKey = data, bottomPadFrac = 0.30,
            follow = follow && ready,
            bearing = if (rotate && ready) camBearing else null,
            onUserGesture = { follow = false },
        )
        TopFade()
        TopBar {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    val shape = RoundedCornerShape(20.dp)
                    Row(
                        Modifier.pressable(shape) { onBack() }.background(C.chrome.copy(alpha = .9f), shape)
                            .padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_back), null, Modifier.size(15.dp), tint = C.text)
                        Text(" Hành trình", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = C.text)
                    }
                }
                if (ready) {
                    IconBtn(R.drawable.ic_share, "Chia sẻ hành trình") {
                        if (sharing) return@IconBtn
                        val trips = loaded?.first ?: return@IconBtn
                        val pts = data?.pts ?: return@IconBtn
                        sharing = true
                        Toast.makeText(ctx, "Đang tạo link chia sẻ…", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            try {
                                val title = if (trips.size == 1) trips[0].name else "Cả ngày · " + Fmt.day(trips.first().startTime)
                                val url = Sync.share(trips, pts, title)
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, "Xem lại hành trình của mình trên M Ride (link có hạn 24 giờ): $url")
                                ctx.startActivity(Intent.createChooser(send, "Chia sẻ hành trình"))
                            } catch (e: IllegalStateException) {
                                Toast.makeText(ctx, e.message ?: "Không chia sẻ được", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Không tạo được link, kiểm tra kết nối mạng", Toast.LENGTH_LONG).show()
                            } finally {
                                sharing = false
                            }
                        }
                    }
                }
                if (sel.ids.size == 1 && loaded?.first?.firstOrNull()?.active == false) {
                    IconBtn(R.drawable.ic_trash, "Xóa hành trình", C.muted) { askDelete = true }
                }
            }
        }

        if (loaded != null && !ready) {
            Text(
                "Chuyến này chưa có đủ dữ liệu để phát lại",
                color = C.muted, modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp)) {
            if (ready) {
                Row(
                    Modifier.align(Alignment.End).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapChip(
                        if (rotate) "Xoay theo xe" else "Hướng Bắc",
                        if (rotate) R.drawable.ic_compass_on else R.drawable.ic_compass, rotate,
                    ) {
                        rotate = !rotate
                        if (rotate) follow = true
                    }
                    MapChip(
                        if (follow) "Đang bám xe" else "Bám theo xe",
                        if (follow) R.drawable.ic_navigation_on else R.drawable.ic_navigation, follow,
                    ) { follow = !follow }
                }
            }
            Card {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (ready) Fmt.hms(data!!.clock(t)) else "--:--:--",
                                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = C.text,
                            )
                            val first = loaded?.first?.firstOrNull()
                            Text(
                                (if (sel.ids.size == 1) first?.name?.ifBlank { "Chuyến đi" } else sel.title) +
                                    (first?.let { " · " + Fmt.day(it.startTime) } ?: ""),
                                fontSize = 12.sp, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (ready) String.format("%.0f", data!!.speed(t)) else "0",
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.text,
                            )
                            Text("km/h", fontSize = 11.sp, color = C.muted)
                        }
                    }
                    Slider(
                        value = if (data != null && data.total > 0) (t / data.total).toFloat() else 0f,
                        onValueChange = { v -> data?.let { t = v * it.total } },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White, activeTrackColor = C.red,
                            inactiveTrackColor = C.track,
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(50.dp).pressable(CircleShape) {
                                if (data != null && t >= data.total) t = 0.0
                                playing = !playing
                            }.background(C.red, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                                if (playing) "Tạm dừng" else "Phát", Modifier.size(20.dp), tint = Color.White,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            RATES.forEach { r -> RateChip("$r×", rate == r) { rate = r } }
                        }
                    }
                }
            }
            Credit(Modifier.align(Alignment.End).padding(top = 3.dp, bottom = 3.dp, end = 2.dp))
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            containerColor = C.panel,
            title = { Text("Xóa hành trình này?", color = C.text) },
            text = { Text("Hành trình sẽ bị xóa khỏi máy và khỏi tài khoản đồng bộ.", color = C.muted) },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    val trip = loaded?.first?.firstOrNull() ?: return@TextButton
                    scope.launch {
                        dao.deleteTrip(trip.id)
                        Sync.deleteRemote(trip.uuid)
                        onDeleted()
                    }
                }) { Text("Xóa", color = C.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Huỷ", color = C.text) } },
        )
    }
}

