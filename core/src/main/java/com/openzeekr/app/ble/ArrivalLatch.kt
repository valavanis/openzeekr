package com.openzeekr.app.ble

import com.openzeekr.app.ble.ProximityController.Zone

/**
 * The once-per-visit latch behind approach-unlock: "have we already fired an unlock for THIS visit to
 * the car?". [ProximityController] fires on a NEAR sample only when [mayFire] says so, and the latch is
 * re-armed only once the user has demonstrably LEFT the car.
 *
 * "Left" has three proofs, all routed to [onLeft]:
 *  - a connected RSSI reading at/below the lock threshold (clearly FAR),
 *  - the link-loss walk-away lock confirmed after the drop delay (we had unlocked the car),
 *  - a walk-away lock decided on a SILENT link ([onSilentWalkAway]) whose link then stays down for the
 *    confirm delay ([onLinkDown]). A NEAR reading in between ([onNear]) means the silent read was a
 *    glitch at the car, so nothing is re-armed.
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

    /** A walk-away lock was decided on a silent link; becomes a departure if the link stays down. */
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

    /** A NEAR reading: the user is at the car, so a pending silent walk-away was a glitch. */
    fun onNear() { departurePending = false }

    /** A walk-away lock was decided because the link went silent (no RSSI reading). */
    fun onSilentWalkAway() { departurePending = true }

    /**
     * The link has been down for [downMs]. Once that reaches [confirmAfterMs] a pending silent
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
