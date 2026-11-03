package com.mondiro.mride.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mondiro.mride.data.Prefs
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.max

object Tiles {
    private fun urls(path: String) = arrayOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/$path/" }.toTypedArray()
    val dark = XYTileSource("CartoDark", 1, 20, 256, "@2x.png", urls("dark_all"), "© OpenStreetMap contributors © CARTO")
    val light = XYTileSource("CartoVoyager", 1, 20, 256, "@2x.png", urls("rastertiles/voyager"), "© OpenStreetMap contributors © CARTO")
}

private val density get() = Resources.getSystem().displayMetrics.density

/** Chấm tròn vẽ trên bản đồ (vị trí hiện tại / điểm xuất phát). */
class DotOverlay(private val fill: Int, private val ring: Int, private val radiusDp: Float, private val halo: Int) : Overlay() {
    var pos: GeoPoint? = null
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ring; style = Paint.Style.STROKE; strokeWidth = 3 * density }
    private val pHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = halo }

    override fun draw(c: Canvas, pj: Projection) {
        val g = pos ?: return
        val pt = pj.toPixels(g, null)
        val r = radiusDp * density
        if (halo != 0) c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r * 2.1f, pHalo)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, pFill)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, pRing)
    }
}

private fun line(color: Int, widthDp: Float) = Polyline().apply {
    outlinePaint.color = color
    outlinePaint.strokeWidth = widthDp * density
    outlinePaint.strokeCap = Paint.Cap.ROUND
    outlinePaint.strokeJoin = Paint.Join.ROUND
    outlinePaint.isAntiAlias = true
    setOnClickListener { _, _, _ -> false }
}

private class Layers(val map: MapView) {
    var trackLines: List<Overlay> = emptyList()
    val progress = line(0xFFE5383B.toInt(), 4.5f)
    val start = DotOverlay(0xFF1F2126.toInt(), 0xFFE8E9EC.toInt(), 5.5f, 0)
    val dot = DotOverlay(0xFFE5383B.toInt(), 0xFFFFFFFF.toInt(), 7f, 0x40E5383B)

    init {
        map.overlays.addAll(listOf(progress, start, dot))
    }

    fun setTracks(tracks: List<List<GeoPoint>>, faded: Boolean) {
        map.overlays.removeAll(trackLines.toSet())
        val list = ArrayList<Overlay>()
        for (t in tracks) {
            if (t.size < 2) continue
            if (faded) {
                list += line(0xFF5A3134.toInt(), 4.5f).apply { setPoints(t) }
            } else {
                list += line(0x38E5383B, 12f).apply { setPoints(t) }
                list += line(0xFFE5383B.toInt(), 4.5f).apply { setPoints(t) }
            }
        }
        map.overlays.addAll(0, list)
        trackLines = list
    }
}

@Composable
fun RideMap(
    modifier: Modifier,
    tracks: List<List<GeoPoint>>,
    faded: Boolean = false,
    progress: List<GeoPoint>? = null,
    dot: GeoPoint? = null,
    fitKey: Any? = null,
    fallbackCenter: GeoPoint? = null,
    bottomInsetFrac: Double = 0.0,
) {
    val ctx = LocalContext.current
    val dark by Prefs.mapDark.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            setBackgroundColor(0xFF1F2126.toInt())
            overlayManager.tilesOverlay.loadingBackgroundColor = 0xFF212329.toInt()
            overlayManager.tilesOverlay.loadingLineColor = 0xFF2A2D34.toInt()
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(10.7769, 106.7009))
        }
    }
    val layers = remember { Layers(map) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
        }
    }

    LaunchedEffect(dark) {
        map.setTileSource(if (dark) Tiles.dark else Tiles.light)
    }

    DisposableEffect(tracks, faded) {
        layers.setTracks(tracks, faded)
        map.invalidate()
        onDispose { }
    }

    SideEffect {
        layers.progress.setPoints(progress ?: emptyList())
        layers.start.pos = tracks.firstOrNull()?.firstOrNull()
        layers.dot.pos = dot
        map.invalidate()
    }

    LaunchedEffect(fitKey, fallbackCenter != null) {
        val all = tracks.flatten()
        val doFit = {
            if (all.size >= 2) {
                val bb = BoundingBox.fromGeoPoints(all)
                val latSpan = max(bb.latitudeSpan, 0.004)
                val lonSpan = max(bb.longitudeSpan, 0.004)
                val box = BoundingBox(
                    bb.latNorth + latSpan * 0.15,
                    bb.lonEast + lonSpan * 0.15,
                    bb.latSouth - latSpan * (0.15 + bottomInsetFrac),
                    bb.lonWest - lonSpan * 0.15,
                )
                map.zoomToBoundingBox(box, false, (24 * density).toInt())
            } else if (all.size == 1) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(all[0])
            } else if (fallbackCenter != null) {
                map.controller.setZoom(15.0)
                map.controller.setCenter(fallbackCenter)
            }
        }
        if (map.width > 0 && map.height > 0) doFit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> doFit() }
    }

    AndroidView(factory = { map }, modifier = modifier)
}
