package com.openzeekr.app.ble

import com.openzeekr.app.ble.ProximityController.Zone

/**
 * The once-per-visit latch behind approach-unlock: "have we already fired an unlock for THIS visit to
 * the car?". [ProximityController] fires on a NEAR sample only when [mayFire] says so, and the latch is
 * re-armed only once the user has demonstrably LEFT the car.
 *
 * "Left" has three proofs, all routed to [onLeft]:
 *  - a SMOOTHED connected RSSI reading at/below the lock threshold (clearly FAR),
 *  - the link-loss walk-away lock confirmed after the drop delay (we had unlocked the car),
 *  - an UNCONFIRMED walk-away lock (the unlocked idle-check: one raw reading, or none at all;
 *    [onUnconfirmedWalkAway]) whose link then stays down for the confirm delay ([onLinkDown]). A NEAR
 *    reading in between ([onNear]) means it was a glitch at the car, so nothing is re-armed - a single
 *    noisy reading must not turn into lock-then-re-unlock while the user is still at the car.
 *
 * Without the third proof, the common walk-away (the link drops during the unlocked idle-wait, so the
 * idle check sees no RSSI) left the latch set forever, and the next session that came up already near
 * the car never unlocked.
 *
 * Pure state, no Android: unit-tested in ArrivalLatchTest.
 */
internal class ArrivalLatch {

    /** True once an approach-unlock fired for the current visit. */
    var acted: Boolean = false
        private set

    /** An unconfirmed walk-away lock was decided; becomes a departure if the link stays down. */
    var departurePending: Boolean = false
        private set

    /**
     * May an approach-unlock fire on a NEAR sample whose previous zone was [prevZone]? A fresh crossing
     * into NEAR always may; staying NEAR may only if nothing fired yet this visit (the "session came up
     * already at the car" case, where the zone latched NEAR before the session was ready).
     */
    fun mayFire(prevZone: Zone): Boolean = prevZone != Zone.NEAR || !acted

    fun onFired() { acted = true }

    /** The user has left the car: the next NEAR is a new arrival. */
    fun onLeft() { acted = false; departurePending = false }

    /** A NEAR reading (at/above the unlock threshold): the user is at the car, so a pending walk-away
     *  was a glitch. */
    fun onNear() { departurePending = false }

    /** A walk-away lock was decided on weak evidence: one raw RSSI reading, or a silent link. */
    fun onUnconfirmedWalkAway() { departurePending = true }

    /**
     * The link has been down for [downMs]. Once that reaches [confirmAfterMs] a pending unconfirmed
     * walk-away is a real departure: re-arm and return true. Otherwise (no departure pending, or a
     * short drop the keep-alive may still recover) nothing changes.
     */
    fun onLinkDown(downMs: Long, confirmAfterMs: Long): Boolean {
        if (!departurePending || downMs < confirmAfterMs) return false
        onLeft()
        return true
    }

    fun reset() { acted = false; departurePending = false }
}
