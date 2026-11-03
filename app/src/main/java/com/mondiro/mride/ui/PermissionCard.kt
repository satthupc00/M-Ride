package com.mondiro.mride.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.mondiro.mride.data.Prefs
import com.mondiro.mride.tracking.AutoDetect
import com.mondiro.mride.tracking.Perms

/** Tăng mỗi khi app quay lại màn hình, để kiểm tra lại quyền / GPS. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return tick
}

private data class Step(val title: String, val desc: String, val button: String, val action: () -> Unit)

@SuppressLint("BatteryLife")
@Composable
fun PermissionCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var extra by remember { mutableIntStateOf(0) }
    val tick = rememberResumeTick() + extra
    val auto by Prefs.autoRecord.collectAsState()

    val multi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        AutoDetect.register(ctx); extra++
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }
        AutoDetect.register(ctx); extra++
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { extra++ }

    fun enableGps() {
        val req = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build())
            .setAlwaysShow(true).build()
        LocationServices.getSettingsClient(ctx).checkLocationSettings(req)
            .addOnSuccessListener { extra++ }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    settingsLauncher.launch(IntentSenderRequest.Builder(e.resolution).build())
                } else ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
    }

    val step: Step? = remember(tick, auto) {
        when {
            !Perms.hasFine(ctx) -> Step(
                "Cho phép truy cập vị trí",
                "M Ride cần vị trí để vẽ lại đường bạn đi.",
                "Cho phép",
            ) {
                val list = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                )
                if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
                multi.launch(list.toTypedArray())
            }
            !Perms.locationOn(ctx) -> Step(
                "Vị trí (GPS) đang tắt",
                "Bật vị trí để app ghi được hành trình.",
                "Bật vị trí",
            ) { enableGps() }
            auto && !Perms.hasActivity(ctx) -> Step(
                "Cho phép nhận diện hoạt động",
                "Để app biết lúc bạn đang đi xe và tự bắt đầu ghi.",
                "Cho phép",
            ) { single.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
            auto && !Perms.hasBackground(ctx) -> Step(
                "Chọn \"Luôn cho phép\" vị trí",
                "Để app tự ghi cả khi màn hình đang tắt. Vào Quyền → Vị trí → Luôn cho phép.",
                "Mở cài đặt",
            ) { single.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            auto && !Perms.ignoringBattery(ctx) -> Step(
                "Cho phép chạy nền",
                "Để máy không tắt M Ride giữa chuyến đi.",
                "Cho phép",
            ) {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                )
            }
            !Perms.hasNotif(ctx) && Build.VERSION.SDK_INT >= 33 -> Step(
                "Cho phép thông báo",
                "Để thấy app đang ghi và dừng nhanh từ thanh thông báo.",
                "Cho phép",
            ) { single.launch(Manifest.permission.POST_NOTIFICATIONS) }
            else -> null
        }
    }

    if (step != null) {
        Card(modifier) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(step.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = C.text)
                Text(step.desc, fontSize = 12.5.sp, color = C.muted)
                RedButton(step.button, null, onClick = step.action)
            }
        }
    }
}
