package com.openzeekr.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleThinnerTest {

    private fun sample(zone: String, armed: Boolean = false) =
        "rssi=-60 ema=-61 ~1.0m trend=+0.0 zone=$zone armed=$armed thr(u/l)=-65/-75 motion=MOVING next=200ms"

    @Test
    fun steadySamplesAreKeptOncePerInterval() {
        val t = SampleThinner(intervalMs = 1_000)
        val kept = (0L..2_000L step 200).filter { t.keep(it, "prox", sample("NEAR")) }
        assertEquals(listOf(0L, 1_000L, 2_000L), kept)
    }

    @Test
    fun aZoneOrArmedChangeIsKeptAtOnce() {
        val t = SampleThinner(intervalMs = 1_000)
        assertTrue(t.keep(0, "prox", sample("FAR")))
        assertTrue(t.keep(200, "prox", sample("NEAR")))
        assertTrue(t.keep(400, "prox", sample("NEAR", armed = true)))
    }

    @Test
    fun everyOtherLineIsKept() {
        val t = SampleThinner(intervalMs = 1_000)
        assertTrue(t.keep(0, "prox", sample("NEAR")))
        assertTrue(t.keep(1, "prox", "approach-unlock held (rssi=-60 prevZone=NEAR): …"))
        assertTrue(t.keep(2, "ble", "rssi=-60 zone=NEAR armed=false"))
    }
}
