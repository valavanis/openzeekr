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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class KickoutInterceptorTest {

    @After fun tearDown() { SessionSignal.sessionExpired.value = false; SessionSignal.loggedInElsewhere.value = false }

    /** A chain whose call runs [duringCall] (e.g. a re-login on another thread) and answers 401 + [code]. */
    private class FakeChain(private val code: String, private val duringCall: () -> Unit = {}) : Interceptor.Chain {
        private val req = Request.Builder().url("https://eu-snc-tsp-api-gw.zeekrlife.com/x").build()
        override fun request() = req
        override fun proceed(request: Request): Response {
            duringCall()
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                .body("""{"code":"$code"}""".toResponseBody("application/json".toMediaType())).build()
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

    @Test
    fun anExpiredTokenIsClearedAndTheUserPrompted() {
        val store = ConfigStore(FakePrefs()).apply { update { it.copy(accessToken = "T1") } }
        KickoutInterceptor(store).intercept(FakeChain("079012"))
        assertEquals("", store.current().accessToken)
        assertTrue(SessionSignal.sessionExpired.value)
    }

    // Regression: a slow request sent with the OLD token came back 401 after the user had already signed
    // in again, and the interceptor wiped the NEW token and showed "session expired" once more.
    @Test
    fun aLate401ForAnOldTokenKeepsTheNewSession() {
        val store = ConfigStore(FakePrefs()).apply { update { it.copy(accessToken = "T1") } }
        KickoutInterceptor(store).intercept(FakeChain("079012") { store.update { it.copy(accessToken = "T2") } })
        assertEquals("T2", store.current().accessToken)
        assertFalse(SessionSignal.sessionExpired.value)
    }
}
