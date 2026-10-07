package com.openzeekr.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DkFrameLogTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // Regression: the 0x010b SEND_DKEY plaintext (nSeq||ts||digitalKey) was hex-dumped to the log.
    @Test
    fun theDigitalKeyFrameIsRedacted() {
        val digitalKey = ByteArray(32) { (0xA0 + it).toByte() }
        val plain = DkPayload.wrap(1, 0x12345678, digitalKey)
        val logged = DkFrameLog.plain(DkProtocol.CMD_A2V_SEND_DKEY, plain)
        assertFalse(logged.contains(hex(digitalKey).substring(0, 16)))
        assertEquals("[redacted ${plain.size}B]", logged)
    }

    @Test
    fun otherFramesAreLoggedInFull() {
        val plain = byteArrayOf(0x00, 0x01, 0x6a, 0x0b, 0x01)
        assertEquals("00016a0b01", DkFrameLog.plain(DkProtocol.CMD_A2V_CONTROL, plain))
    }
}
