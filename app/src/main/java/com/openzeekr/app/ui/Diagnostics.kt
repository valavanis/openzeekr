package com.openzeekr.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.openzeekr.app.Deps
import com.openzeekr.app.ui.theme.Brand
import com.openzeekr.app.util.DiagRecorder
import com.openzeekr.app.util.DiagReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings card for the persistent diagnostic recording ([DiagRecorder]): turn it on, use the car as usual
 * (walk away, come back, note the time of anything that goes wrong), then share ONE plain-text file: a
 * phone / settings header, a summary of what happened, and the timeline. The file never holds key
 * material, passwords or tokens (not recorded at all); identity dumps are left out and the VIN is masked.
 */
@Composable
fun DiagnosticsCard(deps: Deps) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rec by DiagRecorder.state.collectAsState()
    val recording = rec.recording && rec.untilMs > System.currentTimeMillis()
    var sizeBytes by remember { mutableLongStateOf(0L) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Recorded size: refreshed on every change, and every few seconds while recording.
    LaunchedEffect(recording, refresh) {
        while (true) {
            sizeBytes = withContext(Dispatchers.IO) { DiagRecorder.sizeBytes() }
            if (!recording) break
            delay(5_000)
        }
    }

    SettingsCard {
        CardTitle("Diagnostics")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Record diagnostics (48 h)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Keeps a timeline of the key, Bluetooth and approach-unlock steps on this phone, even across " +
                    "restarts. No key, password or token is recorded.", color = Brand.muted, fontSize = 12.sp)
            }
            Switch(
                checked = recording,
                onCheckedChange = { on -> if (on) DiagRecorder.start() else DiagRecorder.stop(); refresh++ },
                colors = brandSwitchColors(),
            )
        }
        Text(
            when {
                recording -> "Recording until ${shortTime(rec.untilMs)} · ${kb(sizeBytes)}"
                sizeBytes > 0 -> "Not recording · ${kb(sizeBytes)} kept"
                else -> "Not recording"
            },
            color = Brand.muted, fontSize = 12.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    busy = true; status = "Preparing…"
                    scope.launch {
                        status = shareDiagnostics(ctx, deps)
                        busy = false
                    }
                },
                enabled = !busy && sizeBytes > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Share") }
            OutlinedButton(
                onClick = { DiagRecorder.clear(); status = "Cleared."; refresh++ },
                enabled = !busy && sizeBytes > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
        }
        if (status.isNotBlank()) Text(status, color = Brand.muted, fontSize = 12.sp)
    }
}

/** Builds the report off the main thread, writes it under cache/exports/ and opens the share sheet. */
private suspend fun shareDiagnostics(ctx: Context, deps: Deps): String = runCatching {
    val file = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        // Only the latest export is kept: an older one would be a stale copy of the same timeline.
        dir.listFiles { f -> f.name.startsWith(EXPORT_PREFIX) }?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(dir, "$EXPORT_PREFIX$stamp.txt").apply { writeText(diagnosticsReport(ctx, deps)) }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "OpenZeekr diagnostics ${file.nameWithoutExtension.removePrefix(EXPORT_PREFIX)}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    "Diagnostics ready (${kb(file.length())}) - pick where to send it."
}.getOrElse { "Share failed: ${it.message}" }

/** Header (phone, settings, permissions) + summary + timeline; VINs masked, identity dumps dropped. */
private fun diagnosticsReport(ctx: Context, deps: Deps): String {
    val cfg = deps.config.current()
    val rec = DiagRecorder.state.value
    val lines = DiagReport.sanitize(DiagRecorder.lines())
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val granted = PERMISSIONS.joinToString(" ") { p ->
        "${p.substringAfterLast('.')}=${ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED}"
    }
    val header = buildString {
        appendLine("OpenZeekr diagnostics, exported ${Date()}")
        appendLine("app ${deps.appVersion} | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("recording: ${if (rec.recording) "on until ${Date(rec.untilMs)}" else "off"}")
        appendLine("key: provisioned=${deps.ble.hasCredential} | bluetooth on=${deps.ble.bluetoothAvailable} | link=${deps.ble.state.value}")
        appendLine("proximity: enabled=${cfg.proximityEnabled} approachUnlock=${cfg.approachUnlockOn} " +
            "walkAwayLock=${cfg.walkAwayLockOn} sensitivity=${cfg.proximitySensitivity}")
        appendLine("thresholds: unlock>=${cfg.sensitivityUnlockRssi} lock<=${cfg.sensitivityLockRssi} dBm " +
            "clampedByFloor=${cfg.unlockClampedByFloor} floorUnreachable=${cfg.unlockFloorUnreachable}")
        appendLine("calibration: calibrated=${cfg.isProximityCalibrated} near=${cfg.calibNearRssi} " +
            "far=${cfg.calibFarRssi} inside=${cfg.calibInsideRssi}")
        appendLine("presenceOffload=${cfg.presenceOffloadEnabled} | motion source=${deps.motion.source} | " +
            "watch key=${cfg.wearKeyEnabled} | region=${cfg.regionCode}")
        appendLine("battery optimization ignored=${pm?.isIgnoringBatteryOptimizations(ctx.packageName)}")
        appendLine("permissions: $granted")
    }
    val vins = buildList {
        add(cfg.vin); cfg.vehicles.forEach { add(it.vin) }; deps.ble.credentialVin?.let { add(it) }
    }
    return DiagReport.redact(header + "\n" + DiagReport.summarize(lines) + "\n---- log ----\n" + lines.joinToString("\n"), vins)
}

private const val EXPORT_PREFIX = "openzeekr-diagnostics-"

// String literals rather than Manifest constants: some are newer than minSdk and only reported here.
private val PERMISSIONS = listOf(
    "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.ACTIVITY_RECOGNITION", "android.permission.POST_NOTIFICATIONS",
)

private fun kb(bytes: Long) = "${(bytes + 1023) / 1024} KB"

private fun shortTime(ms: Long) = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(ms))
