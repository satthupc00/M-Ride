package com.mondiro.mride.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mondiro.mride.R
import com.mondiro.mride.data.RideDb
import com.mondiro.mride.update.Updater

enum class Tab(val label: String, val icon: Int) {
    Today("Hôm nay", R.drawable.ic_pin),
    Trips("Hành trình", R.drawable.ic_route),
    Settings("Cài đặt", R.drawable.ic_settings),
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val dao = remember { RideDb.get(ctx).dao() }
    val trips by remember { dao.observeTrips() }.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var replay by remember { mutableStateOf<ReplaySel?>(null) }

    LaunchedEffect(Unit) { Updater.check(showIfNew = true) }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.chrome)) {
        val wide = maxWidth >= 600.dp
        BackHandler(enabled = replay != null || tab != Tab.Today) {
            if (replay != null && !(wide && tab == Tab.Trips)) replay = null else { replay = null; tab = Tab.Today }
        }
        val openReplay: (ReplaySel) -> Unit = { replay = it; if (wide) tab = Tab.Trips }

        if (!wide) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    val r = replay
                    when {
                        r != null -> ReplayView(r, compact = true, onBack = { replay = null }, onDeleted = { replay = null })
                        tab == Tab.Today -> TodayScreen(false, trips, openReplay)
                        tab == Tab.Trips -> TripsList(
                            trips, emptyList(),
                            onTrip = { replay = ReplaySel(listOf(it.id), it.name) },
                            onDay = { list, title -> replay = ReplaySel(list.map { it.id }, title) },
                        )
                        else -> SettingsScreen(false)
                    }
                    if (r == null && tab == Tab.Trips) Credit(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(6.dp))
                }
                NavigationBar(containerColor = C.chrome, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        val on = tab == t && (replay == null || t == Tab.Trips) || (replay != null && t == Tab.Trips)
                        NavigationBarItem(
                            selected = on,
                            onClick = { tab = t; replay = null },
                            icon = { Icon(painterResource(t.icon), t.label, Modifier.size(22.dp)) },
                            label = { Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = C.red, selectedTextColor = C.text,
                                indicatorColor = C.selected, unselectedIconColor = C.muted, unselectedTextColor = C.muted,
                            ),
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = C.chrome, modifier = Modifier.width(84.dp)) {
                    Spacer(Modifier.size(36.dp))
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t; if (t != Tab.Trips) replay = null },
                            icon = { Icon(painterResource(t.icon), t.label, Modifier.size(22.dp)) },
                            label = { Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = C.red, selectedTextColor = C.text,
                                indicatorColor = C.selected, unselectedIconColor = C.muted, unselectedTextColor = C.muted,
                            ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.padding(bottom = 16.dp)) { AppName(13) }
                }
                VerticalDivider(color = C.line)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (tab) {
                        Tab.Today -> TodayScreen(true, trips, openReplay)
                        Tab.Trips -> {
                            val sel = replay ?: trips.firstOrNull()?.let { ReplaySel(listOf(it.id), it.name) }
                            Row(Modifier.fillMaxSize()) {
                                TripsList(
                                    trips, sel?.ids ?: emptyList(),
                                    onTrip = { replay = ReplaySel(listOf(it.id), it.name) },
                                    onDay = { list, title -> replay = ReplaySel(list.map { it.id }, title) },
                                    modifier = Modifier.width(340.dp),
                                )
                                VerticalDivider(color = C.line)
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (sel != null) {
                                        ReplayView(sel, compact = false, onBack = null, onDeleted = { replay = null })
                                    } else {
                                        Box(Modifier.fillMaxSize().background(C.chrome)) {
                                            Credit(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(10.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Tab.Settings -> Box(Modifier.fillMaxSize()) {
                            SettingsScreen(true)
                        }
                    }
                }
            }
        }
        UpdateDialog()
    }
}
