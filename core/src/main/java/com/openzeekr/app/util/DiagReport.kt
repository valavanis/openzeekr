package com.openzeekr.app.util

/**
 * Turns the recorded diagnostic lines into a shareable report: VIN masking, and a summary of what matters
 * for the "connects but doesn't unlock" and 0x1012-reconnect investigations, so the reader sees the
 * pattern before scrolling through thousands of lines. Pure: unit-tested in DiagReportTest.
 */
object DiagReport {

    /** Replace each full VIN with "VIN…<last 4>". */
    fun redact(text: String, vins: Collection<String>): String =
        vins.filter { it.length >= 8 }.distinct()
            .fold(text) { acc, vin -> acc.replace(vin, "VIN…${vin.takeLast(4)}", ignoreCase = true) }

    // Lines that identify this phone / key (account id, device id, key id, calibration bytes, our
    // certificate) but say nothing about WHEN or WHY a connect / unlock failed: left out of the export.
    private val identityDumps = listOf("userId=", "handshake diag:", "coefSmall=", "coefBig=", "cert DER head=")

    /** Drop the identity dumps, keep the timeline. */
    fun sanitize(lines: List<String>): List<String> = lines.filter { l -> identityDumps.none { l.contains(it) } }

    private val counters = listOf(
        "process starts" to Regex("=== process start"),
        "key service starts" to Regex("svc +service start"),
        "presence wakes" to Regex("presence FIRST_MATCH"),
        "cached address rotated" to Regex("address rotated"),
        "connect timed out -> re-scan" to Regex("connect timed out"),
        "setup drops -> fast retry" to Regex("setup drop \\(status="),
        "sessions ready" to Regex("DK session READY"),
        "link drops" to Regex("link down \\("),
        "approach unlock armed" to Regex("approach-unlock ARM"),
        "walk-away locks" to Regex("walk-away-lock|idle-far-lock"),
        "liveness ping failures" to Regex("liveness ping failed"),
    )

    private val grouped = listOf(
        "handshake DK_STATUS errCode" to Regex("DK_STATUS \\(initState=\\d\\) errCode=(0x[0-9a-f]{4})"),
        "handshake failures" to Regex("DK handshake: (.{0,90})"),
        "approach unlock held because" to Regex("approach-unlock held \\([^)]*\\): (.*)"),
        "approach unlock attempts" to Regex("unlock attempt #\\d+ -> (\\w+)"),
        "walk-away BLE lock attempts" to Regex("BLE lock attempt #\\d+ -> (\\w+)"),
        "cloud lock" to Regex("cloud lock -> (\\w+)"),
        "manual BLE commands" to Regex("BLE control 0x[0-9a-f]{2} -> (\\w+)"),
        "BLE errors" to Regex("E/ble +(.{0,70})"),
    )

    fun summarize(lines: List<String>): String = buildString {
        appendLine("---- summary (${lines.size} lines${lines.firstOrNull()?.take(23)?.let { ", from $it" } ?: ""}) ----")
        counters.forEach { (label, rx) -> appendLine("$label: ${lines.count { rx.containsMatchIn(it) }}") }
        grouped.forEach { (label, rx) ->
            val counts = lines.mapNotNull { rx.find(it)?.groupValues?.get(1)?.trim() }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            if (counts.isEmpty()) appendLine("$label: none")
            else {
                appendLine("$label:")
                counts.take(12).forEach { (k, v) -> appendLine("  $k: $v") }
            }
        }
    }
}
