package com.openzeekr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendToCarParsingTest {

    // Regression: geo: and google.navigation: are OPAQUE URIs. android.net.Uri.getQueryParameter throws
    // UnsupportedOperationException on them, which crashed the whole app (and its BLE key service).
    @Test
    fun geoUriQueryIsReadWithoutTreatingItAsHierarchical() {
        val c = SendToCarParsing.uriCandidates("geo:37.7749,-122.4194?q=37.7749,-122.4194(Office)")
        assertTrue(c.contains("37.7749,-122.4194(Office)"))
        assertTrue(c.contains("37.7749,-122.4194?q=37.7749,-122.4194(Office)"))
    }

    @Test
    fun geoAddressQueryIsDecoded() {
        val c = SendToCarParsing.uriCandidates("geo:0,0?q=1600+Amphitheatre+Parkway%2C+Mountain+View")
        assertTrue(c.contains("1600 Amphitheatre Parkway, Mountain View"))
    }

    @Test
    fun googleNavigationCarriesItsQueryWithoutAQuestionMark() {
        val c = SendToCarParsing.uriCandidates("google.navigation:q=52.3702,4.8952&mode=d")
        assertTrue(c.contains("52.3702,4.8952"))
    }

    @Test
    fun hierarchicalLinksStillYieldTheirQueryAndDestination() {
        val c = SendToCarParsing.uriCandidates("https://www.google.com/maps/dir/?api=1&destination=48.8584,2.2945")
        assertTrue(c.contains("48.8584,2.2945"))
    }

    @Test
    fun malformedEscapesDoNotThrow() {
        val c = SendToCarParsing.uriCandidates("geo:0,0?q=%ZZbad")
        assertEquals(listOf("%ZZbad", "0,0?q=%ZZbad"), c)
    }

    @Test
    fun blankInputHasNoCandidates() {
        assertEquals(emptyList<String>(), SendToCarParsing.uriCandidates(""))
    }
}
