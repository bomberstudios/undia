package dev.sketch

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.CalendarView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var canvas: SketchView
    private lateinit var menuLayer: FrameLayout
    private lateinit var menu: ScrollView
    private lateinit var calendar: CalendarView
    private lateinit var backToToday: View
    private lateinit var dateLabel: TextView
    private lateinit var modeButton: ModeButton

    private var today = LocalDate.now()
    private val handler = Handler(Looper.getMainLooper())
    private val rolloverRunnable = Runnable { checkRollover(); scheduleRollover() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val size = (48 * dp).toInt()
        val margin = (8 * dp).toInt()
        val labelWidth = (168 * dp).toInt()

        canvas = SketchView(this).apply {
            // Pen strokes in this corner are ignored so the button can be tapped (the label sits there too)
            reservedWidth = size + 2 * margin + labelWidth
            reservedHeight = size + 2 * margin
            onLongPress = { _, _ -> showMenu() }
        }

        modeButton = ModeButton(this).apply {
            setOnClickListener { canvas.toggleMode() }
        }
        canvas.onModeChanged = { modeButton.highlighting = it }

        dateLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        canvas.onDayChanged = { day, editable ->
            val text = day.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))
            dateLabel.text = if (editable) text else "$text · read only"
            modeButton.visibility = if (editable) View.VISIBLE else View.INVISIBLE
        }

        calendar = CalendarView(this).apply {
            setOnDateChangeListener { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                hideMenu()
                if (picked != canvas.day) canvas.showDay(picked, today)
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(calendar, LinearLayout.LayoutParams(-1, (320 * dp).toInt()))
            addItem(this, "Copy to clipboard") { exportThen { Exporter.copyToClipboard(this@MainActivity, it); "Copied" } }
            addItem(this, "Save as PDF") {
                exportThen { "Saved " + Exporter.saveAsPdf(this@MainActivity, it) + " to Downloads" }
            }
            addItem(this, "Share…") { exportThen { Exporter.share(this@MainActivity, it) } }
            addItem(this, "Print") { exportThen { Exporter.print(this@MainActivity, it); null } }
            backToToday = addItem(this, "Back to today") { canvas.showDay(today, today) }
        }

        // The menu scrolls if the calendar plus the items don't fit the screen
        menu = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((2 * dp).toInt(), Color.BLACK)
                cornerRadius = 16 * dp
            }
            clipToOutline = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }

        // Full-screen scrim: tapping anywhere outside the menu closes it
        menuLayer = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideMenu() }
            addView(menu, FrameLayout.LayoutParams((320 * dp).toInt(), -2))
        }

        setContentView(FrameLayout(this).apply {
            addView(canvas, FrameLayout.LayoutParams(-1, -1))
            addView(dateLabel, FrameLayout.LayoutParams(labelWidth, size, Gravity.TOP or Gravity.END).apply {
                setMargins(0, margin, size + 2 * margin, 0)
            })
            addView(modeButton, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins(margin, margin, margin, margin)
            })
            addView(menuLayer, FrameLayout.LayoutParams(-1, -1))
        })

        // Notes from before there was one page per day become today's page
        val pages = File(filesDir, "pages").apply { mkdirs() }
        val legacy = File(filesDir, "page.bin")
        val todayFile = File(pages, "$today.bin")
        if (legacy.exists() && !todayFile.exists()) legacy.renameTo(todayFile)

        canvas.pagesDir = pages
        canvas.showDay(today, today, refresh = false)
    }

    private fun addItem(parent: LinearLayout, label: String, onClick: () -> Unit): View {
        val dp = resources.displayMetrics.density
        parent.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(-1, (1 * dp).toInt()))
        return TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            setOnClickListener { hideMenu(); onClick() }
        }.also { parent.addView(it, LinearLayout.LayoutParams(-1, (52 * dp).toInt())) }
    }

    private fun showMenu() {
        // The SDK would keep drawing under the menu, so the pen is off while it is open
        canvas.pauseInput()

        // Only days up to today can be opened
        val zone = ZoneId.systemDefault()
        calendar.maxDate = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        calendar.setDate(canvas.day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), false, true)
        backToToday.visibility = if (canvas.day == today) View.GONE else View.VISIBLE

        menuLayer.visibility = View.VISIBLE
        // menuLayer has no size yet on first show (it was GONE), so use the canvas size
        val w = canvas.width
        val h = canvas.height
        menu.measure(
            View.MeasureSpec.makeMeasureSpec((320 * resources.displayMetrics.density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h * 9 / 10, View.MeasureSpec.AT_MOST)
        )
        menu.translationX = ((w - menu.measuredWidth) / 2f).coerceAtLeast(0f)
        menu.translationY = ((h - menu.measuredHeight) / 2f).coerceAtLeast(0f)
    }

    private fun hideMenu() {
        if (menuLayer.visibility != View.VISIBLE) return
        val area = Rect(menu.translationX.toInt(), menu.translationY.toInt(), 0, 0)
        area.right = area.left + menu.width
        area.bottom = area.top + menu.height
        menuLayer.visibility = View.GONE
        canvas.resumeInput()
        canvas.repaint(area)
    }

    /** Runs [action] on a snapshot off the main thread; a non-null result is shown as a toast. */
    private fun exportThen(action: (android.graphics.Bitmap) -> String?) {
        val bitmap = canvas.snapshot() ?: return
        Thread {
            val message = try {
                action(bitmap)
            } catch (t: Throwable) {
                "Failed: " + (t.message ?: t.javaClass.simpleName)
            }
            if (message != null) runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        }.start()
    }

    /** At 00:00 a new page starts. Whoever was looking at the page of the day that ended moves to it. */
    private fun checkRollover() {
        val now = LocalDate.now()
        if (now == today) return
        val followingToday = canvas.day == today
        today = now
        canvas.showDay(if (followingToday) now else canvas.day, now)
    }

    private fun scheduleRollover() {
        handler.removeCallbacks(rolloverRunnable)
        val now = LocalDateTime.now()
        val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
        handler.postDelayed(rolloverRunnable, untilMidnight + 1000)
    }

    override fun onPause() {
        handler.removeCallbacks(rolloverRunnable)
        try {
            canvas.save()
        } catch (t: Exception) {
            Toast.makeText(this, "Could not save the page", Toast.LENGTH_SHORT).show()
        }
        canvas.pauseInput()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        checkRollover() // the date may have changed while the app was in the background
        scheduleRollover()
        if (menuLayer.visibility != View.VISIBLE) canvas.resumeInput()
    }
}
