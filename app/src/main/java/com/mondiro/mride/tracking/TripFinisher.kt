package com.mondiro.mride.tracking

import android.content.Context
import android.location.Geocoder
import com.mondiro.mride.data.Geo
import com.mondiro.mride.data.RideDb
import com.mondiro.mride.data.TrackPoint
import com.mondiro.mride.sync.Sync
import java.util.Calendar
import java.util.Locale

object TripFinisher {
    private const val MIN_DIST = 300.0

    /** Chốt một chuyến: tính lại số liệu, đặt tên, xoá nếu quá ngắn. Trả về true nếu giữ lại. */
    suspend fun finalize(ctx: Context, tripId: Long, trimAfter: Long?): Boolean {
        val dao = RideDb.get(ctx).dao()
        val t = dao.trip(tripId) ?: return false
        if (trimAfter != null) dao.trimPoints(tripId, trimAfter)
        val pts = dao.points(tripId)
        val dist = Geo.totalDistance(pts)
        if (pts.size < 5 || dist < MIN_DIST) {
            dao.deleteTrip(tripId)
            return false
        }
        dao.updateTrip(
            t.copy(
                startTime = pts.first().time,
                endTime = pts.last().time,
                distanceM = dist,
                maxSpeedKmh = Geo.maxSpeed(pts),
                pointCount = pts.size,
                active = false,
                synced = false,
                name = name(ctx, pts.first(), pts.last()),
                preview = Geo.preview(pts),
            )
        )
        Sync.request()
        return true
    }

    suspend fun finalizeStale(ctx: Context) {
        val dao = RideDb.get(ctx).dao()
        val t = dao.activeTrip() ?: return
        if (!Tracker.state.value.recording && System.currentTimeMillis() - t.endTime > 15 * 60_000) {
            finalize(ctx, t.id, null)
        }
    }

    private fun name(ctx: Context, a: TrackPoint, b: TrackPoint): String {
        try {
            if (Geocoder.isPresent()) {
                val g = Geocoder(ctx, Locale("vi", "VN"))
                val pa = place(g, a)
                val pb = place(g, b)
                if (pa != null && pb != null) return if (pa == pb) "Quanh $pa" else "$pa → $pb"
            }
        } catch (_: Exception) {
        }
        val h = Calendar.getInstance().apply { timeInMillis = a.time }.get(Calendar.HOUR_OF_DAY)
        val part = when (h) {
            in 4..10 -> "sáng"
            in 11..13 -> "trưa"
            in 14..17 -> "chiều"
            else -> "tối"
        }
        return "Chuyến $part"
    }

    @Suppress("DEPRECATION")
    private fun place(g: Geocoder, p: TrackPoint): String? =
        g.getFromLocation(p.lat, p.lon, 1)?.firstOrNull()?.let {
            it.subAdminArea ?: it.locality ?: it.subLocality ?: it.thoroughfare
        }
}
