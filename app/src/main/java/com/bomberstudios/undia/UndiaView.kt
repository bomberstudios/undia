package com.bomberstudios.undia

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.pen.style.StrokeStyle

/**
 * Blank white canvas with a fixed-width 0.2 mm pen; a one-finger tap toggles pen/highlighter. The Onyx SDK draws the live
 * stroke on the EPD; we mirror finished strokes into a bitmap so they survive
 * surface redraws.
 *
 * One page per day: today's page can be drawn on and never erased; past days are read-only,
 * and a past day with no drawing shows a koan.
 */
class UndiaView(context: Context) : SurfaceView(context) {
    private companion object {
        const val PEN_WIDTH_MM = 0.2f
        const val HIGHLIGHT_WIDTH_MM = 4f
        val DATE_COLOR = Color.rgb(0xff, 0x66, 0x00) // #ff6600
        // Ink on a past page is temporary: gray and dotted
        val TEMP_COLOR = Color.rgb(0x88, 0x88, 0x88)
        const val TEMP_DOT_WIDTH_PX = 3.5f
        const val TEMP_DOT_GAP_PX = 8f
        // Temporary ink gets lighter every FADE_STEP_MS and is gone after FADE_STEPS more steps
        const val FADE_STEP_MS = 1_000L
        const val FADE_STEPS = 5
        const val HIGHLIGHT_HEX = PageFile.HIGHLIGHT_HEX
        val HIGHLIGHT_COLOR = Color.parseColor(HIGHLIGHT_HEX)
        const val TAP_MAX_MS = 600L
        // After a highlight, how long before the pen comes back (resets with each new highlight)
        const val REVERT_TO_PEN_MS = 800L
        const val PEN_GUARD_MS = 800L // finger gestures are ignored this long after the pen was last near
        const val PALM_MM = 15f // a touch wider than this is a palm, not a fingertip
        const val TAP_MAX_SINGLE_MS = 250L // longest touch that still counts as a tap
        const val SWIPE_MAX_MS = 800L
        const val SWIPE_MIN_FRACTION = 0.25f // of the page width
        const val ERASER_RADIUS_PX = 24f // debug builds only
        const val CURSOR_INTERVAL_MS = 30L // how often the eraser ring may move on screen
        const val KOAN_LINE_HEIGHT_EM = 1.2f // baseline to baseline, in font sizes
        const val SAVE_DELAY_MS = 3000L
        const val MIN_SEGMENT_PX = 3f
        // Pause after the last stroke before it is repainted smooth
        // How long the pen must be away (not drawing, not hovering) before ink is repainted smooth
        const val SMOOTH_DELAY_MS = 500L
        const val QUICK_REPAINT_MS = 100L // for repaints that aren't ink: a closed menu, a fade step
        const val MERGE_MARGIN_PX = 48 // areas this close are repainted together
        const val MAX_AREAS = 8
    }

    /**
     * The panel's real pixels per inch, as the device reports it (300 on the Go 6, about 227 on the
     * Go 10.3), so the pen is 0.2 mm wide on any Boox. Falls back to the density if it looks wrong.
     */
    private val ppi: Float = resources.displayMetrics.let { m ->
        val real = (m.xdpi + m.ydpi) / 2
        if (real in 100f..600f) real else m.densityDpi.toFloat()
    }
    private val penWidthPx = PEN_WIDTH_MM / 25.4f * ppi // about 2.4 px at 300 ppi
    private val highlightWidthPx = HIGHLIGHT_WIDTH_MM / 25.4f * ppi // about 47 px at 300 ppi

    private class Stroke(highlight: Boolean, xy: FloatArray) : InkStroke(highlight, xy) {
        // Only used by temporary strokes: when it was drawn and how faded it is (0 = fresh)
        val born: Long = SystemClock.elapsedRealtime()
        var level = 0

        // DEBUG BUILDS ONLY: under the eraser, drawn lighter, removed when the pen lifts
        var marked = false
    }

    // Guarded by synchronized(this), like the bitmap
    private val strokes = ArrayList<Stroke>()
    // Scribbles on past pages: shown, never saved, gone when the page changes
    private val tempStrokes = ArrayList<Stroke>()

    /** Where the daily pages (yyyy-MM-dd.svg) live. Set before [showDay]. */
    var pagesDir: File? = null

    /** The day being shown, and the current calendar day; only today's page is editable. */
    var day: LocalDate = LocalDate.now()
        private set
    private var today: LocalDate = LocalDate.now()
    val editable: Boolean get() = day == today

    /** Called on the main thread when the shown day changes. */
    var onDayChanged: ((day: LocalDate, editable: Boolean) -> Unit)? = null

    private var dirty = false

    // A page is being read from disk: its strokes are not here yet, so nothing is drawn or saved
    @Volatile private var loading = false
    private var loadId = 0
    private val loadExecutor = Executors.newSingleThreadExecutor()
    private val saveLock = Any()
    private val saveExecutor = Executors.newSingleThreadExecutor()
    private val saveRunnable = Runnable { saveExecutor.execute { try { save() } catch (t: Exception) {} } }

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private var rawOpen = false

    private val main = Handler(Looper.getMainLooper())
    // Areas waiting for a clean repaint; guarded by itself
    private val pendingAreas = ArrayList<Rect>()
    @Volatile private var smoothIdleMs = QUICK_REPAINT_MS
    private val smoothExecutor = Executors.newSingleThreadExecutor()
    private val smoothRunnable = Runnable { smoothWhenIdle() }
    private val fadeRunnable = Runnable { fadeStep() }
    @Volatile private var penDown = false
    private val revertRunnable = Runnable { if (highlighting) setHighlighter(false) }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = penWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEMP_COLOR
        style = Paint.Style.STROKE
        strokeWidth = TEMP_DOT_WIDTH_PX
        strokeCap = Paint.Cap.ROUND // a zero-length dash with round caps is a dot
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(0.01f, TEMP_DOT_GAP_PX), 0f)
    }

    // DARKEN keeps the ink readable under the highlight, and overlapping passes don't stack
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HIGHLIGHT_COLOR
        style = Paint.Style.STROKE
        strokeWidth = highlightWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }

    // How strokes look while the (debug) eraser is over them: lighter, to confirm they will go
    private val markedPenPaint = Paint(paint).apply { color = Color.rgb(0xb8, 0xb8, 0xb8) }
    private val markedHighlightPaint = Paint(highlightPaint).apply { color = Color.rgb(0xff, 0xe6, 0x80) }

    private val callback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, p: TouchPoint?) {
            notePen()
            // Don't repaint under the pen while the user is still writing
            main.removeCallbacks(smoothRunnable)
            main.removeCallbacks(revertRunnable) // a quick next highlight keeps the highlighter
            penDown = true
        }
        override fun onEndRawDrawing(b: Boolean, p: TouchPoint?) {
            penDown = false
            if (!editable) main.post { scheduleFade() }
        }
        override fun onRawDrawingTouchPointMoveReceived(p: TouchPoint?) { notePen() }
        override fun onRawDrawingTouchPointListReceived(list: TouchPointList?) {
            commitStroke(list, highlighting)
        }

        // The SDK reports a stroke made with the pen's side button held as an "erase" stroke.
        // In release builds nothing can be erased and the button does nothing. Debug builds use it
        // as an eraser, so that test pages can be cleaned up.
        override fun onBeginRawErasing(b: Boolean, p: TouchPoint?) {
            if (!BuildConfig.DEBUG) return
            penDown = true // no smoothing or fading under the eraser
            main.removeCallbacks(smoothRunnable)
            // Rough, fast screen updates while erasing; the quality comes back when the pen lifts
            main.post { try { EpdController.applyTransientUpdate(UpdateMode.ANIMATION_X) } catch (t: Throwable) {} }
        }
        override fun onEndRawErasing(b: Boolean, p: TouchPoint?) {
            if (!BuildConfig.DEBUG) return
            penDown = false
            commitErase() // normally done already; this makes sure nothing stays marked
            main.post {
                clearEraserCursor()
                try { EpdController.clearTransientUpdate(true) } catch (t: Throwable) {}
                main.postDelayed(smoothRunnable, SMOOTH_DELAY_MS) // clean repaint of what changed
            }
        }
        override fun onRawErasingTouchPointMoveReceived(p: TouchPoint?) {
            // Live: strokes disappear as the eraser reaches them
            if (BuildConfig.DEBUG && p != null) {
                markAt(listOf(p.x to p.y)) // lighter, to confirm; removed when the pen lifts
                val now = SystemClock.elapsedRealtime()
                if (now - lastCursorAt >= CURSOR_INTERVAL_MS) {
                    lastCursorAt = now
                    val x = p.x
                    val y = p.y
                    main.post { drawEraserCursor(x, y) }
                }
            }
        }
        override fun onRawErasingTouchPointListReceived(list: TouchPointList?) {
            if (!BuildConfig.DEBUG) return
            markAt(list?.points?.map { it.x to it.y } ?: emptyList()) // the whole path, in case moves were missed
            commitErase()
        }
    }

    @Volatile
    private var highlighting = false

    // Finger tap: three fingers toggle pen/highlighter. Pen input is handled by the
    // SDK, so this only ever sees fingers.
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downAt = 0L // DEBUG log: when the current finger went down, and how large it was
    private var downSizeMm = 0f
    private var maxSizeMm = 0f
    private var lastToggleAt = 0L
    private var tapArmed = false
    private var tapFingers = 1
    private val startX = FloatArray(3)
    private val startY = FloatArray(3)

    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    /**
     * On today's page every tap switches pen/highlighter at once, so two quick taps switch it twice.
     * On a past page a single tap does nothing and two quick taps in the same place go back to today.
     */
    private fun tap(e: MotionEvent) {
        if (editable) {
            toggleMode()
            return
        }
        val doubleSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
        val isSecond = lastTapAt != 0L && e.eventTime - lastTapAt < ViewConfiguration.getDoubleTapTimeout() &&
            Math.hypot((e.x - lastTapX).toDouble(), (e.y - lastTapY).toDouble()) <= doubleSlop
        if (isSecond) {
            lastTapAt = 0L
            showDay(today, today)
        } else {
            lastTapAt = e.eventTime
            lastTapX = e.x
            lastTapY = e.y
        }
    }

    /** Moves [delta] days from the page being shown; there are no pages after today. */
    private fun swipeDay(delta: Long) {
        val target = day.plusDays(delta)
        if (target.isAfter(today)) return
        showDay(target, today)
    }

    @Volatile
    private var lastPenAt = 0L

    private fun notePen() {
        val now = SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG && lastToggleAt != 0L && now - lastToggleAt < 1500 && now - lastPenAt > PEN_GUARD_MS) {
            TapLog.log("pen arrived ${now - lastToggleAt} ms after a toggle (suspect false toggle)")
            lastToggleAt = 0L
        }
        lastPenAt = now
    }

    /** The pen is touching, hovering just above the screen, or was a moment ago. */
    private fun penActive() = SystemClock.elapsedRealtime() - lastPenAt < PEN_GUARD_MS

    /** DEBUG log: the biggest contact among the pointers, in millimetres. */
    private fun sizeMm(e: MotionEvent): Float {
        var m = 0f
        for (i in 0 until e.pointerCount) m = maxOf(m, e.getTouchMajor(i))
        return m / ppi * 25.4f
    }

    /** Flagged as a palm by Android, cancelled by it, or much bigger than a fingertip. */
    private fun looksLikePalm(e: MotionEvent): Boolean {
        if (e.flags and MotionEvent.FLAG_CANCELED != 0) return true
        // Android's own palm classification (tool type 5, not a public constant in this SDK)
        if (Build.VERSION.SDK_INT >= 31 && e.getToolType(0) == 5) return true
        val palmPx = PALM_MM / 25.4f * ppi
        for (i in 0 until e.pointerCount) if (e.getTouchMajor(i) > palmPx) return true
        return false
    }

    /** Hover events tell us the pen is near, before it touches: that is when the hand lands. */
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val tool = e.getToolType(0)
        if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
            notePen()
            cancelGestures()
        }
        return super.onGenericMotionEvent(e)
    }

    /** Forgets a half-finished finger gesture, e.g. when the pen takes over. */
    private fun cancelGestures() {
        main.removeCallbacks(longPressRunnable)
        tapArmed = false
        lastTapAt = 0L
    }

    /** A finger held still on the canvas; arguments are the view coordinates. */
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null
    private var pressX = 0f
    private var pressY = 0f
    private val longPressRunnable = Runnable { if (!penActive()) onLongPress?.invoke(pressX, pressY) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Only fingers make gestures. With the pen's side button held, a touch can be reported as an
        // eraser or with button bits set rather than as a plain stylus, so test for "not a finger".
        val penButtons = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        if (e.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER || e.buttonState and penButtons != 0) {
            notePen()
            cancelGestures()
            return super.onTouchEvent(e)
        }
        // Palm rejection: a hand resting on the screen to write is not a gesture. Ignore touches
        // while the pen is near or has just been used, and touches that look like a palm.
        if (penActive() || looksLikePalm(e)) {
            if (BuildConfig.DEBUG && (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_UP)) {
                TapLog.log("${if (e.actionMasked == MotionEvent.ACTION_DOWN) "down" else "up"} ignored: " +
                    (if (penActive()) "pen near (${SystemClock.elapsedRealtime() - lastPenAt} ms ago)" else "palm") +
                    " size=${"%.1f".format(Locale.US, sizeMm(e))}mm")
            }
            cancelGestures()
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (BuildConfig.DEBUG) {
                    downAt = e.eventTime
                    downSizeMm = sizeMm(e)
                    maxSizeMm = downSizeMm
                    TapLog.log("down size=${"%.1f".format(Locale.US, downSizeMm)}mm sincePen=${SystemClock.elapsedRealtime() - lastPenAt}ms")
                }
                tapArmed = true
                tapFingers = 1
                pressX = e.x
                pressY = e.y
                main.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_UP -> {
                main.removeCallbacks(longPressRunnable)
                if (BuildConfig.DEBUG) {
                    TapLog.log("up after ${e.eventTime - e.downTime}ms moved=${"%.0f".format(Locale.US, Math.hypot((e.x - pressX).toDouble(), (e.y - pressY).toDouble()))}px " +
                        "maxSize=${"%.1f".format(Locale.US, maxOf(maxSizeMm, sizeMm(e)))}mm fingers=$tapFingers armed=$tapArmed")
                }
                val dx = e.x - pressX
                val dy = e.y - pressY
                // One finger, mostly sideways, far and quick enough: turn the page
                if (tapArmed && tapFingers == 1 && e.eventTime - e.downTime < SWIPE_MAX_MS &&
                    Math.abs(dx) > width * SWIPE_MIN_FRACTION && Math.abs(dx) > 2 * Math.abs(dy)
                ) swipeDay(if (dx < 0) 1 else -1)
                else if (tapArmed && tapFingers == 1 && e.eventTime - e.downTime < TAP_MAX_SINGLE_MS &&
                    Math.hypot(dx.toDouble(), dy.toDouble()) <= slop
                ) tap(e)
            }
            MotionEvent.ACTION_CANCEL -> main.removeCallbacks(longPressRunnable)
            MotionEvent.ACTION_POINTER_DOWN -> {
                main.removeCallbacks(longPressRunnable)
                if (e.pointerCount > 3) {
                    tapArmed = false
                } else {
                    tapFingers = e.pointerCount
                    for (i in 0 until e.pointerCount) { startX[i] = e.getX(i); startY[i] = e.getY(i) }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (BuildConfig.DEBUG) maxSizeMm = maxOf(maxSizeMm, sizeMm(e))
                if (e.pointerCount == 1 &&
                    Math.hypot((e.x - pressX).toDouble(), (e.y - pressY).toDouble()) > slop
                ) main.removeCallbacks(longPressRunnable)
                if (tapArmed && e.pointerCount == tapFingers && tapFingers in 2..3) {
                    for (i in 0 until tapFingers) {
                        if (Math.hypot((e.getX(i) - startX[i]).toDouble(), (e.getY(i) - startY[i]).toDouble()) > slop) {
                            tapArmed = false
                        }
                    }
                }
            }
            // The first finger to lift ends the tap, so a tap is judged on lift, not on touch
            MotionEvent.ACTION_POINTER_UP -> if (tapArmed && e.pointerCount == tapFingers) {
                tapArmed = false
                if (e.eventTime - e.downTime < TAP_MAX_MS) when (tapFingers) {
                    3 -> toggleMode()
                }
            }
        }
        return true
    }

    /**
     * A small area around the corner toggle where the pen is ignored, so the pen can tap it.
     * Everywhere else, including the rest of the button's tap area, the pen draws; fingers always work.
     */
    var reservedWidth = 0
    var reservedHeight = 0
    var reservedInsetRight = 0
    var reservedInsetTop = 0

    fun toggleMode() {
        if (BuildConfig.DEBUG) {
            lastToggleAt = SystemClock.elapsedRealtime()
            TapLog.log("TOGGLE to ${if (!highlighting) "highlighter" else "pen"}")
        }
        setHighlighter(!highlighting)
    }

    private fun setHighlighter(on: Boolean) {
        main.removeCallbacks(revertRunnable)
        highlighting = on
        main.post { refreshIndicator() }
        applyStroke()
    }

    /** What the SDK draws live: black pen, highlighter, or gray on a past page. */
    private fun applyStroke() {
        val marker = highlighting && editable
        touchHelper
            .setStrokeStyle(if (marker) StrokeStyle.MARKER else StrokeStyle.PENCIL)
            .setStrokeWidth(if (marker) highlightWidthPx else penWidthPx)
            .setStrokeColor(if (!editable) TEMP_COLOR else if (marker) HIGHLIGHT_COLOR else Color.BLACK)
    }

    /**
     * A wide stroke drawn as one path shows artifacts on tight corners. Segment by segment with
     * round caps, the overlaps are the same flat gray and so come out clean (DARKEN never stacks).
     */
    /**
     * A wide stroke drawn as one path shows artifacts on tight corners. Segment by segment with
     * round caps, the overlaps are the same flat gray and so come out clean (DARKEN never stacks).
     */
    private fun drawHighlight(c: Canvas, xy: FloatArray, p: Paint) {
        var lx = xy[0]
        var ly = xy[1]
        c.drawPoint(lx, ly, p)
        for (i in 2 until xy.size step 2) {
            val x = xy[i]
            val y = xy[i + 1]
            if (Math.hypot((x - lx).toDouble(), (y - ly).toDouble()) < MIN_SEGMENT_PX) continue
            c.drawLine(lx, ly, x, y, p)
            lx = x
            ly = y
        }
    }

    private fun render(c: Canvas, s: Stroke) {
        if (s.highlight) {
            drawHighlight(c, s.xy, if (s.marked) markedHighlightPaint else highlightPaint)
        } else {
            val path = Path().apply {
                moveTo(s.xy[0], s.xy[1])
                if (s.xy.size == 2) lineTo(s.xy[0] + 0.01f, s.xy[1])
                for (i in 2 until s.xy.size step 2) lineTo(s.xy[i], s.xy[i + 1])
            }
            c.drawPath(path, if (s.marked) markedPenPaint else paint)
        }
    }

    private fun renderTemp(c: Canvas, s: Stroke, p: Paint = tempPaint) {
        val v = Color.red(TEMP_COLOR) + s.level * (255 - Color.red(TEMP_COLOR)) / (FADE_STEPS + 1)
        p.color = Color.rgb(v, v, v)
        val path = Path().apply {
            moveTo(s.xy[0], s.xy[1])
            if (s.xy.size == 2) lineTo(s.xy[0] + 0.01f, s.xy[1])
            for (i in 2 until s.xy.size step 2) lineTo(s.xy[i], s.xy[i + 1])
        }
        c.drawPath(path, p)
    }

    private fun bounds(s: Stroke): Rect {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in s.xy.indices step 2) {
            l = minOf(l, s.xy[i]); r = maxOf(r, s.xy[i])
            t = minOf(t, s.xy[i + 1]); b = maxOf(b, s.xy[i + 1])
        }
        val pad = (if (s.highlight) highlightWidthPx else penWidthPx) + 2f
        return Rect((l - pad).toInt(), (t - pad).toInt(), (r + pad).toInt() + 1, (b + pad).toInt() + 1)
    }

    /**
     * DEBUG BUILDS ONLY: marks every stroke of today's page that one of [points] is over. Marked
     * strokes are drawn lighter right away; nothing is removed until [commitErase].
     */
    private fun markAt(points: List<Pair<Float, Float>>) {
        if (points.isEmpty() || !editable) return
        val area = Rect()
        synchronized(this) {
            for (stroke in strokes) {
                if (stroke.marked) continue
                val r = ERASER_RADIUS_PX + (if (stroke.highlight) highlightWidthPx else penWidthPx) / 2
                if (points.any { (x, y) -> touches(stroke, x, y, r) }) {
                    stroke.marked = true
                    area.union(bounds(stroke))
                }
            }
            if (area.isEmpty) return
            redrawAll()
        }
        area.intersect(0, 0, width, height)
        blit(area)
        main.post { fastRefresh(area) }
    }

    /** DEBUG BUILDS ONLY: removes the marked strokes, once the eraser lifts. */
    private fun commitErase() {
        val area = Rect()
        synchronized(this) {
            val it = strokes.iterator()
            while (it.hasNext()) {
                val stroke = it.next()
                if (!stroke.marked) continue
                area.union(bounds(stroke))
                it.remove()
            }
            if (area.isEmpty) return
            dirty = true
            redrawAll()
        }
        area.intersect(0, 0, width, height)
        blit(area)
        main.post {
            queueSmooth(area, afterInk = true)
            main.removeCallbacks(saveRunnable)
            main.postDelayed(saveRunnable, SAVE_DELAY_MS)
        }
    }

    // DEBUG BUILDS ONLY: a ring that follows the eraser
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var eraserCursor: Rect? = null
    private var lastCursorAt = 0L

    private fun fastRefresh(a: Rect) {
        try {
            EpdController.refreshScreenRegion(this, a.left, a.top, a.width(), a.height(), UpdateMode.ANIMATION_MONO)
        } catch (t: Throwable) {
            // Not an e-ink device
        }
    }

    /** Puts the ring at (x, y): redraws the page under the old ring, then draws the new one. */
    private fun drawEraserCursor(x: Float, y: Float) {
        val bmp = bitmap ?: return
        if (!holder.surface.isValid) return
        val r = ERASER_RADIUS_PX + cursorPaint.strokeWidth
        val now = Rect((x - r).toInt(), (y - r).toInt(), (x + r).toInt() + 1, (y + r).toInt() + 1)
        val dirty = Rect(now)
        eraserCursor?.let { dirty.union(it) }
        dirty.intersect(0, 0, width, height)
        val c = holder.lockCanvas(dirty) ?: return
        try {
            synchronized(this) { c.drawBitmap(bmp, 0f, 0f, null) }
            c.drawCircle(x, y, ERASER_RADIUS_PX, cursorPaint)
        } finally {
            holder.unlockCanvasAndPost(c)
        }
        eraserCursor = now
        fastRefresh(dirty)
    }

    private fun clearEraserCursor() {
        val old = eraserCursor ?: return
        eraserCursor = null
        blit(old)
        fastRefresh(old)
    }

    /** Whether the point (x, y) is within [r] of the stroke's line. */
    private fun touches(s: Stroke, x: Float, y: Float, r: Float): Boolean {
        val xy = s.xy
        val r2 = r * r
        if (xy.size == 2) return (x - xy[0]) * (x - xy[0]) + (y - xy[1]) * (y - xy[1]) <= r2
        for (i in 0 until xy.size - 2 step 2) {
            val ax = xy[i]; val ay = xy[i + 1]
            val dx = xy[i + 2] - ax; val dy = xy[i + 3] - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f)
            val px = ax + t * dx - x
            val py = ay + t * dy - y
            if (px * px + py * py <= r2) return true
        }
        return false
    }

    private fun commitStroke(list: TouchPointList?, highlight: Boolean) {
        val pts = list?.points ?: return
        if (pts.isEmpty() || loading) return
        val xy = FloatArray(pts.size * 2) { if (it % 2 == 0) pts[it / 2].x else pts[it / 2].y }
        val temporary = !editable
        val stroke = Stroke(highlight && !temporary, xy)
        synchronized(this) {
            if (temporary) {
                tempStrokes.add(stroke)
                bitmapCanvas?.let { renderTemp(it, stroke) }
            } else {
                strokes.add(stroke)
                dirty = true
                bitmapCanvas?.let { render(it, stroke) }
            }
        }
        // No blit here: the surface is updated later, by queueSmooth, once the pen has been idle.
        // Blitting right after a stroke can wipe the live ink of the next one, which the SDK is
        // already drawing on the panel when strokes are small, fast and overlapping.
        val area = bounds(stroke)
        main.post {
            if (temporary) scheduleFade()
            if (!temporary) {
                main.removeCallbacks(saveRunnable)
                main.postDelayed(saveRunnable, SAVE_DELAY_MS)
            }
            if (stroke.highlight) {
                main.removeCallbacks(revertRunnable)
                main.postDelayed(revertRunnable, REVERT_TO_PEN_MS)
            }
            queueSmooth(area, afterInk = true) // every stroke restarts the wait
        }
    }

    private val touchHelper: TouchHelper = TouchHelper.create(this, callback)

    init {
        TapLog.init(context)
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {}

            override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hgt: Int) {
                ensureBitmap(w, hgt)
                blit()
                openRaw(w, hgt)
            }

            override fun surfaceDestroyed(h: SurfaceHolder) {
                closeRaw()
            }
        })
    }

    private fun ensureBitmap(w: Int, h: Int) {
        val old = bitmap
        if (old != null && old.width == w && old.height == h) return
        val fresh = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(fresh)
        c.drawColor(Color.WHITE)
        bitmap = fresh
        bitmapCanvas = c
        // The strokes hold the whole page, so rebuild from them (also covers a page loaded from disk)
        synchronized(this) { redrawAll() }
    }

    /**
     * Mirrors the bitmap onto the surface. The Onyx SDK paints live strokes straight to the
     * EPD, so without this the surface only holds a blank page and a full refresh wipes them.
     */
    private fun blit(dirty: Rect? = null) {
        val bmp = bitmap ?: return
        if (!holder.surface.isValid) return
        val c = (if (dirty == null) holder.lockCanvas() else holder.lockCanvas(dirty)) ?: return
        try {
            synchronized(this) { c.drawBitmap(bmp, 0f, 0f, null) }
            drawIndicator(c)
        } finally {
            holder.unlockCanvasAndPost(c)
        }
    }

    // The pen/highlighter indicator, top right. It is drawn on the surface only, never into the
    // page bitmap (so not into exports), and it is refreshed as a small region of its own: a view
    // for it made the whole panel refresh every time the tool changed.
    private val indicatorDp = resources.displayMetrics.density
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun indicatorRect(): Rect {
        val cx = width - (8 + 24) * indicatorDp
        val cy = (8 + 24) * indicatorDp
        val r = 12 * indicatorDp
        return Rect((cx - r).toInt(), (cy - r).toInt(), (cx + r).toInt(), (cy + r).toInt())
    }

    private fun drawIndicator(c: Canvas) {
        if (!editable || loading) return
        val box = indicatorRect()
        if (!Rect.intersects(box, c.clipBounds)) return
        if (highlighting) {
            indicatorPaint.color = HIGHLIGHT_COLOR
            c.drawCircle(box.exactCenterX(), box.exactCenterY(), 9 * indicatorDp, indicatorPaint)
        } else {
            indicatorPaint.color = Color.BLACK
            c.drawCircle(box.exactCenterX(), box.exactCenterY(), 4 * indicatorDp, indicatorPaint)
        }
    }

    // Through the same repaint path as the smoothing and a closed menu: that is the update that
    // doesn't flash the panel, unlike a refresh requested straight from here.
    private fun refreshIndicator() = queueSmooth(indicatorRect(), afterInk = false)

    /**
     * Raw drawing paints a rough preview straight on the panel. Once the pen has been away for a
     * while, the finished strokes are repainted from the antialiased bitmap, like Boox Notes does.
     * [afterInk] waits [SMOOTH_DELAY_MS]; anything else only waits a moment. Every new request
     * restarts the wait, and any pen activity (a new stroke, or just hovering) pushes it back.
     */
    private fun queueSmooth(area: Rect, afterInk: Boolean) {
        val grown = Rect(area).apply { inset(-MERGE_MARGIN_PX, -MERGE_MARGIN_PX) }
        synchronized(pendingAreas) {
            val merged = Rect(area)
            val it = pendingAreas.iterator()
            while (it.hasNext()) {
                val r = it.next()
                if (Rect.intersects(r, grown)) {
                    merged.union(r)
                    it.remove()
                }
            }
            pendingAreas.add(merged)
            if (pendingAreas.size > MAX_AREAS) { // too many: repaint one big area instead
                val all = Rect(pendingAreas[0])
                pendingAreas.forEach { all.union(it) }
                pendingAreas.clear()
                pendingAreas.add(all)
            }
        }
        val wait = if (afterInk) SMOOTH_DELAY_MS else QUICK_REPAINT_MS
        smoothIdleMs = maxOf(smoothIdleMs, wait)
        main.removeCallbacks(smoothRunnable)
        main.postDelayed(smoothRunnable, wait)
    }

    /** Main thread, and cheap: only decides whether it is time. The repainting is done elsewhere. */
    private fun smoothWhenIdle() {
        if (!rawOpen) return
        val idle = SystemClock.elapsedRealtime() - lastPenAt
        if (penDown || idle < smoothIdleMs) {
            main.postDelayed(smoothRunnable, maxOf(100L, smoothIdleMs - idle))
            return
        }
        smoothExecutor.execute { smoothNow() }
    }

    /** On a background thread, so a slow panel call can't stall touch handling or the UI. */
    private fun smoothNow() {
        val areas = synchronized(pendingAreas) {
            val copy = ArrayList(pendingAreas)
            pendingAreas.clear()
            smoothIdleMs = QUICK_REPAINT_MS
            copy
        }
        if (areas.isEmpty() || !rawOpen) return
        try {
            for ((i, area) in areas.withIndex()) {
                area.intersect(0, 0, width, height)
                if (area.isEmpty) continue
                if (penDown) { // the pen came back: never repaint under it; try again later
                    areas.drop(i).forEach { queueSmooth(it, afterInk = true) }
                    return
                }
                blit(area) // bring the surface up to date first, so the repaint draws the new strokes
                EpdController.handwritingRepaint(this, area)
            }
            EpdController.clearTransientUpdate(true)
            touchHelper.isRawDrawingRenderEnabled = false
            touchHelper.isRawDrawingRenderEnabled = true
        } catch (t: Throwable) {
            // Not an e-ink device: the surface already holds the smooth strokes
        }
    }

    private fun openRaw(w: Int, h: Int) {
        closeRaw()
        touchHelper
            .setLimitRect(mutableListOf(Rect(0, 0, w, h)))
            .setExcludeRect(
                if (reservedWidth <= 0) emptyList() // no button to protect
                else listOf(Rect(
                    w - reservedInsetRight - reservedWidth, reservedInsetTop,
                    w - reservedInsetRight, reservedInsetTop + reservedHeight
                ))
            )
            .openRawDrawing()
        setHighlighter(highlighting)
        touchHelper.setRawDrawingEnabled(true)
        rawOpen = true
    }

    private fun closeRaw() {
        main.removeCallbacks(smoothRunnable)
        synchronized(pendingAreas) { pendingAreas.clear() }
        if (!rawOpen) return
        touchHelper.setRawDrawingEnabled(false)
        touchHelper.closeRawDrawing()
        rawOpen = false
    }

    /** Copy of the current page: black strokes and highlights on white. */
    fun snapshot(): Bitmap? = synchronized(this) { bitmap?.copy(Bitmap.Config.ARGB_8888, false) }

    /** Repaints an area of the panel from the bitmap, e.g. after an overlay is dismissed. */
    fun repaint(area: Rect) = queueSmooth(area, afterInk = false)

    private fun pageFile(d: LocalDate) = File(pagesDir ?: error("pagesDir not set"), "$d.svg")

    /** Writes today's page if it changed. Via a temp file, so a crash never leaves half a drawing. */
    fun save() {
        synchronized(saveLock) {
            val copy: List<Stroke>
            val file: File
            synchronized(this) {
                if (!dirty || !editable || loading) return
                copy = ArrayList(strokes)
                file = pageFile(day)
                dirty = false
            }
            try {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(PageFile.toSvg(copy, width, height, penWidthPx, highlightWidthPx), Charsets.UTF_8)
                if (!tmp.renameTo(file)) error("Could not replace ${file.name}")
            } catch (t: Exception) {
                synchronized(this) { dirty = true }
                throw t
            }
        }
    }

    /** Reads a saved page. A missing or unreadable file is an empty page. */
    private fun readStrokes(file: File): List<Stroke> {
        if (!file.exists()) return emptyList()
        return try {
            PageFile.parse(file.readText(Charsets.UTF_8)).map { Stroke(it.highlight, it.xy) }
        } catch (t: Exception) {
            emptyList()
        }
    }

    private fun scheduleFade() {
        main.removeCallbacks(fadeRunnable)
        val now = SystemClock.elapsedRealtime()
        val next = synchronized(this) {
            tempStrokes.minOfOrNull { it.born + ((now - it.born) / FADE_STEP_MS + 1) * FADE_STEP_MS - now }
        } ?: return
        main.postDelayed(fadeRunnable, next.coerceAtLeast(300L))
    }

    /** Lightens temporary ink that has reached its next step, and drops what has faded out. */
    private fun fadeStep() {
        if (penDown) { // never redraw under the pen
            main.postDelayed(fadeRunnable, 1000L)
            return
        }
        val now = SystemClock.elapsedRealtime()
        var area: Rect? = null
        synchronized(this) {
            val it = tempStrokes.iterator()
            var changed = false
            while (it.hasNext()) {
                val s = it.next()
                val level = ((now - s.born) / FADE_STEP_MS).toInt()
                if (level > FADE_STEPS || level != s.level) {
                    area = area?.apply { union(bounds(s)) } ?: bounds(s)
                    changed = true
                    if (level > FADE_STEPS) it.remove() else s.level = level
                }
            }
            if (changed) redrawAll()
        }
        area?.let {
            blit(it)
            repaint(it)
        }
        scheduleFade()
    }

    /**
     * Shows [newDay]'s page, given that [newToday] is the current calendar day. Today's page is
     * editable, any other day is read-only. Call from the main thread.
     */
    fun showDay(newDay: LocalDate, newToday: LocalDate, refresh: Boolean = true) {
        main.removeCallbacks(saveRunnable)
        main.removeCallbacks(revertRunnable)
        main.removeCallbacks(fadeRunnable)
        try {
            save() // the page we are leaving
        } catch (t: Exception) {
        }
        // The page is read off the main thread (it can be a megabyte of SVG). Until it arrives the
        // old page stays on the panel, and pen input is ignored.
        val id: Int
        synchronized(this) {
            day = newDay
            today = newToday
            strokes.clear()
            tempStrokes.clear()
            dirty = false
            loading = true
            id = ++loadId
        }
        if (rawOpen) {
            if (!editable) highlighting = false // the highlighter is for today's page only
            applyStroke()
            touchHelper.setRawDrawingEnabled(true)
        }
        val file = pageFile(newDay)
        loadExecutor.execute {
            val loaded = readStrokes(file)
            val current = synchronized(this) {
                if (id != loadId) return@synchronized false // another page was asked for meanwhile
                strokes.addAll(loaded)
                loading = false
                redrawAll()
                true
            }
            if (!current) return@execute
            main.post {
                if (id != loadId) return@post
                blit()
                if (refresh) {
                    try {
                        EpdController.applyGCOnce() // full refresh, so the old page leaves no ghost
                    } catch (t: Throwable) {
                    }
                    repaint(Rect(0, 0, width, height))
                }
                onDayChanged?.invoke(day, editable)
            }
        }
    }

    private val koanPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = resources.getFont(R.font.zodiak_light_italic)
        fontFeatureSettings = "onum" // old-style figures, when the font has them
        letterSpacing = -0.02f // em; a little tighter than the font default
    }

    private val datePaint = TextPaint(koanPaint).apply {
        textAlign = Paint.Align.CENTER
        color = DATE_COLOR
        typeface = resources.getFont(R.font.zodiak_italic) // the date stays regular italic; only the koans are light
    }
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")

    /** Bigger system fonts make the date and the koan bigger; never smaller than designed. */
    private val fontBoost: Float get() = resources.configuration.fontScale.coerceAtLeast(1f)

    /** The date, centered at the top of a past page, in the same font as the koans. */
    private fun drawDate(c: Canvas, w: Int, h: Int, day: LocalDate, p: TextPaint) {
        val text = day.format(dateFormat)
        p.textSize = w * 0.03825f * fontBoost // 15% smaller than 0.045
        val tooWide = p.measureText(text) / (w * 0.92f)
        if (tooWide > 1f) p.textSize /= tooWide // always inside the page
        c.drawText(text, w / 2f, h * 0.06f + p.textSize, p)
    }

    private fun drawKoan(c: Canvas, w: Int, h: Int, day: LocalDate, p: TextPaint) {
        val margin = w * 0.12f
        val koan = Koans.forDay(day)
        fun layoutFor(size: Float): StaticLayout {
            p.textSize = size
            // Line height of 1.2 em: the distance between baselines is 1.2 times the font size
            val natural = p.fontMetrics.let { it.descent - it.ascent }
            return StaticLayout.Builder
                .obtain(koan, 0, koan.length, p, (w - 2 * margin).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(KOAN_LINE_HEIGHT_EM * size - natural, 1f)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED) // like CSS text-wrap: balance
                .build()
        }
        // A big system font may not fit the page: shrink until it does, but never below the floor
        var size = w * 0.055f * fontBoost
        var layout = layoutFor(size)
        while (layout.height > h * 0.62f && size > w * 0.03f) {
            size *= 0.94f
            layout = layoutFor(size)
        }
        c.save()
        c.translate(margin, (h - layout.height) / 2f)
        layout.draw(c)
        c.restore()
    }

    /**
     * Draws a whole page onto [c], which is [w] x [h] units. Used for the bitmap on screen and,
     * with a scaled canvas, for vector PDFs: strokes stay paths, text stays text.
     */
    private fun drawPage(
        c: Canvas, w: Int, h: Int, day: LocalDate, editable: Boolean,
        strokes: List<Stroke>, temps: List<Stroke>,
        koan: TextPaint, date: TextPaint, temp: Paint
    ) {
        c.drawColor(Color.WHITE)
        val emptyPastDay = !editable && strokes.isEmpty()
        // The date goes only on a day with nothing drawn, where it sits with the koan; on a day
        // with notes it would be drawn over them
        if (emptyPastDay) {
            drawDate(c, w, h, day, date)
            drawKoan(c, w, h, day, koan)
        } else {
            strokes.forEach { render(c, it) }
        }
        temps.forEach { renderTemp(c, it, temp) }
    }

    private fun redrawAll() {
        bitmapCanvas?.let { c ->
            drawPage(c, c.width, c.height, day, editable, strokes, tempStrokes, koanPaint, datePaint, tempPaint)
        }
    }

    /** A page that can be drawn later, on any thread and any canvas, as it is right now. */
    class PageSnapshot(val width: Int, val height: Int, val ppi: Float, val draw: (Canvas) -> Unit)

    fun pageSnapshot(): PageSnapshot? {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        val d = day
        val isEditable = editable
        val (s, t) = synchronized(this) { ArrayList(strokes) to ArrayList(tempStrokes) }
        // The paints are copied: drawing may happen on another thread while the screen redraws
        val pixelsPerInch = ppi
        return PageSnapshot(w, h, pixelsPerInch) { c ->
            drawPage(c, w, h, d, isEditable, s, t, TextPaint(koanPaint), TextPaint(datePaint), Paint(tempPaint))
        }
    }

    fun pauseInput() {
        if (rawOpen) touchHelper.setRawDrawingEnabled(false)
    }

    fun resumeInput() {
        if (rawOpen) {
            blit()
            touchHelper.setRawDrawingEnabled(true)
            main.post { fadeStep() } // catch up on any fading missed while paused
        }
    }
}
