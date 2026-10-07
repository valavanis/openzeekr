package com.openzeekr.app.config

import com.openzeekr.app.config.SecretsConfig.Companion.LOCK_RSSI_GAP_DB
import com.openzeekr.app.config.SecretsConfig.Companion.UNLOCK_RSSI_FLOOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockFloorTest {

    private fun cfg(near: Int, far: Int, preset: String) =
        SecretsConfig(calibNearRssi = near, calibFarRssi = far, proximitySensitivity = preset)

    // Regression: the -65 dBm "safety floor" only clamped a UI-only value; the controller used the raw
    // calibrated preset, so "far" on a weak calibration unlocked from ~10 m away (e.g. -74 dBm here).
    @Test
    fun theControllerThresholdNeverGoesWeakerThanTheFloor() {
        val c = cfg(near = -60, far = -80, preset = "far")   // preset alone would be -74
        assertEquals(UNLOCK_RSSI_FLOOR, c.sensitivityUnlockRssi)
        assertEquals(UNLOCK_RSSI_FLOOR - LOCK_RSSI_GAP_DB, c.sensitivityLockRssi)
        assertTrue(c.unlockClampedByFloor)
    }

    @Test
    fun aPresetStrongerThanTheFloorIsKept() {
        val c = cfg(near = -55, far = -75, preset = "veryclose")   // -58
        assertEquals(-58, c.sensitivityUnlockRssi)
        assertEquals(-58 - LOCK_RSSI_GAP_DB, c.sensitivityLockRssi)
        assertFalse(c.unlockClampedByFloor)
    }

    // If even AT THE DOOR this phone reads weaker than the floor, approach unlock can't fire reliably:
    // the UI says so instead of silently never unlocking.
    @Test
    fun aDoorReadingWeakerThanTheFloorIsFlagged() {
        assertTrue(cfg(near = -70, far = -88, preset = "close").unlockFloorUnreachable)
        assertFalse(cfg(near = -60, far = -80, preset = "close").unlockFloorUnreachable)
        assertFalse(SecretsConfig().unlockFloorUnreachable)   // not calibrated
    }
}
