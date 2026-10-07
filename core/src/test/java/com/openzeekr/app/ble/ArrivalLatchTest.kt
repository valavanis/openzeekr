package com.openzeekr.app.ble

import com.openzeekr.app.ble.ProximityController.Zone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalLatchTest {

    private val confirmMs = 5_000L

    @Test
    fun freshLatchAllowsAnArrival() {
        val latch = ArrivalLatch()
        assertTrue(latch.mayFire(Zone.UNKNOWN))
        assertTrue(latch.mayFire(Zone.NEAR))
    }

    @Test
    fun onceFiredTheSameVisitDoesNotFireAgainWhileNear() {
        val latch = ArrivalLatch()
        latch.onFired()
        assertFalse(latch.mayFire(Zone.NEAR))
        // A clean crossing into NEAR (from UNKNOWN/FAR) still counts as an arrival, as before.
        assertTrue(latch.mayFire(Zone.UNKNOWN))
    }

    @Test
    fun aFarReadingReArmsTheNextArrival() {
        val latch = ArrivalLatch()
        latch.onFired()
        latch.onLeft()
        assertTrue(latch.mayFire(Zone.NEAR))
    }

    // Regression: the unlocked idle-watch decided "walk-away" on a silent link (RSSI null) and cleared
    // armedUnlocked itself, so the link-loss handler never re-armed the arrival. The next session that
    // came up already NEAR then never unlocked ("connects but doesn't unlock").
    @Test
    fun silentWalkAwayReArmsOnceTheLinkStaysDown() {
        val latch = ArrivalLatch()
        latch.onFired()
        latch.onSilentWalkAway()
        assertFalse(latch.onLinkDown(confirmMs - 1, confirmMs))
        assertFalse(latch.mayFire(Zone.NEAR))
        assertTrue(latch.onLinkDown(confirmMs, confirmMs))
        assertTrue(latch.mayFire(Zone.NEAR))
    }

    @Test
    fun silentWalkAwayFollowedByANearReadingWasAGlitch() {
        val latch = ArrivalLatch()
        latch.onFired()
        latch.onSilentWalkAway()
        latch.onNear() // still at the car: the silent read was a glitch, not a departure
        assertFalse(latch.onLinkDown(confirmMs * 10, confirmMs))
        assertFalse(latch.mayFire(Zone.NEAR))
    }

    @Test
    fun aLinkBounceWithoutADepartureDoesNotReArm() {
        val latch = ArrivalLatch()
        latch.onFired()
        assertFalse(latch.onLinkDown(confirmMs * 10, confirmMs))
        assertFalse(latch.mayFire(Zone.NEAR))
    }

    @Test
    fun resetClearsEverything() {
        val latch = ArrivalLatch()
        latch.onFired()
        latch.onSilentWalkAway()
        latch.reset()
        assertTrue(latch.mayFire(Zone.NEAR))
        assertFalse(latch.onLinkDown(confirmMs, confirmMs))
    }
}
