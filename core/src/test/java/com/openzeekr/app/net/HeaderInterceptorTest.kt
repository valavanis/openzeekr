package com.openzeekr.app.net

import com.openzeekr.app.config.ConfigStore
import com.openzeekr.app.config.FakePrefs
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class HeaderInterceptorTest {

    private val key = "0123456789abcdef"
    private val iv = "fedcba9876543210"

    /** Records the request the interceptor forwards (what the next interceptor - the signer - sees). */
    private class CapturingChain(private val req: Request) : Interceptor.Chain {
        var forwarded: Request? = null
        override fun request() = req
        override fun proceed(request: Request): Response {
            forwarded = request
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    private fun store() = ConfigStore(FakePrefs()).apply {
        update { it.copy(vin = "ACTIVE00000000001", vinKey = key, vinIv = iv, accessToken = "T") }
    }

    private fun request(targetVin: String?) = Request.Builder()
        .url("https://eu-snc-tsp-api-gw.zeekrlife.com/ms-remote-control/v1.0/remoteControl/control")
        .apply { if (targetVin != null) header(TARGET_VIN_HEADER, targetVin) }
        .build()

    @Test
    fun withoutATargetTheActiveCarIsAddressed() {
        val chain = CapturingChain(request(null))
        HeaderInterceptor(store()).intercept(chain)
        assertEquals(VinCrypto.encryptVin("ACTIVE00000000001", key, iv), chain.forwarded!!.header("x-vin"))
    }

    // The walk-away backstop targets the KEY's car. The internal header must become X-VIN and be gone
    // before the signer: it is never sent, and never part of the signed string.
    @Test
    fun aTargetVinBecomesXVinAndIsStrippedBeforeSigning() {
        val chain = CapturingChain(request("KEYCAR00000000002"))
        HeaderInterceptor(store()).intercept(chain)
        val fwd = chain.forwarded!!
        assertEquals(VinCrypto.encryptVin("KEYCAR00000000002", key, iv), fwd.header("x-vin"))
        assertNull(fwd.header(TARGET_VIN_HEADER))
    }
}
