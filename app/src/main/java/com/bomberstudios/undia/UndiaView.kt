package com.bomberstudios.undia

import android.content.Context
import java.io.File
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
import android.os.Handler
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
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
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.pen.style.StrokeStyle

/**
 * Blank white canvas with a fixed-width 0.2 mm pen; the pen's side button toggles pen/highlighter. The Onyx SDK draws the live
 * stroke on the EPD; we mirror finished strokes into a bitmap so they survive
 * surface redraws.
 *
 * One page per day: today's page can be drawn on and never erased; past days are read-only,
 * and a past day with no drawing shows a koan.
 */
class UndiaView(context: Context) : SurfaceView(context) {
    private companion object {
        const val PEN_WIDTH_MM = 0.2f
        // Go 6 Gen2: 1264x1680 on a 6" panel = 300 ppi
        const val PANEL_PPI = 300f
        const val PEN_WIDTH_PX = PEN_WIDTH_MM / 25.4f * PANEL_PPI // ~2.36 px
        const val HIGHLIGHT_WIDTH_MM = 4f
        const val HIGHLIGHT_WIDTH_PX = HIGHLIGHT_WIDTH_MM / 25.4f * PANEL_PPI // ~47 px
        val DATE_COLOR = Color.rgb(0xff, 0x66, 0x00) // #ff6600
        // Ink on a past page is temporary: gray and dotted
        val TEMP_COLOR = Color.rgb(0x88, 0x88, 0x88)
        const val TEMP_DOT_WIDTH_PX = 3.5f
        const val TEMP_DOT_GAP_PX = 8f
        // Temporary ink gets lighter every FADE_STEP_MS and is gone after FADE_STEPS more steps
        const val FADE_STEP_MS = 1_000L
        const val FADE_STEPS = 5
        const val HIGHLIGHT_HEX = "#ffcc00"
        val HIGHLIGHT_COLOR = Color.parseColor(HIGHLIGHT_HEX)
        const val TAP_MAX_MS = 600L
        // After a highlight, how long before the pen comes back (resets with each new highlight)
        const val REVERT_TO_PEN_MS = 800L
        const val TAP_MAX_SINGLE_MS = 250L
        const val SWIPE_MAX_MS = 800L
        const val SWIPE_MIN_FRACTION = 0.25f // of the page width
        const val SAVE_DELAY_MS = 3000L
        const val MIN_SEGMENT_PX = 3f
        // Pause after the last stroke before it is repainted smooth
        const val SMOOTH_DELAY_MS = 300L
    }

    private class Stroke(val highlight: Boolean, val xy: FloatArray) {
        // Only used by temporary strokes: when it was drawn and how faded it is (0 = fresh)
        val born: Long = SystemClock.elapsedRealtime()
        var level = 0
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
    private val saveLock = Any()
    private val saveExecutor = Executors.newSingleThreadExecutor()
    private val saveRunnable = Runnable { saveExecutor.execute { try { save() } catch (t: Exception) {} } }

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private var rawOpen = false

    private val main = Handler(Looper.getMainLooper())
    private val pendingSmooth = Rect()
    private val smoothRunnable = Runnable { smoothPending() }
    private val fadeRunnable = Runnable { fadeStep() }
    @Volatile private var penDown = false
    private val revertRunnable = Runnable { if (highlighting) setHighlighter(false) }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = PEN_WIDTH_PX
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
        strokeWidth = HIGHLIGHT_WIDTH_PX
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }

    private val callback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, p: TouchPoint?) {
            // Don't repaint under the pen while the user is still writing
            main.removeCallbacks(smoothRunnable)
            main.removeCallbacks(revertRunnable) // a quick next highlight keeps the highlighter
            penDown = true
        }
        override fun onEndRawDrawing(b: Boolean, p: TouchPoint?) {
            penDown = false
            if (!editable) main.post { scheduleFade() }
        }
        override fun onRawDrawingTouchPointMoveReceived(p: TouchPoint?) {}
        override fun onRawDrawingTouchPointListReceived(list: TouchPointList?) {
            commitStroke(list, highlighting)
        }

        // Touching down with the pen's side button held reaches us as an "erase" stroke.
        // We never erase: it flips between pen and highlighter, and the stroke is dropped.
        override fun onBeginRawErasing(b: Boolean, p: TouchPoint?) {
            main.removeCallbacks(smoothRunnable)
            if (editable) setHighlighter(!highlighting)
        }
        override fun onEndRawErasing(b: Boolean, p: TouchPoint?) {}
        override fun onRawErasingTouchPointMoveReceived(p: TouchPoint?) {}
        override fun onRawErasingTouchPointListReceived(list: TouchPointList?) {}
    }

    @Volatile
    private var highlighting = false

    // Finger tap: three fingers toggle pen/highlighter. Pen input is handled by the
    // SDK, so this only ever sees fingers.
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var tapArmed = false
    private var tapFingers = 1
    private val startX = FloatArray(3)
    private val startY = FloatArray(3)

    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    /** Two quick taps in the same place go back to today. */
    private fun singleTap(e: MotionEvent) {
        val doubleSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
        val isSecond = lastTapAt != 0L && e.eventTime - lastTapAt < ViewConfiguration.getDoubleTapTimeout() &&
            Math.hypot((e.x - lastTapX).toDouble(), (e.y - lastTapY).toDouble()) <= doubleSlop
        if (isSecond) {
            lastTapAt = 0L
            if (day != today) showDay(today, today)
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

    /** A finger held still on the canvas; arguments are the view coordinates. */
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null
    private var pressX = 0f
    private var pressY = 0f
    private val longPressRunnable = Runnable { onLongPress?.invoke(pressX, pressY) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapArmed = true
                tapFingers = 1
                pressX = e.x
                pressY = e.y
                main.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_UP -> {
                main.removeCallbacks(longPressRunnable)
                val dx = e.x - pressX
                val dy = e.y - pressY
                // One finger, mostly sideways, far and quick enough: turn the page
                if (tapArmed && tapFingers == 1 && e.eventTime - e.downTime < SWIPE_MAX_MS &&
                    Math.abs(dx) > width * SWIPE_MIN_FRACTION && Math.abs(dx) > 2 * Math.abs(dy)
                ) swipeDay(if (dx < 0) 1 else -1)
                else if (tapArmed && tapFingers == 1 && e.eventTime - e.downTime < TAP_MAX_SINGLE_MS &&
                    Math.hypot(dx.toDouble(), dy.toDouble()) <= slop
                ) singleTap(e)
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

    /** Called on the main thread whenever the mode changes. */
    var onModeChanged: ((highlighting: Boolean) -> Unit)? = null

    fun toggleMode() = setHighlighter(!highlighting)

    private fun setHighlighter(on: Boolean) {
        main.removeCallbacks(revertRunnable)
        highlighting = on
        main.post { onModeChanged?.invoke(on) }
        applyStroke()
    }

    /** What the SDK draws live: black pen, highlighter, or gray on a past page. */
    private fun applyStroke() {
        val marker = highlighting && editable
        touchHelper
            .setStrokeStyle(if (marker) StrokeStyle.MARKER else StrokeStyle.PENCIL)
            .setStrokeWidth(if (marker) HIGHLIGHT_WIDTH_PX else PEN_WIDTH_PX)
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
    private fun drawHighlight(c: Canvas, xy: FloatArray) {
        var lx = xy[0]
        var ly = xy[1]
        c.drawPoint(lx, ly, highlightPaint)
        for (i in 2 until xy.size step 2) {
            val x = xy[i]
            val y = xy[i + 1]
            if (Math.hypot((x - lx).toDouble(), (y - ly).toDouble()) < MIN_SEGMENT_PX) continue
            c.drawLine(lx, ly, x, y, highlightPaint)
            lx = x
            ly = y
        }
    }

    private fun render(c: Canvas, s: Stroke) {
        if (s.highlight) {
            drawHighlight(c, s.xy)
        } else {
            val path = Path().apply {
                moveTo(s.xy[0], s.xy[1])
                if (s.xy.size == 2) lineTo(s.xy[0] + 0.01f, s.xy[1])
                for (i in 2 until s.xy.size step 2) lineTo(s.xy[i], s.xy[i + 1])
            }
            c.drawPath(path, paint)
        }
    }

    private fun renderTemp(c: Canvas, s: Stroke) {
        val v = Color.red(TEMP_COLOR) + s.level * (255 - Color.red(TEMP_COLOR)) / (FADE_STEPS + 1)
        tempPaint.color = Color.rgb(v, v, v)
        val path = Path().apply {
            moveTo(s.xy[0], s.xy[1])
            if (s.xy.size == 2) lineTo(s.xy[0] + 0.01f, s.xy[1])
            for (i in 2 until s.xy.size step 2) lineTo(s.xy[i], s.xy[i + 1])
        }
        c.drawPath(path, tempPaint)
    }

    private fun bounds(s: Stroke): Rect {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in s.xy.indices step 2) {
            l = minOf(l, s.xy[i]); r = maxOf(r, s.xy[i])
            t = minOf(t, s.xy[i + 1]); b = maxOf(b, s.xy[i + 1])
        }
        val pad = (if (s.highlight) HIGHLIGHT_WIDTH_PX else PEN_WIDTH_PX) + 2f
        return Rect((l - pad).toInt(), (t - pad).toInt(), (r + pad).toInt() + 1, (b + pad).toInt() + 1)
    }

    private fun commitStroke(list: TouchPointList?, highlight: Boolean) {
        val pts = list?.points ?: return
        if (pts.isEmpty()) return
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
        // No blit here: the surface is updated later, in smoothPending, while the pen is idle.
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
            pendingSmooth.union(area)
            main.removeCallbacks(smoothRunnable)
            main.postDelayed(smoothRunnable, SMOOTH_DELAY_MS)
        }
    }

    private val touchHelper: TouchHelper = TouchHelper.create(this, callback)

    init {
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
        } finally {
            holder.unlockCanvasAndPost(c)
        }
    }

    /**
     * Raw drawing paints a rough preview straight on the panel. After a short pause, repaint
     * the finished strokes from the antialiased bitmap, like Boox Notes does.
     */
    private fun smoothPending() {
        if (!rawOpen || pendingSmooth.isEmpty) return
        if (penDown) { // wait for the pen to lift: never repaint under it
            main.postDelayed(smoothRunnable, SMOOTH_DELAY_MS)
            return
        }
        val area = Rect(pendingSmooth)
        pendingSmooth.setEmpty()
        area.intersect(0, 0, width, height)
        if (area.isEmpty) return
        blit(area) // bring the surface up to date first, so the repaint below draws the new strokes
        try {
            EpdController.handwritingRepaint(this, area)
            EpdController.clearTransientUpdate(true)
            touchHelper.isRawDrawingRenderEnabled = false
            touchHelper.isRawDrawingRenderEnabled = true
        } catch (t: Throwable) {
            // Not an e-ink device: the surface already holds the smooth stroke
        }
    }

    private fun openRaw(w: Int, h: Int) {
        closeRaw()
        touchHelper
            .setLimitRect(mutableListOf(Rect(0, 0, w, h)))
            .setExcludeRect(listOf(Rect(
                w - reservedInsetRight - reservedWidth, reservedInsetTop,
                w - reservedInsetRight, reservedInsetTop + reservedHeight
            )))
            .openRawDrawing()
        setHighlighter(highlighting)
        touchHelper.setRawDrawingEnabled(true)
        rawOpen = true
    }

    private fun closeRaw() {
        main.removeCallbacks(smoothRunnable)
        pendingSmooth.setEmpty()
        if (!rawOpen) return
        touchHelper.setRawDrawingEnabled(false)
        touchHelper.closeRawDrawing()
        rawOpen = false
    }

    /** Copy of the current page: black strokes and highlights on white. */
    fun snapshot(): Bitmap? = synchronized(this) { bitmap?.copy(Bitmap.Config.ARGB_8888, false) }

    /** Repaints an area of the panel from the bitmap, e.g. after an overlay is dismissed. */
    fun repaint(area: Rect) {
        main.post {
            pendingSmooth.union(area)
            main.removeCallbacks(smoothRunnable)
            main.postDelayed(smoothRunnable, 50)
        }
    }

    private fun pageFile(d: LocalDate) = File(pagesDir ?: error("pagesDir not set"), "$d.svg")

    /** Writes today's page if it changed. Via a temp file, so a crash never leaves half a drawing. */
    fun save() {
        synchronized(saveLock) {
            val copy: List<Stroke>
            val file: File
            synchronized(this) {
                if (!dirty || !editable) return
                copy = ArrayList(strokes)
                file = pageFile(day)
                dirty = false
            }
            try {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(toSvg(copy, width, height), Charsets.UTF_8)
                if (!tmp.renameTo(file)) error("Could not replace ${file.name}")
            } catch (t: Exception) {
                synchronized(this) { dirty = true }
                throw t
            }
        }
    }

    private fun fmt(v: Float) = String.format(Locale.US, "%.1f", v)

    /** A page as a plain SVG: one polyline per stroke, in panel pixels. */
    private fun toSvg(list: List<Stroke>, w: Int, h: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">\n")
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n")
        sb.append("<g fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")
        for (s in list) {
            if (s.highlight) {
                sb.append("<polyline stroke=\"$HIGHLIGHT_HEX\" stroke-width=\"${fmt(HIGHLIGHT_WIDTH_PX)}\" style=\"mix-blend-mode:darken\" points=\"")
            } else {
                sb.append("<polyline stroke=\"#000000\" stroke-width=\"${fmt(PEN_WIDTH_PX)}\" points=\"")
            }
            for (i in s.xy.indices step 2) {
                if (i > 0) sb.append(' ')
                sb.append(fmt(s.xy[i])).append(',').append(fmt(s.xy[i + 1]))
            }
            // A single point needs a second one, or SVG draws no dot
            if (s.xy.size == 2) sb.append(' ').append(fmt(s.xy[0])).append(',').append(fmt(s.xy[1]))
            sb.append("\"/>\n")
        }
        sb.append("</g>\n</svg>\n")
        return sb.toString()
    }

    /** Reads a saved page. A missing or unreadable file is an empty page. */
    private fun readStrokes(file: File): List<Stroke> {
        if (!file.exists()) return emptyList()
        return try {
            val loaded = ArrayList<Stroke>()
            file.reader(Charsets.UTF_8).use { reader ->
                val parser = Xml.newPullParser()
                parser.setInput(reader)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG || parser.name != "polyline") continue
                    val highlight = parser.getAttributeValue(null, "stroke")
                        ?.equals(HIGHLIGHT_HEX, ignoreCase = true) == true
                    val nums = parser.getAttributeValue(null, "points")
                        ?.split(' ', ',', '\n', '\t')?.filter { it.isNotEmpty() }?.map { it.toFloat() }
                        ?: continue
                    if (nums.size >= 2 && nums.size % 2 == 0) loaded.add(Stroke(highlight, nums.toFloatArray()))
                }
            }
            loaded
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
        val loaded = readStrokes(pageFile(newDay))
        synchronized(this) {
            day = newDay
            today = newToday
            strokes.clear()
            strokes.addAll(loaded)
            tempStrokes.clear()
            dirty = false
            redrawAll()
        }
        if (rawOpen) {
            if (!editable) highlighting = false // the highlighter is for today's page only
            applyStroke()
            touchHelper.setRawDrawingEnabled(true)
        }
        blit()
        main.post {
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

    /** The date, centered at the top of a past page, in the same font as the koans. */
    private fun drawDate(c: Canvas) {
        datePaint.textSize = c.width * 0.03825f // 15% smaller than 0.045
        c.drawText(day.format(dateFormat), c.width / 2f, c.height * 0.06f + datePaint.textSize, datePaint)
    }

    private fun drawKoan(c: Canvas) {
        val margin = c.width * 0.12f
        koanPaint.textSize = c.width * 0.055f
        val koan = Koans.forDay(day)
        val layout = StaticLayout.Builder
            .obtain(koan, 0, koan.length, koanPaint, (c.width - 2 * margin).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED) // like CSS text-wrap: balance
            .build()
        c.save()
        c.translate(margin, (c.height - layout.height) / 2f)
        layout.draw(c)
        c.restore()
    }

    private fun redrawAll() {
        bitmapCanvas?.let { c ->
            c.drawColor(Color.WHITE)
            if (!editable) drawDate(c)
            if (!editable && strokes.isEmpty()) drawKoan(c) else strokes.forEach { render(c, it) }
            tempStrokes.forEach { renderTemp(c, it) }
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
