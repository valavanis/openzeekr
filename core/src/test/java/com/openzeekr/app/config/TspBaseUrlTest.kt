package com.openzeekr.app.config

import com.openzeekr.app.config.SecretsConfig.Companion.tspBaseOrDefault
import org.junit.Assert.assertEquals
import org.junit.Test

class TspBaseUrlTest {

    private val fallback = "https://eu-snc-tsp-api-gw.zeekrlife.com"

    @Test
    fun aValidBaseIsNormalisedWithOneTrailingSlash() {
        assertEquals("https://my-gw.example.com/", tspBaseOrDefault(" https://my-gw.example.com// ", fallback))
    }

    // Regression: Retrofit.baseUrl throws on an invalid URL. The Settings field rebuilt the client on
    // every keystroke, so clearing it (or typing "https:/") crashed the app - and, the value being
    // persisted, crashed every later launch in Application.onCreate.
    @Test
    fun anInvalidOrBlankBaseFallsBackToTheRegionDefault() {
        assertEquals("$fallback/", tspBaseOrDefault("", fallback))
        assertEquals("$fallback/", tspBaseOrDefault("https:/", fallback))
        assertEquals("$fallback/", tspBaseOrDefault("eu-snc-tsp-api-gw.zeekrlife.com", fallback))
        assertEquals("$fallback/", tspBaseOrDefault("ftp://host.example.com", fallback))
    }
}
