package com.openzeekr.app.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConfigStoreTest {

    private fun store() = ConfigStore(FakePrefs())

    // Regression: signOut() promised to wipe "every session token" but kept the user-center (azure)
    // token and the xchanger session, which kept re-registering pushes for the signed-out account.
    @Test
    fun signOutClearsEverySessionToken() {
        val s = store()
        s.update {
            it.copy(accessToken = "tsp", azureToken = "azure", xchangerToken = "xch", xchangerClientId = "cid",
                email = "a@b.c", password = "pw", userId = "42", accountUuid = "uuid", vin = "VIN1")
        }
        s.signOut()
        val c = s.current()
        listOf(c.accessToken, c.azureToken, c.xchangerToken, c.xchangerClientId, c.email, c.password,
            c.userId, c.accountUuid, c.vin).forEach { assertEquals("", it) }
    }

    // Regression: importJson silently dropped the documented overseas_* inbox keys.
    @Test
    fun importReadsTheDocumentedOverseasKeys() {
        val s = store()
        // A configured install (import validates strictly: the mandatory app keys must be present).
        s.update { it.copy(hmacAccessKey = "a", hmacSecretKey = "b", passwordPublicKey = "c", prodSecret = "d") }
        val r = s.importJson("""{"overseas_access_key":"AK","overseas_secret_key":"SK","inbox_auth_secret":"IS"}""")
        assertTrue(r.isSuccess)
        assertEquals("AK", s.current().overseasAccessKey)
        assertEquals("SK", s.current().overseasSecretKey)
        assertEquals("IS", s.current().inboxAuthSecret)
    }

    // Regression: the export (copied to the clipboard) carried the account password and live tokens.
    @Test
    fun exportCarriesNoPasswordOrSessionToken() {
        val s = store()
        s.update { it.copy(password = "pw", accessToken = "tsp", azureToken = "azure", xchangerToken = "xch", prodSecret = "keep") }
        val obj = Json.parseToJsonElement(s.exportJson()).jsonObject
        listOf("password", "accessToken", "azureToken", "xchangerToken").forEach { k ->
            assertFalse("$k must not be exported", obj[k].toString().trim('"').isNotEmpty())
        }
        assertEquals("\"keep\"", obj["prod_secret"].toString())
    }

    // Regression: update() was an unsynchronized read-modify-write called from OkHttp, IO and Main
    // threads, so concurrent updates were lost (e.g. a freshly stored token overwritten).
    @Test
    fun concurrentUpdatesAreNotLost() {
        val s = store()
        val threads = 8; val perThread = 250
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        repeat(threads) {
            pool.execute {
                start.await()
                repeat(perThread) { s.update { c -> c.copy(calibInsideRssi = c.calibInsideRssi + 1) } }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(60, TimeUnit.SECONDS)
        assertEquals(threads * perThread, s.current().calibInsideRssi)
    }
}
