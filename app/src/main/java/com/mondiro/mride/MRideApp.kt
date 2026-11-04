package com.mondiro.mride

import android.app.Application
import com.mondiro.mride.data.Prefs
import com.mondiro.mride.sync.Sync
import com.mondiro.mride.tracking.AutoDetect
import com.mondiro.mride.tracking.Notifs
import com.mondiro.mride.tracking.TripFinisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MRideApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifs.createChannels(this)
        MapLibre.getInstance(this)
        Sync.init(this)
        AutoDetect.register(this)
        scope.launch { TripFinisher.finalizeStale(this@MRideApp) }
    }
}
