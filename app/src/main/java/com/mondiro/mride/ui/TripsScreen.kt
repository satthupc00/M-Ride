package com.mondiro.mride.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mondiro.mride.R
import com.mondiro.mride.data.Geo
import com.mondiro.mride.data.Trip
import kotlin.math.max

data class ReplaySel(val ids: List<Long>, val title: String)

@Composable
fun TripThumb(preview: String, modifier: Modifier = Modifier) {
    val pts = remember(preview) { Geo.decode(preview) }
    Canvas(modifier.size(58.dp).clip(RoundedCornerShape(9.dp)).background(ThumbBg)) {
        if (pts.size < 2) return@Canvas
        val minLa = pts.minOf { it.first }; val maxLa = pts.maxOf { it.first }
        val minLo = pts.minOf { it.second }; val maxLo = pts.maxOf { it.second }
        val pad = 9.dp.toPx()
        val w = size.width - pad * 2; val h = size.height - pad * 2
        val span = max(max(maxLa - minLa, maxLo - minLo), 1e-6)
        val ox = pad + (w - (maxLo - minLo) / span * w).toFloat() / 2
        val oy = pad + (h - (maxLa - minLa) / span * h).toFloat() / 2
        fun p(i: Int) = Offset(
            ox + ((pts[i].second - minLo) / span * w).toFloat(),
            oy + ((maxLa - pts[i].first) / span * h).toFloat(),
        )
        val path = Path().apply {
            moveTo(p(0).x, p(0).y)
            for (i in 1 until pts.size) lineTo(p(i).x, p(i).y)
        }
        drawPath(path, C.red, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(C.text, 3.dp.toPx(), p(0))
    }
}

private val ThumbBg = androidx.compose.ui.graphics.Color(0xFF23262C)

@Composable
fun TripRow(t: Trip, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().pressable(shape) { onClick() }
            .background(if (selected) C.selected else C.panel, shape)
            .drawBehind { if (selected) drawRect(C.red, size = size.copy(width = 3.dp.toPx())) }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        TripThumb(t.preview)
        Column(Modifier.weight(1f)) {
            Text(
                if (t.active) "Đang ghi…" else t.name.ifBlank { "Chuyến đi" },
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Fmt.hm(t.startTime)} – ${Fmt.hm(t.endTime)} · ${Fmt.dur(t.endTime - t.startTime)}",
                fontSize = 12.sp, color = C.muted, maxLines = 1,
            )
        }
        Text(
            buildAnnotatedString {
                append(Fmt.km(t.distanceM))
                withStyle(SpanStyle(fontSize = 11.sp, color = C.muted, fontWeight = FontWeight.SemiBold)) { append(" km") }
            },
            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = C.text,
        )
    }
}

@Composable
fun TripsList(
    trips: List<Trip>,
    selectedIds: List<Long>,
    onTrip: (Trip) -> Unit,
    onDay: (List<Trip>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(trips) {
        trips.groupBy { Fmt.dayStart(it.startTime) }.toSortedMap(compareByDescending { it })
            .map { (day, list) -> day to list.sortedByDescending { it.startTime } }
    }
    Column(modifier.fillMaxSize().background(C.chrome)) {
        TopBar {
            Text("Hành trình", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.text)
        }
        if (trips.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Chưa có hành trình nào", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.text)
                Spacer(Modifier.height(6.dp))
                Text("Khi bạn đi xe, app sẽ tự ghi lại ở đây.", fontSize = 13.sp, color = C.muted)
            }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((day, list) in groups) {
                item(key = "d$day") {
                    val title = Fmt.day(day)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 0.dp, top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.Bold)) { append(title) }
                                append(" · ${Fmt.km(list.sumOf { it.distanceM })} km")
                            },
                            fontSize = 12.5.sp, color = C.muted, modifier = Modifier.weight(1f),
                        )
                        DayPlay { onDay(list.sortedBy { it.startTime }, title) }
                    }
                }
                items(list, key = { it.id }) { t ->
                    TripRow(t, selected = t.id in selectedIds && selectedIds.size == 1) { onTrip(t) }
                }
            }
        }
    }
}

@Composable
private fun DayPlay(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.pressable(shape) { onClick() }.border(1.dp, C.line, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(R.drawable.ic_play), null, Modifier.size(11.dp), tint = C.text)
        Text("Phát lại ngày", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = C.text)
    }
}

