package org.naranjanaranja.undia

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * DEBUG BUILDS ONLY: a plain-text log of finger touches and what the app made of them, to tune
 * palm rejection from real use. One line per event in files/taps.log (pull it with adb run-as).
 */
object TapLog {
    private const val MAX_BYTES = 200_000L
    private var file: File? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (BuildConfig.DEBUG) file = File(context.filesDir, "taps.log")
    }

    fun log(line: String) {
        val f = file ?: return
        val stamp = clock.format(Date())
        executor.execute {
            try {
                if (f.length() > MAX_BYTES) f.writeText("")
                f.appendText("$stamp $line\n")
            } catch (t: Exception) {
            }
        }
    }
}
