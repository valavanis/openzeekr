package com.openzeekr.app.util

/**
 * Thins the proximity controller's per-sample RSSI line ("rssi=… zone=… armed=…", up to five a second while
 * walking or driving) for the persistent recording, so a long drive doesn't rotate the earlier events out:
 * a sample is kept when its zone / armed state differs from the last kept one, or [intervalMs] after it.
 * Every other line passes untouched. Pure: unit-tested in SampleThinnerTest.
 */
class SampleThinner(private val intervalMs: Long = 1_000L) {

    private var lastKeptMs = 0L
    private var lastState: String? = null

    @Synchronized
    fun keep(tsMs: Long, area: String, msg: String): Boolean {
        if (area != "prox" || !msg.startsWith("rssi=")) return true
        val state = STATE.find(msg)?.value.orEmpty()
        if (state == lastState && tsMs - lastKeptMs < intervalMs) return false
        lastState = state
        lastKeptMs = tsMs
        return true
    }

    private companion object {
        val STATE = Regex("zone=\\S+ armed=\\S+")
    }
}
