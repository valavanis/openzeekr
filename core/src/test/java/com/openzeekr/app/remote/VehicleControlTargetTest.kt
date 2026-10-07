package com.openzeekr.app.remote

import com.openzeekr.app.remote.VehicleControl.Companion.keyOpensActiveCar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleControlTargetTest {

    @Test
    fun theKeyActsOnTheActiveCarWhenTheyMatch() {
        assertTrue(keyOpensActiveCar("LRWYGCEK1PC000001", "lrwygcek1pc000001"))
    }

    // Regression (multi-car): the BLE key always talks to the car it was provisioned for, so with
    // another car selected, tapping Unlock on that car's screen unlocked the KEY's car over BLE.
    @Test
    fun withAnotherCarActiveTheCommandMustNotGoOverTheKey() {
        assertFalse(keyOpensActiveCar("LRWYGCEK1PC000001", "LRWYGCEK1PC000002"))
    }

    // No active car known yet: nothing to disagree with, the key's car is the only candidate.
    @Test
    fun withoutAnActiveCarTheKeyStillWorks() {
        assertTrue(keyOpensActiveCar("LRWYGCEK1PC000001", ""))
    }

    @Test
    fun noKeyNeverMatches() {
        assertFalse(keyOpensActiveCar(null, "LRWYGCEK1PC000001"))
    }
}
