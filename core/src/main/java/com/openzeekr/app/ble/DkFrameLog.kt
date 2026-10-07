package com.openzeekr.app.ble

/**
 * Formats DK frame plaintext for the BLE log ("dkframe"), redacting key material. The frame-diff dumps
 * are kept for byte-comparison against stock captures, but the 0x010b SEND_DKEY plaintext is
 * nSeq||ts||digitalKey: the key that opens the car must never reach logcat or the on-device buffer.
 */
internal object DkFrameLog {

    private val REDACTED = setOf(DkProtocol.CMD_A2V_SEND_DKEY)

    fun plain(cmdId: Int, plain: ByteArray): String =
        if (cmdId in REDACTED) "[redacted ${plain.size}B]" else plain.joinToString("") { "%02x".format(it) }
}
