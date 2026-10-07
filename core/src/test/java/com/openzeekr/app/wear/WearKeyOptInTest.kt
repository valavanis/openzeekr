package com.openzeekr.app.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearKeyOptInTest {

    private val key = mapOf("dk_id" to "8000a6af", "priv" to "…")

    // The phone used to hand its private key to ANY paired watch that asked (and push it unasked on
    // provisioning). Sharing is now opt-in on the phone.
    @Test
    fun withWatchKeyOffTheWatchGetsARefusalNotTheKey() {
        val reply = WearKeyProtocol.keyReply(watchKeyEnabled = false, blob = key)
        assertEquals(WearKeyProtocol.ERR_WATCH_KEY_OFF, WearKeyProtocol.replyError(reply))
    }

    @Test
    fun withWatchKeyOnTheKeyIsSent() {
        val reply = WearKeyProtocol.keyReply(watchKeyEnabled = true, blob = key)
        assertEquals(key, reply)
        assertNull(WearKeyProtocol.replyError(reply))
    }

    @Test
    fun noKeyOnThePhoneIsAnEmptyReply() {
        assertEquals(emptyMap<String, String>(), WearKeyProtocol.keyReply(watchKeyEnabled = true, blob = null))
    }

    // The durable key state tells watches which key they may keep: none while sharing is off.
    @Test
    fun thePublishedKeyIdIsBlankWhileSharingIsOff() {
        assertEquals("", WearKeyProtocol.publishedKeyId(watchKeyEnabled = false, phoneDkId = "8000a6af"))
        assertEquals("8000a6af", WearKeyProtocol.publishedKeyId(watchKeyEnabled = true, phoneDkId = "8000a6af"))
        assertEquals("", WearKeyProtocol.publishedKeyId(watchKeyEnabled = true, phoneDkId = ""))
    }
}
