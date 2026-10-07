package com.openzeekr.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagLogFileTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun linesSurviveAReopen() {
        val dir = tmp.newFolder("diag")
        DiagLogFile(dir).apply { append("a"); append("b"); close() }
        assertEquals(listOf("a", "b"), DiagLogFile(dir).readAll())
    }

    // Bounded: at most ~2 x maxBytes on disk, oldest data dropped first, order preserved.
    @Test
    fun itRotatesAndKeepsTheNewestLinesInOrder() {
        val log = DiagLogFile(tmp.newFolder("diag"), maxBytes = 100)
        (1..60).forEach { log.append("line %03d".format(it)) }
        val all = log.readAll()
        assertTrue(log.sizeBytes() <= 2 * 100 + 20)
        assertEquals("line 060", all.last())
        assertEquals(all.sorted(), all)
        assertTrue(all.size < 60)
    }

    @Test
    fun clearRemovesEverything() {
        val log = DiagLogFile(tmp.newFolder("diag"))
        log.append("x")
        log.clear()
        assertEquals(emptyList<String>(), log.readAll())
        assertEquals(0L, log.sizeBytes())
    }
}
