package com.mondiro.mride.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var pLat = 0L
        var pLon = 0L
        for ((la, lo) in points) {
            val iLat = Math.round(la * 1e5)
            val iLon = Math.round(lo * 1e5)
            enc(iLat - pLat, sb)
            enc(iLon - pLon, sb)
            pLat = iLat
            pLon = iLon
        }
        return sb.toString()
    }

    private fun enc(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else (v shl 1)
        while (x >= 0x20) {
            sb.append(((0x20L or (x and 0x1fL)) + 63).toInt().toChar())
            x = x shr 5
        }
        sb.append((x + 63).toInt().toChar())
    }

    fun decode(s: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0
        var lat = 0L
        var lon = 0L
        fun next(): Long {
            var r = 0L
            var sh = 0
            var b: Int
            do {
                b = s[i++].code - 63
                r = r or ((b and 0x1f).toLong() shl sh)
                sh += 5
            } while (b >= 0x20 && i < s.length)
            return if ((r and 1L) != 0L) (r shr 1).inv() else (r shr 1)
        }
        try {
            while (i < s.length) {
                lat += next()
                if (i >= s.length) break
                lon += next()
                out.add(lat / 1e5 to lon / 1e5)
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Đường thu nhỏ (tối đa 48 điểm) cho hình thu nhỏ trong danh sách. */
    fun preview(pts: List<TrackPoint>): String {
        if (pts.isEmpty()) return ""
        val n = 48
        val pick = if (pts.size <= n) pts else (0 until n).map { pts[it * (pts.size - 1) / (n - 1)] }
        return encode(pick.map { it.lat to it.lon })
    }

    fun totalDistance(pts: List<TrackPoint>): Double {
        var d = 0.0
        for (i in 1 until pts.size) d += dist(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
        return d
    }

    /** Tốc độ tối đa bỏ qua vài điểm nhiễu GPS (lấy phân vị 98%). */
    fun maxSpeed(pts: List<TrackPoint>): Double {
        if (pts.isEmpty()) return 0.0
        val s = pts.map { it.speedKmh.toDouble() }.filter { it < 200 }.sorted()
        if (s.isEmpty()) return 0.0
        return s[((s.size - 1) * 0.98).toInt()]
    }
}
