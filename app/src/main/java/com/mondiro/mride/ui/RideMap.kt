package com.mondiro.mride.ui

import android.content.res.Resources
import android.graphics.PointF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mondiro.mride.data.Prefs
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.RotateGestureDetector
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Góc xoay hiện tại của bản đồ (cập nhật liên tục, không gây vẽ lại giao diện). */
class MapCam { var bearing: Double = 0.0 }

/** Toạ độ đơn giản dùng chung trong app. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Bản đồ vector OpenFreeMap (dữ liệu OpenStreetMap), miễn phí, không cần khoá. */
object MapStyles {
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
    const val COLOR = "https://tiles.openfreemap.org/styles/liberty"
    const val FALLBACK = "https://tiles.openfreemap.org/styles/bright"
}

private val density get() = Resources.getSystem().displayMetrics.density
private const val RED = "#E5383B"
private const val FADED = "#6A3538"

private fun emptyFc(): FeatureCollection = FeatureCollection.fromFeatures(ArrayList<Feature>())

private fun lineOf(pts: List<GeoPoint>) =
    LineString.fromLngLats(pts.map { Point.fromLngLat(it.longitude, it.latitude) })

private fun pointFc(p: GeoPoint?, label: String? = null): FeatureCollection =
    if (p == null) emptyFc()
    else FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).also {
        if (label != null) it.addStringProperty("label", label)
    }))

private fun addRideLayers(s: Style) {
    for (id in listOf("tracks", "progress", "start", "dot")) s.addSource(GeoJsonSource(id, emptyFc()))
    s.addLayer(
        LineLayer("tracks-glow", "tracks").withProperties(
            PropertyFactory.lineColor(RED), PropertyFactory.lineWidth(11f), PropertyFactory.lineOpacity(0.22f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    s.addLayer(
        LineLayer("tracks-line", "tracks").withProperties(
            PropertyFactory.lineColor(RED), PropertyFactory.lineWidth(4.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    s.addLayer(
        LineLayer("progress-line", "progress").withProperties(
            PropertyFactory.lineColor(RED), PropertyFactory.lineWidth(4.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    s.addLayer(
        CircleLayer("start-c", "start").withProperties(
            PropertyFactory.circleRadius(5.5f), PropertyFactory.circleColor("#1F2126"),
            PropertyFactory.circleStrokeColor("#E8E9EC"), PropertyFactory.circleStrokeWidth(3f),
        )
    )
    s.addLayer(
        CircleLayer("dot-halo", "dot").withProperties(
            PropertyFactory.circleRadius(15f), PropertyFactory.circleColor(RED), PropertyFactory.circleOpacity(0.25f),
        )
    )
    s.addLayer(
        CircleLayer("dot-c", "dot").withProperties(
            PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(RED),
            PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f),
        )
    )
    s.addLayer(
        SymbolLayer("dot-label", "dot").withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(12.5f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#1F2126"),
            PropertyFactory.textHaloWidth(2.4f),
            PropertyFactory.textOffset(arrayOf(0f, -2.0f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
    )
}

/** Tên địa điểm ưu tiên tiếng Việt (tên gốc OSM ở VN đã là tiếng Việt có dấu). */
private fun vietnameseLabels(s: Style) {
    val nameLayers = setOf(
        "place", "transportation_name", "water_name", "poi", "aerodrome_label",
        "mountain_peak", "park", "waterway", "aeroway",
    )
    val viName = Expression.coalesce(
        Expression.get("name:vi"), Expression.get("name"), Expression.get("name:latin"), Expression.get("name_en"),
    )
    for (l in s.layers) {
        if (l !is SymbolLayer) continue
        val id = l.id
        if (id.contains("shield") || id.contains("housenumber") || id.contains("ref")) continue
        if (l.sourceLayer !in nameLayers) continue
        try {
            l.setProperties(PropertyFactory.textField(viName))
        } catch (_: Exception) {
        }
    }
}

/** Kiểu tối gốc gần như đơn sắc: tô màu sông, cây xanh, đường lớn để nhìn xa vẫn phân biệt được. */
private fun tintDark(s: Style) {
    for (l in s.layers) {
        val id = l.id
        try {
            when (l) {
                is FillLayer -> when (l.sourceLayer) {
                    "water" -> l.setProperties(PropertyFactory.fillColor("#1E4A63"))
                    "park" -> l.setProperties(PropertyFactory.fillColor("#1E3B2A"))
                    "landcover" -> l.setProperties(PropertyFactory.fillColor("#1D3527"))
                    "building" -> l.setProperties(PropertyFactory.fillColor("#2E3138"))
                }
                is LineLayer -> when {
                    l.sourceLayer == "waterway" -> l.setProperties(PropertyFactory.lineColor("#1E4A63"))
                    l.sourceLayer == "transportation" && !id.contains("casing") && !id.contains("rail") -> when {
                        id.contains("motorway") || id.contains("trunk") ->
                            l.setProperties(PropertyFactory.lineColor("#C27B32"))
                        id.contains("primary") ->
                            l.setProperties(PropertyFactory.lineColor("#9C7A45"))
                        id.contains("secondary") || id.contains("tertiary") ->
                            l.setProperties(PropertyFactory.lineColor("#6E6A5E"))
                    }
                }
                is SymbolLayer -> if (l.sourceLayer == "water_name") {
                    l.setProperties(PropertyFactory.textColor("#7FB3D1"))
                }
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * @param bottomPadFrac phần chiều cao bản đồ bị bảng phía dưới che, để căn đường đi vào phần còn thấy.
 */
@Composable
fun RideMap(
    modifier: Modifier,
    tracks: List<List<GeoPoint>>,
    faded: Boolean = false,
    progress: List<GeoPoint>? = null,
    dot: GeoPoint? = null,
    fitKey: Any? = null,
    fallbackCenter: GeoPoint? = null,
    bottomPadFrac: Double = 0.0,
    follow: Boolean = false,
    bearing: Double? = null,
    onUserGesture: (() -> Unit)? = null,
    dotLabel: String? = null,
    onRouteTap: ((Int) -> Unit)? = null,
    onUserRotate: (() -> Unit)? = null,
    cam: MapCam? = null,
    northTick: Int = 0,
) {
    val ctx = LocalContext.current
    val gestureCb by rememberUpdatedState(onUserGesture)
    val tapCb by rememberUpdatedState(onRouteTap)
    val rotateCb by rememberUpdatedState(onUserRotate)
    val camRef by rememberUpdatedState(cam)
    val tracksRef by rememberUpdatedState(tracks)
    var followAnimUntil by remember { mutableLongStateOf(0L) }
    val dark by Prefs.mapDark.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(ctx)
        val opts = MapLibreMapOptions.createFromAttributes(ctx)
            .foregroundLoadColor(0xFF1F2126.toInt())
            .textureMode(true)
        MapView(ctx, opts).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle) {
        var started = false
        var resumed = false
        fun sync(state: Lifecycle.State) {
            val wantStart = state.isAtLeast(Lifecycle.State.STARTED)
            val wantResume = state.isAtLeast(Lifecycle.State.RESUMED)
            if (wantStart && !started) { mapView.onStart(); started = true }
            if (wantResume && !resumed) { mapView.onResume(); resumed = true }
            if (!wantResume && resumed) { mapView.onPause(); resumed = false }
            if (!wantStart && started) { mapView.onStop(); started = false }
        }
        val obs = LifecycleEventObserver { _, _ -> sync(lifecycle.currentState) }
        lifecycle.addObserver(obs)
        sync(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(obs)
            sync(Lifecycle.State.CREATED)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.setMaxZoomPreference(19.0)
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(10.7769, 106.7009), 13.0))
            // Chỉ kéo bằng 1 ngón mới tắt bám xe; zoom 2 ngón vẫn giữ bám xe
            m.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                override fun onMoveBegin(detector: MoveGestureDetector) {
                    if (detector.pointersCount <= 1) gestureCb?.invoke()
                }
                override fun onMove(detector: MoveGestureDetector) {}
                override fun onMoveEnd(detector: MoveGestureDetector) {}
            })
            // Xoay tự do bằng 2 ngón → tắt chế độ xoay theo xe
            m.addOnRotateListener(object : MapLibreMap.OnRotateListener {
                override fun onRotateBegin(detector: RotateGestureDetector) { rotateCb?.invoke() }
                override fun onRotate(detector: RotateGestureDetector) {}
                override fun onRotateEnd(detector: RotateGestureDetector) {}
            })
            m.addOnCameraMoveListener { camRef?.bearing = m.cameraPosition.bearing }
            // Chạm vào đường đi → báo vị trí điểm gần nhất để tua tới đó
            m.addOnMapClickListener { ll ->
                val cb = tapCb ?: return@addOnMapClickListener false
                val pts = tracksRef.firstOrNull() ?: return@addOnMapClickListener false
                val tap = m.projection.toScreenLocation(ll)
                var best = -1
                var bd = Float.MAX_VALUE
                pts.forEachIndexed { k, g ->
                    val q = m.projection.toScreenLocation(LatLng(g.latitude, g.longitude))
                    val dd = (q.x - tap.x) * (q.x - tap.x) + (q.y - tap.y) * (q.y - tap.y)
                    if (dd < bd) { bd = dd; best = k }
                }
                val lim = 30 * density
                if (best >= 0 && bd < lim * lim) { cb(best); true } else false
            }
            map = m
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        var triedFallback = false
        val onLoaded = Style.OnStyleLoaded { s ->
            vietnameseLabels(s)
            if (dark) tintDark(s)
            addRideLayers(s)
            style = s
        }
        mapView.addOnDidFailLoadingMapListener {
            if (!triedFallback) {
                triedFallback = true
                m.setStyle(Style.Builder().fromUri(MapStyles.FALLBACK), onLoaded)
            }
        }
        m.setStyle(Style.Builder().fromUri(if (dark) MapStyles.DARK else MapStyles.COLOR), onLoaded)
    }

    LaunchedEffect(style, tracks, faded) {
        val s = style ?: return@LaunchedEffect
        val feats = tracks.filter { it.size >= 2 }.map { Feature.fromGeometry(lineOf(it)) }
        s.getSourceAs<GeoJsonSource>("tracks")?.setGeoJson(FeatureCollection.fromFeatures(feats))
        s.getLayerAs<LineLayer>("tracks-line")?.setProperties(PropertyFactory.lineColor(if (faded) FADED else RED))
        s.getLayerAs<LineLayer>("tracks-glow")?.setProperties(
            PropertyFactory.visibility(if (faded) Property.NONE else Property.VISIBLE)
        )
        s.getSourceAs<GeoJsonSource>("start")?.setGeoJson(pointFc(tracks.firstOrNull()?.firstOrNull()))
    }

    SideEffect {
        val s = style ?: return@SideEffect
        val p = progress
        s.getSourceAs<GeoJsonSource>("progress")?.setGeoJson(
            if (p != null && p.size >= 2) FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(lineOf(p))))
            else emptyFc()
        )
        s.getSourceAs<GeoJsonSource>("dot")?.setGeoJson(pointFc(dot, dotLabel))
        // Bám theo xe: camera chạy theo chấm đỏ mỗi khung hình
        val m = map
        if (follow && m != null && dot != null && System.currentTimeMillis() > followAnimUntil) {
            val target = LatLng(dot.latitude, dot.longitude)
            if (bearing != null) {
                // Xoay bản đồ theo hướng xe: đầu xe luôn hướng lên trên
                m.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder(m.cameraPosition).target(target).bearing(bearing).build()
                    )
                )
            } else {
                m.moveCamera(CameraUpdateFactory.newLatLng(target))
            }
        }
    }

    // Xoay 2 ngón chỉ bật ở màn phát lại
    LaunchedEffect(map, onUserRotate != null) {
        map?.uiSettings?.isRotateGesturesEnabled = onUserRotate != null
    }

    // Về hướng Bắc khi không bám xe (khi bám xe thì màn phát lại tự xoay dần qua tham số bearing)
    LaunchedEffect(map, northTick) {
        val m = map ?: return@LaunchedEffect
        if (northTick > 0 && !follow) m.animateCamera(CameraUpdateFactory.bearingTo(0.0), 400)
    }

    // Bật bám xe: chừa chỗ cho bảng điều khiển phía dưới rồi phóng to mượt vào xe
    LaunchedEffect(map, follow, dot != null) {
        val m = map ?: return@LaunchedEffect
        if (follow) {
            val d = dot ?: return@LaunchedEffect
            val h = mapView.height.toDouble()
            val top = 80.0 * density
            val bottom = h * bottomPadFrac + 24.0 * density
            m.moveCamera(CameraUpdateFactory.paddingTo(0.0, top, 0.0, bottom))
            // zoom bằng 2 ngón sẽ phóng to quanh xe
            m.uiSettings.focalPoint = PointF(mapView.width / 2f, (top + (h - top - bottom) / 2).toFloat())
            followAnimUntil = System.currentTimeMillis() + 800
            val z = m.cameraPosition.zoom.let { if (it < 14) 16.5 else it }
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(d.latitude, d.longitude), z), 750)
        } else {
            // tắt bám xe: giữ nguyên khung nhìn hiện tại, không lùi ra cả tuyến
            m.uiSettings.focalPoint = null
        }
    }

    LaunchedEffect(map, fitKey, fallbackCenter != null) {
        val m = map ?: return@LaunchedEffect
        if (follow) return@LaunchedEffect
        val all = tracks.flatten()
        mapView.post {
            val h = mapView.height
            val side = (36 * density).toInt()
            val top = (80 * density).toInt()
            val bottom = (h * bottomPadFrac).toInt() + (24 * density).toInt()
            try {
                if (all.size >= 2) {
                    val b = LatLngBounds.Builder()
                    all.forEach { b.include(LatLng(it.latitude, it.longitude)) }
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), side, top, side, bottom))
                    if (m.cameraPosition.zoom > 17) m.moveCamera(CameraUpdateFactory.zoomTo(17.0))
                } else if (all.size == 1) {
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(all[0].latitude, all[0].longitude), 16.0))
                } else if (fallbackCenter != null) {
                    m.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(fallbackCenter.latitude, fallbackCenter.longitude), 15.0)
                    )
                }
            } catch (_: Exception) {
                // các điểm trùng nhau → không tạo được khung, giữ camera hiện tại
                all.firstOrNull()?.let {
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.0))
                }
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Text(
            "© OpenFreeMap © OpenStreetMap",
            fontSize = 9.sp, color = C.muted,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 46.dp),
        )
    }
}
