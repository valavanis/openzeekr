package com.openzeekr.app.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Persistent diagnostic recording for a problem that shows up at the car (e.g. "connects but doesn't
 * unlock", a 0x1012 reconnect): while on, every key / proximity / BLE log line ([Logx.recorder]) goes to a
 * bounded file in app-private storage, with the date, across process restarts (the presence wake-up
 * restarts the process, which would wipe the in-memory log). It turns itself off after the chosen window.
 *
 * What it never contains: the digital key (redacted at the source, see DkFrameLog), session keys,
 * passwords or cloud tokens (the HTTP category isn't recorded). The VIN is masked when exported.
 */
object DiagRecorder {

    /** Default recording window: long enough to cover "tomorrow at the car". */
    const val DEFAULT_WINDOW_MS = 48L * 60 * 60 * 1000

    data class State(val recording: Boolean = false, val untilMs: Long = 0L)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while recording (and not past its window). */
    val isRecording: Boolean get() = _state.value.let { it.recording && System.currentTimeMillis() < it.untilMs }

    // One writer thread: disk I/O never runs on the BLE callback / main threads, and the date format
    // (not thread-safe) is only ever used here.
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "oz-diag").apply { isDaemon = true } }
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var file: DiagLogFile? = null
    private val thinner = SampleThinner()

    /** Call once per process (Application.onCreate): resumes a recording that is still in its window. */
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        file = DiagLogFile(File(app.filesDir, "diag"))
        val until = prefs?.getLong(KEY_UNTIL, 0L) ?: 0L
        if (until > System.currentTimeMillis()) install(until) else prefs?.edit()?.remove(KEY_UNTIL)?.apply()
    }

    fun start(windowMs: Long = DEFAULT_WINDOW_MS) {
        val until = System.currentTimeMillis() + windowMs
        prefs?.edit()?.putLong(KEY_UNTIL, until)?.apply()
        install(until)
        Logx.w("diag", "=== diagnostic recording started (until ${Date(until)}) ===")
    }

    fun stop() {
        if (isRecording) Logx.w("diag", "=== diagnostic recording stopped ===")
        disable()
    }

    /** Recorded size on disk (bytes); waits for pending writes. */
    fun sizeBytes(): Long = runCatching { io.submit<Long> { file?.sizeBytes() ?: 0L }.get() }.getOrDefault(0L)

    /** All recorded lines, oldest first; waits for pending writes. */
    fun lines(): List<String> = runCatching { io.submit<List<String>> { file?.readAll().orEmpty() }.get() }.getOrDefault(emptyList())

    fun clear() { io.execute { file?.clear() } }

    // Never logs: called from [record] (inside a Logx call) when the window is over.
    private fun disable() {
        prefs?.edit()?.remove(KEY_UNTIL)?.apply()
        Logx.recorder = null
        _state.value = State()
    }

    private fun install(until: Long) {
        _state.value = State(recording = true, untilMs = until)
        Logx.recorder = ::record
    }

    private fun record(tsMs: Long, level: Char, area: String, msg: String) {
        if (tsMs >= _state.value.untilMs) { disable(); return }   // window over: turn ourselves off
        if (!thinner.keep(tsMs, area, msg)) return
        io.execute { runCatching { file?.append("${stamp.format(Date(tsMs))} $level/$area  $msg") } }
    }

    private const val PREFS = "diag_recorder"
    private const val KEY_UNTIL = "until_ms"
}
