package com.openzeekr.app.util

/**
 * Thins the high-rate lines for the persistent recording, so a long drive doesn't rotate the earlier events
 * out: the proximity controller's per-sample RSSI line ("rssi=… zone=… armed=…", up to five a second) and
 * the car's 0x182 ranging push (raw frame and decoded line). A line of one of these kinds is kept when its
 * state (zone / armed, for RSSI samples) differs from the last kept one, or [intervalMs] after it. Every
 * other line passes untouched. Pure: unit-tested in SampleThinnerTest.
 */
class SampleThinner(private val intervalMs: Long = 1_000L) {

    private class Kind(val area: String, val prefix: String, val state: Regex? = null) {
        var lastKeptMs = 0L
        var lastState: String? = null
    }

    private val kinds = listOf(
        Kind("prox", "rssi=", Regex("zone=\\S+ armed=\\S+")),
        Kind("dkframe", "<- 0x0182 "),
        Kind("dk", "0x182 ranging"),
    )

    @Synchronized
    fun keep(tsMs: Long, area: String, msg: String): Boolean {
        val k = kinds.firstOrNull { it.area == area && msg.startsWith(it.prefix) } ?: return true
        val state = k.state?.find(msg)?.value.orEmpty()
        if (state == k.lastState && tsMs - k.lastKeptMs < intervalMs) return false
        k.lastState = state
        k.lastKeptMs = tsMs
        return true
    }
}
