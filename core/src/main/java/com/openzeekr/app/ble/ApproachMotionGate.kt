package com.openzeekr.app.ble

import com.openzeekr.app.ble.ProximityController.Zone

/**
 * "Is this NEAR reading an APPROACH?" An approach unlock needs evidence that the user walked up, not just
 * a strong signal: without it, any (re)start of the key service next to the car - a process restart, an
 * app update, a settings toggle, with the car in a garage next to the bedroom at night - unlocked it on
 * the first reading.
 *
 *  - With a motion source: the phone is moving, or moved within [RECENT_MOTION_MS] (walked up, then stood
 *    at the door while the key session came up).
 *  - Without one we can't tell rest from approach, so we require an OBSERVED FAR -> NEAR crossing in this
 *    session ([prevZone] FAR), never the first reading of a fresh session.
 *
 * Pure: unit-tested in ApproachMotionGateTest.
 */
internal object ApproachMotionGate {

    const val RECENT_MOTION_MS = 120_000L

    fun mayUnlock(hasSource: Boolean, movingNow: Boolean, lastMovingAtMs: Long, nowMs: Long, prevZone: Zone): Boolean =
        if (hasSource) movingNow || (lastMovingAtMs > 0L && nowMs - lastMovingAtMs <= RECENT_MOTION_MS)
        else prevZone == Zone.FAR
}
