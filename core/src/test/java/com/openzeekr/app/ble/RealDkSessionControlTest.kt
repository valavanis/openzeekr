package com.openzeekr.app.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RealDkSession.control] / [RealDkSession.ping] against a scripted fake car. The car's 0x0111 / 0x0112
 * replies carry no request id, so the session must never have two 0x0110 exchanges in flight at once.
 */
class RealDkSessionControlTest {

    private val key = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(12) { (0x40 + it).toByte() }
    private val carScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() = carScope.cancel()

    /** One scripted reply: after [delayMs], the car sends [cmdId] carrying [tail] (after nSeq||ts). */
    private data class Reply(val delayMs: Long, val cmdId: Int, val tail: ByteArray)

    private fun receipt(delayMs: Long) = Reply(delayMs, DkProtocol.CMD_V2A_CMD_RECEIVED, byteArrayOf(0))
    private fun result(delayMs: Long, err: Int) =
        Reply(delayMs, DkProtocol.CMD_V2A_RESULT, byteArrayOf((err ushr 8).toByte(), err.toByte()))

    /** Fake car: answers every 0x0110 control frame by its ctrl byte, from another thread (like GATT callbacks). */
    private inner class FakeCar(private val script: (ctrl: Byte) -> List<Reply>) : DkTransport {
        private lateinit var handler: (Int, ByteArray) -> Unit

        override suspend fun write(cmd: Int, framed: ByteArray): Boolean {
            if (cmd != DkProtocol.CMD_A2V_CONTROL) return true
            val plain = DkCrypto.gcmDecrypt(key, iv, DkFrame.decode(framed).body)
            for (r in script(plain[6])) carScope.launch {
                delay(r.delayMs)
                val body = DkPayload.wrap(DkPayload.nextSeq(), DkPayload.timestamp(), r.tail)
                handler(r.cmdId, DkCrypto.gcmEncrypt(key, iv, body))
            }
            return true
        }

        override fun onInbound(handler: (Int, ByteArray) -> Unit) { this.handler = handler }
        override fun broadcastRnd(): ByteArray? = null
        override fun close() {}
    }

    private fun session(script: (ctrl: Byte) -> List<Reply>) =
        RealDkSession(FakeCar(script), { null }).also { it.primeEstablishedForTest(key, iv) }

    @Test
    fun aReceiptWithoutAResultIsConfirmed() = runBlocking {
        val s = session { listOf(receipt(10)) }
        assertEquals(ControlResult.CONFIRMED, s.control(DkProtocol.CTRL_UNLOCK, 1_500))
    }

    @Test
    fun aNonZeroResultIsRejected() = runBlocking {
        val s = session { listOf(receipt(10), result(30, err = 0x0001)) }
        assertEquals(ControlResult.REJECTED, s.control(DkProtocol.CTRL_LOCK, 1_500))
    }

    @Test
    fun noReceiptIsNoResponse() = runBlocking {
        val s = session { emptyList() }
        assertEquals(ControlResult.NO_RESPONSE, s.control(DkProtocol.CTRL_UNLOCK, 200))
    }

    // Regression: the approach liveness ping (0x0110 / RPA-start, which the car always rejects with a
    // 0x0112 err=0x100a) was still in flight when the approach unlock fired. The ping's late 0x0112 landed
    // in the unlock's result slot, so a good unlock read as REJECTED and the link was torn down and
    // rebuilt before the next attempt - seconds of "connected but not unlocking" at the door.
    @Test
    fun aPingsLateRejectIsNotCreditedToTheNextUnlock() = runBlocking {
        val s = session { ctrl ->
            when (ctrl) {
                DkProtocol.CTRL_RPA_START -> listOf(receipt(10), result(200, err = 0x100a))
                DkProtocol.CTRL_UNLOCK -> listOf(receipt(10)) // a successful unlock sends only 0x0111
                else -> emptyList()
            }
        }
        val ping = async(Dispatchers.Default) { s.ping(700) }
        delay(60) // the ping's receipt is in; its reject is still on the way
        assertEquals(ControlResult.CONFIRMED, s.control(DkProtocol.CTRL_UNLOCK, 1_500))
        assertTrue(ping.await())
    }
}
