package com.mondiro.mride.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.mondiro.mride.R
import com.mondiro.mride.data.RideDb
import com.mondiro.mride.data.Trip
import com.mondiro.mride.tracking.Perms
import com.mondiro.mride.tracking.Tracker
import com.mondiro.mride.tracking.TrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

data class DayStats(val dist: Double, val durMs: Long, val maxKmh: Double) {
    val avgKmh: Double get() = if (durMs > 0) (dist / 1000.0) / (durMs / 3_600_000.0) else 0.0

    companion object {
        fun of(trips: List<Trip>) = DayStats(
            trips.sumOf { it.distanceM },
            trips.sumOf { it.endTime - it.startTime },
            trips.maxOfOrNull { it.maxSpeedKmh } ?: 0.0,
        )
    }
}

@SuppressLint("MissingPermission")
suspend fun lastKnown(ctx: Context): GeoPoint? = try {
    if (!Perms.hasFine(ctx)) null
    else LocationServices.getFusedLocationProviderClient(ctx).lastLocation.await()
        ?.let { GeoPoint(it.latitude, it.longitude) }
} catch (_: Exception) {
    null
}

fun toggleRecording(ctx: Context) {
    if (Tracker.state.value.recording) {
        TrackingService.stop(ctx)
        return
    }
    when {
        !Perms.hasFine(ctx) -> Toast.makeText(ctx, "Cần cho phép vị trí trước", Toast.LENGTH_SHORT).show()
        !Perms.locationOn(ctx) -> Toast.makeText(ctx, "Hãy bật vị trí (GPS) trước", Toast.LENGTH_SHORT).show()
        else -> TrackingService.start(ctx, auto = false)
    }
}

@Composable
fun StatsGrid(s: DayStats, twoCols: Boolean, bg: androidx.compose.ui.graphics.Color) {
    val cells = listOf(
        Triple(Fmt.km(s.dist), "km", "Quãng đường"),
        Triple(Fmt.dur(s.durMs), "", "Thời gian"),
        Triple(String.format("%.0f", s.avgKmh), "km/h", "Trung bình"),
        Triple(String.format("%.0f", s.maxKmh), "km/h", "Tối đa"),
    )
    if (twoCols) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (v, u, l) -> StatCell(v, u, l, Modifier.weight(1f), bg, big = true) }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.forEach { (v, u, l) -> StatCell(v, u, l, Modifier.weight(1f), bg) }
        }
    }
}

@Composable
fun TodayScreen(wide: Boolean, trips: List<Trip>, onReplay: (ReplaySel) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { RideDb.get(ctx).dao() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    val dayStart = Fmt.dayStart(now)
    val points by remember(dayStart) { dao.observePointsBetween(dayStart, dayStart + Fmt.DAY) }
        .collectAsState(initial = emptyList())
    val rec by Tracker.state.collectAsState()
    val today = trips.filter { it.startTime in dayStart until dayStart + Fmt.DAY }.sortedBy { it.startTime }
    val stats = DayStats.of(today)
    val tracks = remember(points) {
        points.groupBy { it.tripId }.values.map { l -> l.map { GeoPoint(it.lat, it.lon) } }
    }
    val live = if (rec.recording) points.lastOrNull { it.tripId == rec.tripId }?.let { GeoPoint(it.lat, it.lon) } else null
    val tick = rememberResumeTick()
    val fallback by produceState<GeoPoint?>(null, tick) { value = lastKnown(ctx) ?: value }
    val replayToday = { onReplay(ReplaySel(today.map { it.id }, "Hôm nay")) }

    if (!wide) {
        Box(Modifier.fillMaxSize()) {
            RideMap(
                Modifier.fillMaxSize(), tracks, dot = live, fitKey = today.size,
                fallbackCenter = fallback, bottomPadFrac = 0.34,
            )
            TopFade()
            Column(Modifier.fillMaxWidth()) {
                TopBar { if (rec.recording) RecPill(rec.startTime, now) }
                PermissionCard(Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp)) {
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Hôm nay", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.text)
                            Spacer(Modifier.weight(1f))
                            Text("${Fmt.day(now)} · ${today.size} chuyến", fontSize = 12.sp, color = C.muted)
                        }
                        StatsGrid(stats, false, C.chrome)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RedButton("Phát lại hôm nay", R.drawable.ic_play, Modifier.weight(1f), enabled = today.isNotEmpty(), onClick = replayToday)
                            GhostButton(
                                if (rec.recording) "Dừng" else "Ghi",
                                if (rec.recording) R.drawable.ic_stop else R.drawable.ic_record,
                                iconTint = C.red,
                            ) { toggleRecording(ctx) }
                        }
                    }
                }
                Credit(Modifier.align(Alignment.End).padding(top = 3.dp, bottom = 3.dp, end = 2.dp))
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(340.dp).fillMaxHeight().background(C.chrome)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 44.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Hôm nay", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.text)
                Text("${Fmt.day(now)} · ${today.size} chuyến", fontSize = 12.5.sp, color = C.muted)
                if (rec.recording) RecPill(rec.startTime, now)
                PermissionCard()
                StatsGrid(stats, true, C.panel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RedButton("Phát lại hôm nay", R.drawable.ic_play, Modifier.weight(1f), enabled = today.isNotEmpty(), onClick = replayToday)
                    GhostButton(
                        if (rec.recording) "Dừng" else "Ghi",
                        if (rec.recording) R.drawable.ic_stop else R.drawable.ic_record,
                        iconTint = C.red,
                    ) { toggleRecording(ctx) }
                }
                Spacer(Modifier.height(4.dp))
                today.asReversed().forEach { t ->
                    TripRow(t, selected = false) { onReplay(ReplaySel(listOf(t.id), t.name)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                RideMap(Modifier.fillMaxSize(), tracks, dot = live, fitKey = today.size, fallbackCenter = fallback)
                TopFade()
                TopBar()
                Credit(Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
        }
    }
}

@Composable
fun TopFade() {
    Box(
        Modifier.fillMaxWidth().height(96.dp)
            .background(Brush.verticalGradient(listOf(C.chrome.copy(alpha = .85f), C.chrome.copy(alpha = 0f))))
    )
}

