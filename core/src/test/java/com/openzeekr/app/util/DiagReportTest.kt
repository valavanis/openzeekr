package com.openzeekr.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagReportTest {

    @Test
    fun theVinIsMaskedToItsLastFourCharacters() {
        val out = DiagReport.redact("handshake diag: vin=LRWYGCEK1PC000123 dkId=…", listOf("LRWYGCEK1PC000123", ""))
        assertFalse(out.contains("LRWYGCEK1PC000123"))
        assertTrue(out.contains("vin=VIN…0123"))
    }

    @Test
    fun identityDumpsAreDroppedFromTheExportButTheTimelineStays() {
        val lines = listOf(
            "08:00:00.000 D/provision  userId=123456 vin=LRWYGCEK1PC000123 deviceId=abcdef0123",
            "08:00:01.000 D/dk  handshake diag: vin=LRWYGCEK1PC000123 dkId=8000a6af phoneId=0102030405060708",
            "08:00:01.001 D/dk  coefSmall=0a0b0c",
            "08:00:01.002 D/dk  coefBig=0d0e0f",
            "08:00:01.003 D/dk  our cert DER head=3082",
            "08:00:02.000 D/dk  handshake 0/5 DK_STATUS (initState=0) errCode=0x1012 status=00",
        )
        val out = DiagReport.sanitize(lines)
        assertTrue(out.size == 1 && out.single().contains("errCode=0x1012"))
    }

    @Test
    fun theSummaryCountsWhatMattersForTheUnlockAndReconnectIssues() {
        val lines = listOf(
            "2026-10-08 08:00:00.000 W/diag  === process start v0.2 ===",
            "2026-10-08 08:00:01.000 D/ble  presence FIRST_MATCH mac=AA rssi=-80 — waking to connect",
            "2026-10-08 08:00:01.100 D/ble  presence: cached BB not among [AA] - address rotated, fresh filtered scan",
            "2026-10-08 08:00:03.000 D/dk  handshake 0/5 DK_STATUS (initState=0) errCode=0x1012 status=00",
            "2026-10-08 08:00:05.000 E/ble  DK handshake: DK reconnect (0x1012 authenticated) flow not implemented yet",
            "2026-10-08 08:00:09.000 D/dk  handshake 0/5 DK_STATUS (initState=0) errCode=0x1011 status=00",
            "2026-10-08 08:00:10.000 D/ble  DK session READY",
            "2026-10-08 08:00:11.000 D/prox  approach-unlock held (rssi=-60 prevZone=NEAR): no approach - the phone hasn't moved recently",
            "2026-10-08 08:00:12.000 D/prox  approach-unlock ARM (rssi=-58 ~1.0m prevZone=FAR) — confirmed-unlock loop",
            "2026-10-08 08:00:12.500 D/prox  unlock attempt #1 -> CONFIRMED",
        )
        val s = DiagReport.summarize(lines)
        listOf(
            "process starts: 1", "presence wakes: 1", "cached address rotated: 1",
            "0x1012: 1", "0x1011: 1", "sessions ready: 1", "approach unlock armed: 1",
            "no approach - the phone hasn't moved recently: 1", "CONFIRMED: 1",
        ).forEach { assertTrue("missing '$it' in:\n$s", s.contains(it)) }
    }
}
