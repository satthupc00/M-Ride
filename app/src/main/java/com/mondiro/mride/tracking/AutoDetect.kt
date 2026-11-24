package com.mondiro.mride.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.mondiro.mride.data.Prefs

/** Nhận diện "đang đi xe" bằng cảm biến chuyển động của Android để tự bắt đầu ghi. */
object AutoDetect {
    private const val TAG = "MRideAuto"
    const val ACTION = "com.mondiro.mride.TRANSITION"

    private fun pi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7,
        Intent(ctx, TransitionReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        if (!Prefs.autoRecord.value || !Perms.hasActivity(ctx)) return
        val types = listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE)
        val list = types.flatMap { type ->
            listOf(
                ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                ActivityTransition.ACTIVITY_TRANSITION_EXIT,
            ).map { tr ->
                ActivityTransition.Builder().setActivityType(type).setActivityTransition(tr).build()
            }
        }
        try {
            ActivityRecognition.getClient(ctx)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(list), pi(ctx))
                .addOnFailureListener { Log.w(TAG, "register failed", it) }
        } catch (e: Exception) {
            Log.w(TAG, "register", e)
        }
        registerUpdates(ctx)
    }

    // Kiểm tra định kỳ (~90 giây/lần, chỉ dùng cảm biến chuyển động, không bật GPS):
    // bắt được trường hợp vẫn đang trên xe nhưng không có tín hiệu "vừa lên xe" (vd sau khi bấm Dừng / tự dừng lúc nghỉ).
    private fun piUpdates(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 8,
        Intent(ctx, ActivityUpdateReceiver::class.java).setAction(ACTION + ".UPDATES"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun registerUpdates(ctx: Context) {
        try {
            ActivityRecognition.getClient(ctx).requestActivityUpdates(90_000L, piUpdates(ctx))
                .addOnFailureListener { Log.w(TAG, "updates failed", it) }
        } catch (e: Exception) {
            Log.w(TAG, "updates", e)
        }
    }

    /** Có nên tự bắt đầu ghi không (đang bật tự ghi, chưa ghi, đã qua 3 phút kể từ lúc tự bấm Dừng). */
    fun shouldAutoStart(): Boolean =
        Prefs.autoRecord.value && !Tracker.state.value.recording &&
            System.currentTimeMillis() - Prefs.manualStopAt > 3 * 60_000L

    @SuppressLint("MissingPermission")
    fun unregister(ctx: Context) {
        try {
            ActivityRecognition.getClient(ctx).removeActivityUpdates(piUpdates(ctx))
        } catch (e: Exception) {
            Log.w(TAG, "unregister updates", e)
        }
        try {
            ActivityRecognition.getClient(ctx).removeActivityTransitionUpdates(pi(ctx))
        } catch (e: Exception) {
            Log.w(TAG, "unregister", e)
        }
    }
}

class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val res = ActivityTransitionResult.extractResult(intent) ?: return
        if (!Prefs.autoRecord.value) return
        val enter = res.transitionEvents.any {
            it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                (it.activityType == DetectedActivity.IN_VEHICLE || it.activityType == DetectedActivity.ON_BICYCLE)
        }
        if (!enter || Tracker.state.value.recording) return
        if (!Perms.locationOn(ctx)) {
            Notifs.gpsOff(ctx)
            return
        }
        TrackingService.start(ctx, auto = true)
    }
}

class ActivityUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val a = ActivityRecognitionResult.extractResult(intent)?.mostProbableActivity ?: return
        val riding = (a.type == DetectedActivity.IN_VEHICLE || a.type == DetectedActivity.ON_BICYCLE) && a.confidence >= 70
        if (!riding || !AutoDetect.shouldAutoStart()) return
        if (!Perms.locationOn(ctx)) {
            Notifs.gpsOff(ctx)
            return
        }
        TrackingService.start(ctx, auto = true)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AutoDetect.register(ctx)
    }
}
