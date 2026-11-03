package com.mondiro.mride.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mondiro.mride.update.Updater
import com.mondiro.mride.update.WhatsNew
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = C.red)
        items.forEach { Text("•  $it", fontSize = 13.sp, color = C.text) }
    }
}

@Composable
fun WhatsNewDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val wn = remember { WhatsNew.load(ctx) }
    val upd by Updater.state.collectAsState()
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.panel,
        title = { Text("What's new", color = C.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (wn != null) {
                    val d = wn.date.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else wn.date }
                    Text("v${wn.version} · $d", color = C.muted, fontSize = 12.5.sp)
                    Section("Mới", wn.new)
                    Section("Cải thiện", wn.improved)
                    Section("Sửa lỗi", wn.fixed)
                }
                val u = upd
                if (u is Updater.State.Available) {
                    Text("Có bản mới v${u.version}", color = C.latest, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            val u = upd
            if (u is Updater.State.Available) {
                TextButton(onClick = { onClose(); Updater.showDialog.value = true }) {
                    Text("Cập nhật", color = C.red, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Đóng", color = C.text) } },
    )
}

@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val show by Updater.showDialog.collectAsState()
    val st by Updater.state.collectAsState()
    if (!show) return
    val a = Updater.available ?: return
    val dl = st as? Updater.State.Downloading
    AlertDialog(
        onDismissRequest = { Updater.showDialog.value = false },
        containerColor = C.panel,
        title = { Text("Có bản mới v${a.version}", color = C.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (a.notes.isNotBlank()) {
                    Text(
                        a.notes.replace("#", "").trim(), color = C.text, fontSize = 13.sp,
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (dl != null) {
                    Row { Text("Đang tải… ${(dl.progress * 100).toInt()}%", color = C.muted, fontSize = 12.sp) }
                    LinearProgressIndicator(
                        progress = { dl.progress }, modifier = Modifier.fillMaxWidth(),
                        color = C.red, trackColor = C.track,
                    )
                }
                val e = st as? Updater.State.Error
                if (e != null) Text(e.msg, color = C.red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = dl == null, onClick = { scope.launch { Updater.downloadAndInstall(ctx) } }) {
                Text("Cập nhật", color = C.red, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { Updater.showDialog.value = false }) { Text("Để sau", color = C.text) }
        },
    )
}
