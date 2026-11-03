package com.mondiro.mride.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.mondiro.mride.data.Geo
import com.mondiro.mride.data.Prefs
import com.mondiro.mride.data.RideDb
import com.mondiro.mride.data.TrackPoint
import com.mondiro.mride.data.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/** Đồng bộ hành trình lên mây. Tên dịch vụ chỉ nằm trong code, không hiện trên giao diện. */
object Sync {
    enum class Status { Off, Pending, Synced, Error }

    private const val TAG = "MRideSync"
    val status = MutableStateFlow(Status.Off)
    val email = MutableStateFlow<String?>(null)
    val lastSync = MutableStateFlow(0L)

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun available(): Boolean = ::app.isInitialized && FirebaseApp.getApps(app).isNotEmpty()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        if (!available()) return
        FirebaseAuth.getInstance().addAuthStateListener { a ->
            email.value = a.currentUser?.email
            request()
        }
    }

    fun request() {
        if (!available()) return
        scope.launch { mutex.withLock { run() } }
    }

    private fun col(uid: String) = FirebaseFirestore.getInstance()
        .collection("users").document(uid).collection("trips")

    private suspend fun run() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            status.value = Status.Off
            return
        }
        status.value = Status.Pending
        try {
            withTimeout(45_000) {
                val c = col(user.uid)
                val dao = RideDb.get(app).dao()
                for (t in dao.unsynced()) {
                    val pts = dao.points(t.id)
                    c.document(t.uuid).set(toDoc(t, pts)).await()
                    dao.markSynced(t.id)
                }
                val snap = c.get(Source.SERVER).await()
                val local = dao.allUuids().toHashSet()
                val deleted = Prefs.deletedUuids()
                for (d in snap.documents) {
                    if (d.id in local || d.id in deleted) continue
                    val parsed = fromDoc(d) ?: continue
                    val id = dao.insertTrip(parsed.first)
                    dao.insertPoints(parsed.second.map { it.copy(tripId = id) })
                }
            }
            status.value = Status.Synced
            lastSync.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "sync", e)
            status.value = Status.Error
        }
    }

    fun deleteRemote(uuid: String) {
        Prefs.addDeleted(uuid)
        if (!available()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        col(user.uid).document(uuid).delete()
    }

    private fun toDoc(t: Trip, pts: List<TrackPoint>): Map<String, Any> = mapOf(
        "start" to t.startTime,
        "end" to t.endTime,
        "dist" to t.distanceM,
        "max" to t.maxSpeedKmh,
        "name" to t.name,
        "count" to pts.size,
        "poly" to Geo.encode(pts.map { it.lat to it.lon }),
        "ts" to pts.joinToString(",") { ((it.time - t.startTime) / 1000).toString() },
        "sp" to pts.joinToString(",") { it.speedKmh.roundToInt().toString() },
        "v" to 1,
    )

    private fun fromDoc(d: DocumentSnapshot): Pair<Trip, List<TrackPoint>>? = try {
        val start = d.getLong("start") ?: 0L
        val ll = Geo.decode(d.getString("poly") ?: "")
        val ts = (d.getString("ts") ?: "").split(",").mapNotNull { it.toLongOrNull() }
        val sp = (d.getString("sp") ?: "").split(",").mapNotNull { it.toFloatOrNull() }
        val pts = ll.mapIndexed { i, (la, lo) ->
            TrackPoint(
                tripId = 0, time = start + (ts.getOrNull(i) ?: i.toLong()) * 1000,
                lat = la, lon = lo, speedKmh = sp.getOrNull(i) ?: 0f, accuracy = 0f,
            )
        }
        val trip = Trip(
            uuid = d.id,
            startTime = start,
            endTime = d.getLong("end") ?: start,
            distanceM = d.getDouble("dist") ?: 0.0,
            maxSpeedKmh = d.getDouble("max") ?: 0.0,
            pointCount = pts.size,
            active = false,
            synced = true,
            name = d.getString("name") ?: "",
            preview = Geo.preview(pts),
        )
        trip to pts
    } catch (e: Exception) {
        null
    }

    /** Đăng nhập Google. Trả về null nếu thành công, ngược lại là câu báo lỗi. */
    suspend fun signIn(activity: Activity): String? {
        if (!available()) return "Đồng bộ chưa được thiết lập"
        val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (resId == 0) return "Đồng bộ chưa được thiết lập"
        return try {
            val opt = GetSignInWithGoogleOption.Builder(activity.getString(resId)).build()
            val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
            val res = CredentialManager.create(activity).getCredential(activity, req)
            val cred = res.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                FirebaseAuth.getInstance()
                    .signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
                // Chuyến trên máy sẽ được đẩy lên tài khoản vừa đăng nhập
                null
            } else "Không đăng nhập được"
        } catch (e: GetCredentialCancellationException) {
            "Đã huỷ đăng nhập"
        } catch (e: Exception) {
            Log.w(TAG, "signIn", e)
            "Không đăng nhập được"
        }
    }

    suspend fun signOut(ctx: Context) {
        if (!available()) return
        FirebaseAuth.getInstance().signOut()
        try {
            CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        status.value = Status.Off
    }
}
