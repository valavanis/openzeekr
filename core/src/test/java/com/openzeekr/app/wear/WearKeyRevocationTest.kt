package com.openzeekr.app.wear

import com.openzeekr.app.wear.WearKeyProtocol.watchMustPurge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearKeyRevocationTest {

    // Regression: Remove key / sign-out told the watch to purge with one fire-and-forget message, sent
    // only to watches connected at that moment. A watch that was off or out of range kept a working key.
    // The phone now publishes its CURRENT key id as durable state; the watch compares on every sync.
    @Test
    fun aWatchKeyThePhoneNoLongerHasIsPurged() {
        assertTrue(watchMustPurge(watchDkId = "8000a6af", phoneDkId = ""))
    }

    @Test
    fun aWatchKeyReplacedOnThePhoneIsPurged() {
        assertTrue(watchMustPurge(watchDkId = "8000a6af", phoneDkId = "8000b001"))
    }

    @Test
    fun theCurrentKeyIsKept() {
        assertFalse(watchMustPurge(watchDkId = "8000a6af", phoneDkId = "8000a6af"))
    }

    @Test
    fun aWatchWithoutAKeyHasNothingToPurge() {
        assertFalse(watchMustPurge(watchDkId = null, phoneDkId = ""))
    }
}
