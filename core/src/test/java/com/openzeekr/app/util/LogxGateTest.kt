package com.openzeekr.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogxGateTest {

    @After fun tearDown() { Logx.setHttp(false); Logx.setBle(false) }

    // Regression: "dkframe" (DK frame plaintext) was in neither category, so turning on only HTTP
    // logging also dumped BLE key frames.
    @Test
    fun dkFramesFollowTheBleSwitchOnly() {
        Logx.setBle(false); Logx.setHttp(true); Logx.clear()
        Logx.d("dkframe", "frame")
        assertTrue(Logx.lines.value.isEmpty())

        Logx.setBle(true)
        Logx.d("dkframe", "frame")
        assertEquals(1, Logx.lines.value.size)
    }
}
