package com.openzeekr.app.ble

import com.openzeekr.app.ble.DkBleManager.Companion.canReuseCachedDevice
import com.openzeekr.app.ble.DkBleManager.Companion.dropRecovery
import com.openzeekr.app.ble.DkBleManager.DropRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DkBleManagerReconnectTest {

    // ---- presence wake: reuse the cached device only if the car still advertises from that address ----

    @Test
    fun presenceFromTheCachedAddressReusesIt() {
        assertTrue(canReuseCachedDevice("C4:7F:11:22:33:44", listOf("c4:7f:11:22:33:44")))
    }

    // Regression: the car advertises a rotating Resolvable Private Address. After a long absence the
    // presence scan sees the NEW address, but the wake reconnected to the cached OLD one, which no longer
    // answers: a ~30 s connect timeout per attempt while the user stood at the car.
    @Test
    fun presenceFromARotatedAddressDoesNotReuseTheCachedDevice() {
        assertFalse(canReuseCachedDevice("C4:7F:11:22:33:44", listOf("D2:01:AA:BB:CC:DD")))
    }

    // Another Zeekr parked nearby may be the strongest advertiser; our cached car is still valid if it is
    // among the presence results at all.
    @Test
    fun theCachedCarIsReusedEvenWhenAnotherZeekrIsStronger() {
        assertTrue(canReuseCachedDevice("C4:7F:11:22:33:44", listOf("D2:01:AA:BB:CC:DD", "C4:7F:11:22:33:44")))
    }

    @Test
    fun withoutBothSidesTheCachedDeviceIsNotTrusted() {
        assertFalse(canReuseCachedDevice(null, listOf("D2:01:AA:BB:CC:DD")))
        assertFalse(canReuseCachedDevice("C4:7F:11:22:33:44", emptyList()))
    }

    // ---- link dropped before the session was ready ----

    @Test
    fun aReadyLinkDropGoesIdleForTheKeepAlive() {
        assertEquals(DropRecovery.IDLE, dropRecovery(wasReady = true, connectTimedOut = false, deliberate = false,
            hasCachedDevice = true, retries = 0, maxRetries = 3))
    }

    @Test
    fun aMidSetupDropRetriesTheCachedDevice() {
        assertEquals(DropRecovery.RECONNECT_CACHED, dropRecovery(wasReady = false, connectTimedOut = false,
            deliberate = false, hasCachedDevice = true, retries = 0, maxRetries = 3))
    }

    // Regression: a connect that never came up within the stack's timeout (a rotated, dead address) was
    // retried against the same address three more times. Re-scan for the car's current address instead.
    @Test
    fun aConnectThatTimedOutRescans() {
        assertEquals(DropRecovery.RESCAN, dropRecovery(wasReady = false, connectTimedOut = true,
            deliberate = false, hasCachedDevice = true, retries = 0, maxRetries = 3))
    }

    @Test
    fun aDeliberateOrExhaustedDropFails() {
        assertEquals(DropRecovery.FAIL, dropRecovery(wasReady = false, connectTimedOut = false, deliberate = true,
            hasCachedDevice = true, retries = 0, maxRetries = 3))
        assertEquals(DropRecovery.FAIL, dropRecovery(wasReady = false, connectTimedOut = true, deliberate = false,
            hasCachedDevice = true, retries = 3, maxRetries = 3))
        assertEquals(DropRecovery.FAIL, dropRecovery(wasReady = false, connectTimedOut = false, deliberate = false,
            hasCachedDevice = false, retries = 0, maxRetries = 3))
    }
}
