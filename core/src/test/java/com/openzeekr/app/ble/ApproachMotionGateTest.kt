package com.openzeekr.app.ble

import com.openzeekr.app.ble.ProximityController.Zone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachMotionGateTest {

    private val now = 1_000_000_000L
    private val recent = ApproachMotionGate.RECENT_MOTION_MS

    // Regression: approach unlock fired on the first NEAR reading after ANY service restart (process
    // restart, app update, settings toggle), e.g. a car in a garage next to the bedroom, at night,
    // with the phone at rest on the nightstand.
    @Test
    fun aPhoneThatHasNotMovedDoesNotUnlock() {
        assertFalse(ApproachMotionGate.mayUnlock(hasSource = true, movingNow = false, lastMovingAtMs = 0L, nowMs = now, prevZone = Zone.UNKNOWN))
        assertFalse(ApproachMotionGate.mayUnlock(hasSource = true, movingNow = false, lastMovingAtMs = now - recent - 1, nowMs = now, prevZone = Zone.FAR))
    }

    @Test
    fun walkingUpOrHavingJustStoppedAtTheDoorUnlocks() {
        assertTrue(ApproachMotionGate.mayUnlock(hasSource = true, movingNow = true, lastMovingAtMs = 0L, nowMs = now, prevZone = Zone.UNKNOWN))
        assertTrue(ApproachMotionGate.mayUnlock(hasSource = true, movingNow = false, lastMovingAtMs = now - recent, nowMs = now, prevZone = Zone.NEAR))
    }

    // No motion sensor at all: we can't tell rest from approach, so require an OBSERVED FAR -> NEAR
    // crossing (not the first reading of a fresh session).
    @Test
    fun withoutAMotionSourceOnlyAnObservedCrossingUnlocks() {
        assertTrue(ApproachMotionGate.mayUnlock(hasSource = false, movingNow = false, lastMovingAtMs = 0L, nowMs = now, prevZone = Zone.FAR))
        assertFalse(ApproachMotionGate.mayUnlock(hasSource = false, movingNow = false, lastMovingAtMs = 0L, nowMs = now, prevZone = Zone.UNKNOWN))
        assertFalse(ApproachMotionGate.mayUnlock(hasSource = false, movingNow = false, lastMovingAtMs = 0L, nowMs = now, prevZone = Zone.NEAR))
    }
}
