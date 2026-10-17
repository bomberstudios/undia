package com.bomberstudios.undia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A month grid for picking a day up to today. Drawn by hand so it can use Zodiak (the system
 * CalendarView ignores custom fonts) and so it has no animations, which suits e-ink.
 */
class CalendarGrid(context: Context) : View(context) {
    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val headerH = 56 * dp
    private val weekdayH = 32 * dp
    private val rowH = 44 * dp
    // 1 em of the day numbers (18 sp) of space on each side
    private val sidePad = 18 * sp
    private val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy")

    private var month = YearMonth.now()
    private var selected = LocalDate.now()
    private var today = LocalDate.now()

    /** Called when a day is tapped. */
    var onPick: ((LocalDate) -> Unit)? = null

    private fun textPaint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size * sp
        textAlign = Paint.Align.CENTER
        typeface = resources.getFont(R.font.zodiak)
        fontFeatureSettings = "onum" // old-style figures, when the font has them
    }

    private val titlePaint = textPaint(18f, Color.BLACK)
    private val weekdayPaint = textPaint(14f, Color.rgb(0x66, 0x66, 0x66))
    private val dayPaint = textPaint(18f, Color.BLACK)
    private val futurePaint = textPaint(18f, Color.rgb(0xbb, 0xbb, 0xbb))
    private val selectedPaint = textPaint(18f, Color.WHITE)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xff, 0x66, 0x00) } // selected day, #ff6600
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
    }

    init {
        isClickable = true
    }

    /** Shows the month of [selectedDay]. */
    fun show(selectedDay: LocalDate, todayDate: LocalDate) {
        selected = selectedDay
        today = todayDate
        month = YearMonth.from(selectedDay)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(w),
            (headerH + weekdayH + rows() * rowH).toInt()
        )
    }

    /** How many columns the first of the month is in from the start of the week. */
    private fun offset() = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7

    /** 4 to 6, however many weeks the month spans: no empty rows. */
    private fun rows() = (offset() + month.lengthOfMonth() + 6) / 7

    private fun cellWidth() = (width - 2 * sidePad) / 7f

    private fun textBaseline(p: Paint, centerY: Float) = centerY - (p.ascent() + p.descent()) / 2

    override fun onDraw(c: Canvas) {
        val canNext = month.isBefore(YearMonth.from(today))
        c.drawText(month.format(monthFormat), width / 2f, textBaseline(titlePaint, headerH / 2), titlePaint)
        c.drawText("‹", sidePad + headerH / 2, textBaseline(titlePaint, headerH / 2), titlePaint)
        c.drawText("›", width - sidePad - headerH / 2, textBaseline(titlePaint, headerH / 2), if (canNext) titlePaint else futurePaint)

        val cw = cellWidth()
        for (i in 0 until 7) {
            val d = firstDay.plus(i.toLong())
            c.drawText(
                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                sidePad + cw * i + cw / 2, textBaseline(weekdayPaint, headerH + weekdayH / 2), weekdayPaint
            )
        }

        val offset = offset()
        for (day in 1..month.lengthOfMonth()) {
            val cell = offset + day - 1
            val cx = sidePad + cw * (cell % 7) + cw / 2
            val cy = headerH + weekdayH + rowH * (cell / 7) + rowH / 2
            val date = month.atDay(day)
            val r = minOf(cw, rowH) / 2 - 3 * dp
            val paint = when {
                date.isAfter(today) -> futurePaint
                date == selected -> {
                    c.drawCircle(cx, cy, r, fill)
                    selectedPaint
                }
                else -> {
                    if (date == today) c.drawCircle(cx, cy, r, ring)
                    dayPaint
                }
            }
            c.drawText(day.toString(), cx, textBaseline(paint, cy), paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val x = e.x
        val y = e.y
        if (y < headerH) {
            when {
                x < sidePad + headerH -> month = month.minusMonths(1)
                x > width - sidePad - headerH && month.isBefore(YearMonth.from(today)) -> month = month.plusMonths(1)
            }
            requestLayout() // the number of rows can change
            invalidate()
            return true
        }
        if (y < headerH + weekdayH) return true
        val col = ((x - sidePad) / cellWidth()).toInt().coerceIn(0, 6)
        val row = ((y - headerH - weekdayH) / rowH).toInt()
        val day = row * 7 + col - offset() + 1
        if (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            if (!date.isAfter(today)) onPick?.invoke(date)
        }
        return true
    }
}
