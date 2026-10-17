package dev.sketch

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var canvas: SketchView
    private lateinit var menuLayer: FrameLayout
    private lateinit var menu: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val size = (48 * dp).toInt()
        val margin = (8 * dp).toInt()

        canvas = SketchView(this).apply {
            // Pen strokes here are ignored so the button can be tapped
            reservedWidth = size + 2 * margin
            reservedHeight = size + 2 * margin
            onLongPress = { _, _ -> showMenu() }
            load(pageFile())
        }

        val toggle = ModeButton(this).apply {
            setOnClickListener { canvas.toggleMode() }
        }
        canvas.onModeChanged = { toggle.highlighting = it }

        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((2 * dp).toInt(), Color.BLACK)
                cornerRadius = 16 * dp
            }
            addItem("Copy to clipboard") { exportThen { Exporter.copyToClipboard(this@MainActivity, it); "Copied" } }
            addItem("Save as PDF") {
                exportThen { "Saved " + Exporter.saveAsPdf(this@MainActivity, it) + " to Downloads" }
            }
            addItem("Share…") { exportThen { Exporter.share(this@MainActivity, it) } }
            addItem("Email PDF to me") { exportThen { Exporter.emailPdf(it) } }
            addItem("Clear page") {
                canvas.clear()
                Toast.makeText(this@MainActivity, "Cleared", Toast.LENGTH_SHORT).show()
            }
            addItem("Undo") {
                if (!canvas.undo()) Toast.makeText(this@MainActivity, "Nothing to undo", Toast.LENGTH_SHORT).show()
            }
            addItem("Redo") {
                if (!canvas.redo()) Toast.makeText(this@MainActivity, "Nothing to redo", Toast.LENGTH_SHORT).show()
            }
            addItem("Print") { exportThen { Exporter.print(this@MainActivity, it); null } }
        }

        // Full-screen scrim: tapping anywhere outside the menu closes it
        menuLayer = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideMenu() }
            addView(menu, FrameLayout.LayoutParams((240 * dp).toInt(), -2))
        }

        setContentView(FrameLayout(this).apply {
            addView(canvas, FrameLayout.LayoutParams(-1, -1))
            addView(toggle, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                setMargins(margin, margin, margin, margin)
            })
            addView(menuLayer, FrameLayout.LayoutParams(-1, -1))
        })
    }

    private fun LinearLayout.addItem(label: String, onClick: () -> Unit) {
        val dp = resources.displayMetrics.density
        if (childCount > 0) addView(View(context).apply {
            setBackgroundColor(Color.BLACK)
        }, LinearLayout.LayoutParams(-1, (1 * dp).toInt()))
        addView(TextView(context).apply {
            text = label
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            setOnClickListener { hideMenu(); onClick() }
        }, LinearLayout.LayoutParams(-1, (56 * dp).toInt()))
    }

    private fun showMenu() {
        // The SDK would keep drawing under the menu, so the pen is off while it is open
        canvas.pauseInput()
        menuLayer.visibility = View.VISIBLE
        // menuLayer has no size yet on first show (it was GONE), so use the canvas size
        val w = canvas.width
        val h = canvas.height
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST)
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

    private fun pageFile() = java.io.File(filesDir, "page.bin")

    override fun onPause() {
        try {
            canvas.save(pageFile())
        } catch (t: Exception) {
            Toast.makeText(this, "Could not save the sketch", Toast.LENGTH_SHORT).show()
        }
        canvas.pauseInput()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (menuLayer.visibility != View.VISIBLE) canvas.resumeInput()
    }
}
