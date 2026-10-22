package com.bomberstudios.undia

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : Activity() {
    private companion object {
        // How round the corners of the menu and the About window are
        const val PANEL_CORNER_DP = 8
    }

    private lateinit var canvas: UndiaView
    private lateinit var menuLayer: FrameLayout
    private lateinit var menu: ScrollView
    private lateinit var calendar: CalendarGrid
    private lateinit var backToToday: View
    private lateinit var aboutLayer: FrameLayout
    private lateinit var aboutCard: ScrollView
    // Shows the current tool. It is not a button: a one-finger tap anywhere switches pen/highlighter.

    private var today = LocalDate.now()
    private val handler = Handler(Looper.getMainLooper())
    private val rolloverRunnable = Runnable { checkRollover(); scheduleRollover() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val size = (48 * dp).toInt()
        val margin = (8 * dp).toInt()

        canvas = UndiaView(this).apply {
            // The pen is ignored only around the mark itself (the button is 48 dp, the mark is smaller)
            val markBox = 0 // nothing to protect: the indicator isn't touchable, so the pen can draw under it
            reservedWidth = markBox
            reservedHeight = markBox
            reservedInsetRight = margin + (size - markBox) / 2
            reservedInsetTop = margin + (size - markBox) / 2
            onLongPress = { _, _ -> showMenu() }
        }

        calendar = CalendarGrid(this).apply {
            onPick = { picked ->
                hideMenu()
                if (picked != canvas.day) canvas.showDay(picked, today)
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(calendar, LinearLayout.LayoutParams(-1, -2))
            addItem(this, "Copy to clipboard") {
                val bitmap = canvas.snapshot() ?: return@addItem
                runExport { Exporter.copyToClipboard(this@MainActivity, bitmap); "Copied" }
            }
            addItem(this, "Save as PDF") {
                val page = canvas.pageSnapshot() ?: return@addItem
                runExport { "Saved " + Exporter.saveAsPdf(this@MainActivity, page) + " to Downloads" }
            }
            addItem(this, "Share…") {
                val page = canvas.pageSnapshot() ?: return@addItem
                runExport { Exporter.share(this@MainActivity, page) }
            }
            addItem(this, "About") { showAbout() }
            backToToday = addItem(this, "Back to today") { canvas.showDay(today, today) }
        }

        // The menu scrolls if the calendar plus the items don't fit the screen
        menu = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((2 * dp).toInt(), Color.BLACK)
                cornerRadius = PANEL_CORNER_DP * dp
            }
            clipToOutline = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }

        // The menu changes height when the calendar changes month: keep it centered
        menu.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // On a short screen (a tablet held sideways) the menu would be taller than the screen. It is
            // given its full natural height, then shrunk around its center until it fits.
            content.measure(
                View.MeasureSpec.makeMeasureSpec(menuWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val natural = content.measuredHeight
            val lp = menu.layoutParams
            if (natural > 0 && lp.height != natural) {
                lp.height = natural
                menu.layoutParams = lp
                menu.post { menu.requestLayout() } // a request made during layout is not always honored
            }
            val fit = (canvas.height * 0.9f / natural.coerceAtLeast(1)).coerceAtMost(1f)
            menu.scaleX = fit
            menu.scaleY = fit
            menu.translationX = ((canvas.width - menu.width) / 2f).coerceAtLeast(0f) - menu.left
            menu.translationY = ((canvas.height - menu.height) / 2f).coerceAtLeast(0f) - menu.top
        }

        // Full-screen scrim: tapping anywhere outside the menu closes it
        menuLayer = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideMenu() }
            addView(menu, FrameLayout.LayoutParams(menuWidth(), -2).apply {
                val m = resources.displayMetrics.heightPixels * 5 / 100 // never touches the edges
                topMargin = m
                bottomMargin = m
            })
        }

        // The About text fits the screen at the normal font size. With a bigger one it scrolls.
        aboutCard = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((2 * dp).toInt(), Color.BLACK)
                cornerRadius = PANEL_CORNER_DP * dp
            }
            clipToOutline = true
            isVerticalScrollBarEnabled = false
            addView(buildAboutCard(dp))
        }
        aboutLayer = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideAbout() } // tap anywhere to close
            addView(aboutCard, FrameLayout.LayoutParams(aboutWidth(), -2, Gravity.CENTER).apply {
                val m = resources.displayMetrics.heightPixels * 5 / 100 // never touches the edges
                topMargin = m
                bottomMargin = m
            })
        }

        setContentView(FrameLayout(this).apply {
            addView(canvas, FrameLayout.LayoutParams(-1, -1))
            addView(menuLayer, FrameLayout.LayoutParams(-1, -1))
            addView(aboutLayer, FrameLayout.LayoutParams(-1, -1))
        })

        val pages = File(filesDir, "pages").apply { mkdirs() }
        canvas.pagesDir = pages
        canvas.showDay(today, today, refresh = false)
    }

    /**
     * Text follows the system font size (sp). The panels follow it too, so that bigger text has
     * room: they get wider (up to the screen) and the rows taller. Smaller than normal changes nothing.
     */
    private val fontBoost: Float get() = resources.configuration.fontScale.coerceAtLeast(1f)

    private fun menuWidth() = minOf(
        (320 * resources.displayMetrics.density * fontBoost).toInt(),
        resources.displayMetrics.widthPixels * 9 / 10
    )

    private fun aboutWidth() = minOf(
        (460 * resources.displayMetrics.density * fontBoost).toInt(),
        resources.displayMetrics.widthPixels * 9 / 10
    )

    private fun buildAboutCard(dp: Float): LinearLayout {
        val zodiak = resources.getFont(R.font.zodiak)
        val zodiakItalic = resources.getFont(R.font.zodiak_italic)
        val zodiakBold = resources.getFont(R.font.zodiak_bold)
        fun text(value: String, sizeSp: Float, gravity: Int, topDp: Int, color: Int = Color.BLACK, face: Typeface = zodiak) =
            TextView(this).apply {
                text = value
                textSize = sizeSp
                typeface = face
                fontFeatureSettings = "onum"
                setTextColor(color)
                this.gravity = gravity
                setLineSpacing(0f, 1.05f)
                breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED // like CSS text-wrap: balance
                hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (topDp * dp).toInt() }
            }

        val version = packageManager.getPackageInfo(packageName, 0).versionName
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (54 * dp).toInt()
            setPadding(pad, pad, pad, pad) // the same on every side; wide sides keep the lines short
            addView(ImageView(context).apply {
                setImageDrawable(getDrawable(R.mipmap.ic_launcher))
                contentDescription = "undia icon"
            }, LinearLayout.LayoutParams((88 * dp).toInt(), (88 * dp).toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(text("undia", 28f, Gravity.CENTER, 8, face = zodiakBold))
            addView(text("The notebook that does LESS", 17f, Gravity.CENTER, 0, face = zodiakItalic))
            addView(text("Version $version · built ${BuildConfig.BUILD_DATE}", 14f, Gravity.CENTER, 6, Color.rgb(0x44, 0x44, 0x44)))
            addView(text("undia is a half notebook, half philosophy manifesto:", 17f, Gravity.CENTER, 18))
            addView(text(
                "You get one page per day. You can draw, but you can’t erase or undo. Regret is not your friend (and it’s overrated anyway).",
                17f, Gravity.CENTER, 10
            ))
            addView(text(
                "You can revisit old pages, but you can’t rewrite the past (well, you can try, but it won’t work). Skipped a day? You’ll get a random koan so you can still have something to meditate about.",
                17f, Gravity.CENTER, 10
            ))
            addView(text(
                "undia does less by giving you one pen and one highlighter. Toggle between them using the mark at the top right (or a three finger tap). Swipe to flip pages, double tap to come back to today, and hold for a menu.",
                17f, Gravity.CENTER, 10
            ))
            addView(text("Made with love by Ale Muñoz", 13f, Gravity.CENTER, 20, Color.rgb(0x44, 0x44, 0x44)))
        }
    }

    private fun showAbout() {
        canvas.pauseInput()
        aboutLayer.visibility = View.VISIBLE
    }

    private fun hideAbout() {
        if (aboutLayer.visibility != View.VISIBLE) return
        val area = Rect(aboutCard.left, aboutCard.top, aboutCard.right, aboutCard.bottom)
        aboutLayer.visibility = View.GONE
        canvas.resumeInput()
        canvas.repaint(area)
    }

    private fun addItem(parent: LinearLayout, label: String, onClick: () -> Unit): View {
        val dp = resources.displayMetrics.density
        parent.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(-1, (1 * dp).toInt()))
        return TextView(this).apply {
            text = label
            textSize = 18f
            typeface = resources.getFont(R.font.zodiak)
            fontFeatureSettings = "onum" // old-style figures, when the font has them
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL
            // The padding makes the row: 17.2 dp above and below the text, which with the text at the
            // usual font size is the old 52 dp row. So more padding means a taller row, and so does
            // bigger text. The bottom gets a little extra, which lifts the text: Zodiak's metrics
            // leave it sitting low (1.07 dp measured, plus 1 dp by eye). 48 dp is the least a touch
            // target should be.
            minimumHeight = (48 * dp).toInt()
            setPadding((16 * dp).toInt(), (17.2f * dp).toInt(), (16 * dp).toInt(), ((17.2f + 1.07f + 1) * dp).toInt())
            setOnClickListener { hideMenu(); onClick() }
        }.also { parent.addView(it, LinearLayout.LayoutParams(-1, -2)) }
    }

    private fun showMenu() {
        // The SDK would keep drawing under the menu, so the pen is off while it is open
        canvas.pauseInput()

        calendar.show(canvas.day, today)
        backToToday.visibility = if (canvas.day == today) View.GONE else View.VISIBLE

        menuLayer.visibility = View.VISIBLE
        // menuLayer has no size yet on first show (it was GONE), so use the canvas size
        val w = canvas.width
        val h = canvas.height
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(menuWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h * 9 / 10, View.MeasureSpec.AT_MOST)
        )
        menu.translationX = ((w - menu.measuredWidth) / 2f).coerceAtLeast(0f) - menu.left
        menu.translationY = ((h - menu.measuredHeight) / 2f).coerceAtLeast(0f) - menu.top
    }

    private fun hideMenu() {
        if (menuLayer.visibility != View.VISIBLE) return
        val area = Rect(
            (menu.left + menu.translationX).toInt(), (menu.top + menu.translationY).toInt(), 0, 0
        )
        area.right = area.left + menu.width
        area.bottom = area.top + menu.height
        menuLayer.visibility = View.GONE
        canvas.resumeInput()
        canvas.repaint(area)
    }

    /** Runs [action] off the main thread; a non-null result is shown as a toast. */
    private fun runExport(action: () -> String?) {
        Thread {
            val message = try {
                action()
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
        if (menuLayer.visibility != View.VISIBLE && aboutLayer.visibility != View.VISIBLE) canvas.resumeInput()
    }
}
