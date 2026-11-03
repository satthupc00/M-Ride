package com.mondiro.mride.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mondiro.mride.data.Geo
import com.mondiro.mride.data.Prefs
import com.mondiro.mride.data.RideDb
import com.mondiro.mride.data.TrackPoint
import com.mondiro.mride.data.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max

class TrackingService : Service() {

    companion object {
        const val ACTION_START = "com.mondiro.mride.START"
        const val ACTION_STOP = "com.mondiro.mride.STOP"
        const val EXTRA_AUTO = "auto"
        private const val TAG = "MRideTrack"

        fun start(ctx: Context, auto: Boolean) {
            val i = Intent(ctx, TrackingService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_AUTO, auto)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                Log.w(TAG, "Không mở được dịch vụ ghi", e)
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, TrackingService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                Log.w(TAG, "stop", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private lateinit var fused: FusedLocationProviderClient
    private val dao by lazy { RideDb.get(this).dao() }

    private var started = false
    private var trip: Trip? = null
    private var last: TrackPoint? = null
    private var anchorLat = 0.0
    private var anchorLon = 0.0
    private var anchorSet = false
    private var lastMove = 0L
    private var lastNotif = 0L
    private var watchdog: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            val locs = r.locations.toList()
            scope.launch { for (l in locs) onLocation(l) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (!started) {
                stopSelf()
            } else {
                scope.launch { finish(manual = true) }
            }
            return START_NOT_STICKY
        }
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            val auto = intent?.getBooleanExtra(EXTRA_AUTO, true) ?: true
            scope.launch { begin(auto) }
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        return try {
            ServiceCompat.startForeground(
                this, Notifs.TRACK_ID,
                Notifs.tracking(this, 0.0, System.currentTimeMillis()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            Perms.hasFine(this)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground", e)
            false
        }
    }

    private suspend fun begin(auto: Boolean) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val existing = dao.activeTrip()
            val t = existing ?: Trip(uuid = UUID.randomUUID().toString(), startTime = now, endTime = now)
                .let { it.copy(id = dao.insertTrip(it)) }
            trip = t
            last = existing?.let { dao.points(it.id).lastOrNull() }
            lastMove = now
            anchorSet = false
            Tracker.state.value = Tracker.State(true, t.id, t.startTime, t.distanceM, 0f, auto)
        }
        withContext(Dispatchers.Main) { requestUpdates() }
        watchdog = scope.launch {
            while (isActive) {
                delay(15_000)
                val limit = Prefs.stopMinutes.value * 60_000L
                if (System.currentTimeMillis() - lastMove > limit) {
                    finish(manual = false)
                    break
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        val pr = if (Prefs.highAccuracy.value) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val req = LocationRequest.Builder(pr, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(2f)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "Thiếu quyền vị trí", e)
            scope.launch { finish(manual = true) }
        }
    }

    private suspend fun onLocation(l: Location) = mutex.withLock {
        val t = trip ?: return@withLock
        if (l.hasAccuracy() && l.accuracy > 35f) return@withLock
        val prev = last
        val d = if (prev != null) Geo.dist(prev.lat, prev.lon, l.latitude, l.longitude) else 0.0
        val dt = if (prev != null) (l.time - prev.time) / 1000.0 else 0.0
        if (prev != null && dt <= 0) return@withLock
        if (prev != null && d / dt > 70) return@withLock // nhảy điểm bất thường (> 250 km/h)
        val kmh = when {
            l.hasSpeed() -> l.speed * 3.6f
            dt > 0 -> (d / dt * 3.6).toFloat()
            else -> 0f
        }

        val moved = !anchorSet ||
            Geo.dist(anchorLat, anchorLon, l.latitude, l.longitude) > 40 || kmh > 8f
        if (moved) {
            anchorLat = l.latitude
            anchorLon = l.longitude
            anchorSet = true
            lastMove = System.currentTimeMillis()
        }

        if (prev != null && (d < 3 || (kmh < 3f && d < 15))) return@withLock

        val p = TrackPoint(
            tripId = t.id, time = l.time, lat = l.latitude, lon = l.longitude,
            speedKmh = kmh, accuracy = if (l.hasAccuracy()) l.accuracy else 0f,
        )
        dao.insertPoint(p)
        last = p
        val nt = t.copy(
            endTime = l.time,
            distanceM = t.distanceM + d,
            maxSpeedKmh = if (kmh < 200) max(t.maxSpeedKmh, kmh.toDouble()) else t.maxSpeedKmh,
            pointCount = t.pointCount + 1,
        )
        dao.updateTrip(nt)
        trip = nt
        Tracker.state.value = Tracker.state.value.copy(distanceM = nt.distanceM, speedKmh = kmh)
        val now = System.currentTimeMillis()
        if (now - lastNotif > 10_000) {
            lastNotif = now
            Notifs.update(this@TrackingService, nt.distanceM, nt.startTime)
        }
    }

    private suspend fun finish(manual: Boolean) {
        withContext(NonCancellable) {
            val t: Trip? = mutex.withLock {
                watchdog?.cancel()
                fused.removeLocationUpdates(callback)
                val cur = trip
                trip = null
                cur
            }
            if (t != null) {
                try {
                    TripFinisher.finalize(this@TrackingService, t.id, if (manual) null else lastMove)
                } catch (e: Exception) {
                    Log.w(TAG, "finalize", e)
                }
            }
            Tracker.state.value = Tracker.State()
            withContext(Dispatchers.Main) {
                ServiceCompat.stopForeground(this@TrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }
}
