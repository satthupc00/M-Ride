package com.mondiro.mride.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object C {
    val chrome = Color(0xFF1F2126)
    val panel = Color(0xFF26292F)
    val line = Color(0xFF363A42)
    val hover = Color(0xFF2F333A)
    val text = Color(0xFFE8E9EC)
    val muted = Color(0xFF8D929C)
    val red = Color(0xFFE5383B)
    val selected = Color(0xFF3A2426)
    val ok = Color(0xFF3DDC8A)
    val latest = Color(0xFF2EB872)
    val credit = Color(0xFF6B707A)
    val warn = Color(0xFFF5A623)
    val grey = Color(0xFFA6A9B0)
    val track = Color(0xFF474C55)
}

object Fmt {
    private val vi = Locale("vi", "VN")
    const val DAY = 86_400_000L
    fun km(m: Double): String = String.format(vi, "%.1f", m / 1000.0)
    fun hm(t: Long): String = SimpleDateFormat("HH:mm", vi).format(Date(t))
    fun hms(t: Long): String = SimpleDateFormat("HH:mm:ss", vi).format(Date(t))
    /** Thời gian đã đi, dạng H:MM:SS. */
    fun elapsed(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(vi, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }
    fun dur(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "${min / 60}:${String.format(vi, "%02d", min % 60)}" else "$min ph"
    }
    fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun day(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val wd = arrayOf("Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")[c.get(Calendar.DAY_OF_WEEK) - 1]
        return "$wd, " + SimpleDateFormat("dd/MM", vi).format(Date(t))
    }
    fun date(t: Long): String = SimpleDateFormat("dd/MM/yyyy", vi).format(Date(t))
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun MRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.red, onPrimary = Color.White,
            background = C.chrome, onBackground = C.text,
            surface = C.panel, onSurface = C.text,
            surfaceVariant = C.panel, onSurfaceVariant = C.muted,
            surfaceContainer = C.chrome, surfaceContainerHigh = C.panel,
            secondaryContainer = C.selected, onSecondaryContainer = C.text,
            outline = C.line, outlineVariant = C.line,
        ),
        content = content,
    )
}

/** Phản hồi khi bấm: lún nhẹ (scale .95, spring) + ripple đỏ. */
@Composable
fun Modifier.pressable(shape: Shape, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press",
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clip(shape)
        .clickable(src, ripple(color = C.red), enabled = enabled, onClick = onClick)
}

@Composable
fun Credit(modifier: Modifier = Modifier) {
    Text("Created by Mondiro", modifier = modifier, color = C.credit, fontSize = 10.5.sp)
}

@Composable
fun AppName(size: Int = 15) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = C.red)) { append("M") }
            append(" Ride")
        },
        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = size.sp,
    )
}

@Composable
fun StatCell(value: String, unit: String, label: String, modifier: Modifier = Modifier, bg: Color = C.chrome, big: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = if (big) 12.dp else 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            buildAnnotatedString {
                append(value)
                if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 11.sp, color = C.muted, fontWeight = FontWeight.SemiBold)) { append(" $unit") }
            },
            fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 17.sp, color = C.text, maxLines = 1,
        )
        Text(label, fontSize = 10.5.sp, color = C.muted, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun RedButton(text: String, @DrawableRes icon: Int?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(46.dp).pressable(shape, enabled, onClick)
            .background(if (enabled) C.red else C.red.copy(alpha = .35f), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(17.dp), tint = Color.White)
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, @DrawableRes icon: Int?, modifier: Modifier = Modifier, height: Int = 46, iconTint: Color = C.text, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (height >= 40) 12.dp else 8.dp)
    Row(
        modifier.height(height.dp).pressable(shape, true, onClick)
            .background(C.chrome, shape).border(1.dp, C.line, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(if (height >= 40) 16.dp else 14.dp), tint = iconTint)
        Text(text, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = if (height >= 40) 13.5.sp else 12.5.sp, maxLines = 1)
    }
}

@Composable
fun RateChip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.pressable(shape, true, onClick)
            .background(if (on) C.selected else C.chrome, shape)
            .border(1.dp, if (on) C.red else C.line, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.text) }
}

@Composable
fun IconBtn(@DrawableRes icon: Int, desc: String, tint: Color = C.text, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(40.dp).pressable(CircleShape, true, onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), desc, Modifier.size(19.dp), tint = tint)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(C.panel.copy(alpha = .97f)).border(1.dp, C.line, shape)) { content() }
}
