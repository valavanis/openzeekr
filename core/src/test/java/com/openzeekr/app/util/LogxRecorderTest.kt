package com.openzeekr.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LogxRecorderTest {

    @After fun tearDown() { Logx.recorder = null; Logx.setBle(false); Logx.setHttp(false) }

    // The diagnostic recorder must see the key / proximity trail even with the on-screen switches off,
    // and never the cloud (HTTP) category with its tokens.
    @Test
    fun theRecorderGetsBleAreasRegardlessOfTheSwitchesButNotHttp() {
        val got = mutableListOf<String>()
        Logx.recorder = { _, level, area, msg -> got += "$level/$area $msg" }
        Logx.setBle(false); Logx.setHttp(false)
        Logx.d("prox", "near")
        Logx.w("dk", "0x1012")
        Logx.d("http", "Authorization: secret")
        assertEquals(listOf("D/prox near", "W/dk 0x1012"), got)
    }
}
