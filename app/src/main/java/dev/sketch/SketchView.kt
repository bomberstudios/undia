package dev.sketch

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 */
class SketchView(context: Context) : SurfaceView(context) {
    private companion object {
        const val PEN_WIDTH_MM = 0.2f
        // Go 6 Gen2: 1264x1680 on a 6" panel = 300 ppi
        const val PANEL_PPI = 300f
        const val PEN_WIDTH_PX = PEN_WIDTH_MM / 25.4f * PANEL_PPI // ~2.36 px
        const val HIGHLIGHT_WIDTH_MM = 4f
        const val HIGHLIGHT_WIDTH_PX = HIGHLIGHT_WIDTH_MM / 25.4f * PANEL_PPI // ~47 px
        val HIGHLIGHT_COLOR = Color.rgb(200, 200, 200)
        const val TAP_MAX_MS = 600L
        // After a highlight, how long before the pen comes back (resets with each new highlight)
        const val REVERT_TO_PEN_MS = 800L
        const val FILE_VERSION = 1
        const val DOUBLE_TAP_MS = 400L
        const val MIN_SEGMENT_PX = 3f
        // Pause after the last stroke before it is repainted smooth
        const val SMOOTH_DELAY_MS = 300L
    }

    private class Stroke(val highlight: Boolean, val xy: FloatArray)

    // Guarded by synchronized(this), like the bitmap
    private val strokes = ArrayList<Stroke>()
    private var clearedStrokes: List<Stroke>? = null
    private val redoStack = ArrayList<Stroke>()

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private var rawOpen = false

    private val main = Handler(Looper.getMainLooper())
    private val pendingSmooth = Rect()
    private val smoothRunnable = Runnable { smoothPending() }
    private val revertRunnable = Runnable { if (highlighting) setHighlighter(false) }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = PEN_WIDTH_PX
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        }
        override fun onEndRawDrawing(b: Boolean, p: TouchPoint?) {}
        override fun onRawDrawingTouchPointMoveReceived(p: TouchPoint?) {}
        override fun onRawDrawingTouchPointListReceived(list: TouchPointList?) {
            commitStroke(list, highlighting)
        }

        // Touching down with the pen's side button held reaches us as an "erase" stroke.
        // We never erase: it flips between pen and highlighter, and the stroke is dropped.
        override fun onBeginRawErasing(b: Boolean, p: TouchPoint?) {
            main.removeCallbacks(smoothRunnable)
            setHighlighter(!highlighting)
        }
        override fun onEndRawErasing(b: Boolean, p: TouchPoint?) {}
        override fun onRawErasingTouchPointMoveReceived(p: TouchPoint?) {}
        override fun onRawErasingTouchPointListReceived(list: TouchPointList?) {}
    }

    @Volatile
    private var highlighting = false

    // Finger taps: two fingers undo, three toggle pen/highlighter. Pen input is handled by the
    // SDK, so this only ever sees fingers.
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var tapArmed = false
    private var tapFingers = 1
    private val startX = FloatArray(3)
    private val startY = FloatArray(3)

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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.removeCallbacks(longPressRunnable)
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
                    2 -> twoFingerTap()
                    3 -> toggleMode()
                }
            }
        }
        return true
    }

    private var lastTwoTapAt = 0L
    private var lastTwoTapUndid = false

    /**
     * One tap undoes right away, with no wait for a possible second tap. A quick second tap then
     * counts as a double tap: it takes back that undo and redoes one more stroke.
     */
    private fun twoFingerTap() {
        val now = SystemClock.elapsedRealtime()
        if (lastTwoTapAt != 0L && now - lastTwoTapAt < DOUBLE_TAP_MS) {
            lastTwoTapAt = 0L
            if (lastTwoTapUndid) redo()
            redo()
        } else {
            lastTwoTapAt = now
            lastTwoTapUndid = undo()
        }
    }

    /** Size of the corner toggle button; pen strokes there are ignored so it can be tapped. */
    var reservedWidth = 0
    var reservedHeight = 0

    /** Called on the main thread whenever the mode changes. */
    var onModeChanged: ((highlighting: Boolean) -> Unit)? = null

    fun toggleMode() = setHighlighter(!highlighting)

    private fun setHighlighter(on: Boolean) {
        main.removeCallbacks(revertRunnable)
        highlighting = on
        main.post { onModeChanged?.invoke(on) }
        touchHelper
            .setStrokeStyle(if (on) StrokeStyle.MARKER else StrokeStyle.PENCIL)
            .setStrokeWidth(if (on) HIGHLIGHT_WIDTH_PX else PEN_WIDTH_PX)
            .setStrokeColor(if (on) HIGHLIGHT_COLOR else Color.BLACK)
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
        val stroke = Stroke(highlight, FloatArray(pts.size * 2) { if (it % 2 == 0) pts[it / 2].x else pts[it / 2].y })
        synchronized(this) {
            strokes.add(stroke)
            clearedStrokes = null // drawing after a clear makes the old page unrecoverable
            redoStack.clear()
            bitmapCanvas?.let { render(it, stroke) }
        }
        val dirty = bounds(stroke)
        blit(dirty)
        main.post {
            if (highlight) {
                main.removeCallbacks(revertRunnable)
                main.postDelayed(revertRunnable, REVERT_TO_PEN_MS)
            }
            pendingSmooth.union(dirty)
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
        synchronized(this) { strokes.forEach { render(c, it) } }
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
        val area = Rect(pendingSmooth)
        pendingSmooth.setEmpty()
        area.intersect(0, 0, width, height)
        if (area.isEmpty) return
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
            .setExcludeRect(listOf(Rect(w - reservedWidth, 0, w, reservedHeight)))
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

    /** Writes the page to [file] (via a temp file, so a crash never leaves half a drawing). */
    fun save(file: File) {
        val copy = synchronized(this) { ArrayList(strokes) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { out ->
            out.writeInt(FILE_VERSION)
            out.writeInt(copy.size)
            for (s in copy) {
                out.writeBoolean(s.highlight)
                out.writeInt(s.xy.size)
                for (v in s.xy) out.writeFloat(v)
            }
        }
        tmp.renameTo(file)
    }

    /** Loads a page saved by [save]. Call before the surface exists. A bad file is ignored. */
    fun load(file: File) {
        if (!file.exists()) return
        try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != FILE_VERSION) return
                val loaded = ArrayList<Stroke>()
                repeat(input.readInt()) {
                    val highlight = input.readBoolean()
                    val xy = FloatArray(input.readInt()) { input.readFloat() }
                    if (xy.size >= 2 && xy.size % 2 == 0) loaded.add(Stroke(highlight, xy))
                }
                synchronized(this) {
                    strokes.clear()
                    strokes.addAll(loaded)
                    redrawAll()
                }
            }
        } catch (t: Exception) {
            // Unreadable file: start blank rather than crash
        }
    }

    private fun redrawAll() {
        bitmapCanvas?.let { c ->
            c.drawColor(Color.WHITE)
            strokes.forEach { render(c, it) }
        }
    }

    private fun refreshAfterEdit(area: Rect) {
        blit(area)
        repaint(area)
    }

    /** Wipes the page. [undo] brings it back as long as nothing is drawn afterwards. */
    fun clear() {
        synchronized(this) {
            if (strokes.isEmpty()) return
            clearedStrokes = ArrayList(strokes)
            strokes.clear()
            redoStack.clear()
            redrawAll()
        }
        blit()
        main.post {
            try {
                EpdController.applyGCOnce() // full refresh to clear ghosting
            } catch (t: Throwable) {
            }
            repaint(Rect(0, 0, width, height))
        }
    }

    /** Puts back the most recently undone stroke. False when there is none. */
    fun redo(): Boolean {
        val area: Rect
        synchronized(this) {
            if (redoStack.isEmpty()) return false
            val stroke = redoStack.removeAt(redoStack.size - 1)
            strokes.add(stroke)
            bitmapCanvas?.let { render(it, stroke) }
            area = bounds(stroke)
        }
        refreshAfterEdit(area)
        return true
    }

    /** Removes the last stroke, or restores a cleared page. False when there is nothing to undo. */
    fun undo(): Boolean {
        val area: Rect
        synchronized(this) {
            val cleared = clearedStrokes
            if (strokes.isNotEmpty()) {
                val last = strokes.removeAt(strokes.size - 1)
                redoStack.add(last)
                area = bounds(last)
            } else if (cleared != null) {
                strokes.addAll(cleared)
                clearedStrokes = null
                area = Rect(0, 0, width, height)
            } else {
                return false
            }
            redrawAll()
        }
        refreshAfterEdit(area)
        return true
    }

    fun pauseInput() {
        if (rawOpen) touchHelper.setRawDrawingEnabled(false)
    }

    fun resumeInput() {
        if (rawOpen) {
            blit()
            touchHelper.setRawDrawingEnabled(true)
        }
    }
}
