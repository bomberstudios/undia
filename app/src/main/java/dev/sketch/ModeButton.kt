package dev.sketch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Round button showing a small dot (pen) or a larger unstroked circle (highlighter). No animation: e-ink friendly. */
class ModeButton(context: Context) : View(context) {
    private val dp = resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
        color = Color.BLACK
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    var highlighting = false
        set(value) {
            field = value
            contentDescription = if (value) "Highlighter" else "Pen"
            invalidate()
        }

    init {
        isClickable = true
        contentDescription = "Pen"
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (highlighting) {
            fill.color = Color.rgb(0xff, 0xcc, 0x00)
            c.drawCircle(cx, cy, 9 * dp, fill)
        } else {
            fill.color = Color.BLACK
            c.drawCircle(cx, cy, 4 * dp, fill)
        }
    }
}
