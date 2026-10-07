package com.openzeekr.app.util

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Append-only diagnostic log on disk, bounded by rotation: `diag-current.log` grows to [maxBytes], then
 * becomes `diag-previous.log` (replacing the older one), so at most about 2 x [maxBytes] is kept and the
 * oldest lines go first. Each line is flushed as it is written, so a killed process loses nothing.
 * Plain java.io (no Android): unit-tested in DiagLogFileTest.
 */
class DiagLogFile(private val dir: File, private val maxBytes: Long = MAX_BYTES) {

    private val current = File(dir, "diag-current.log")
    private val previous = File(dir, "diag-previous.log")
    private var writer: BufferedWriter? = null

    @Synchronized
    fun append(line: String) {
        val w = writer ?: run { dir.mkdirs(); BufferedWriter(FileWriter(current, true)).also { writer = it } }
        w.write(line)
        w.newLine()
        w.flush()
        if (current.length() > maxBytes) rotate()
    }

    /** Every kept line, oldest first. */
    @Synchronized
    fun readAll(): List<String> = listOf(previous, current).filter { it.exists() }.flatMap { it.readLines() }

    @Synchronized
    fun sizeBytes(): Long = listOf(previous, current).filter { it.exists() }.sumOf { it.length() }

    @Synchronized
    fun clear() {
        close()
        current.delete()
        previous.delete()
    }

    @Synchronized
    fun close() {
        runCatching { writer?.close() }
        writer = null
    }

    private fun rotate() {
        close()
        previous.delete()
        current.renameTo(previous)
    }

    companion object {
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}
