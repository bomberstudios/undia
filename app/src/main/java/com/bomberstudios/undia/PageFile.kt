package com.bomberstudios.undia

import java.util.Locale

/** One stroke of a page, in panel pixels: x and y alternate in [xy]. */
open class InkStroke(val highlight: Boolean, val xy: FloatArray)

/** A page as a plain SVG: one polyline per stroke. No Android classes, so it runs in unit tests. */
object PageFile {
    const val HIGHLIGHT_HEX = "#ffcc00"

    private fun fmt(v: Float) = String.format(Locale.US, "%.1f", v)

    fun toSvg(list: List<InkStroke>, w: Int, h: Int, penWidthPx: Float, highlightWidthPx: Float): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">\n")
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n")
        sb.append("<g fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")
        for (s in list) {
            if (s.highlight) {
                sb.append("<polyline stroke=\"$HIGHLIGHT_HEX\" stroke-width=\"${fmt(highlightWidthPx)}\" style=\"mix-blend-mode:darken\" points=\"")
            } else {
                sb.append("<polyline stroke=\"#000000\" stroke-width=\"${fmt(penWidthPx)}\" points=\"")
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

    /**
     * Reads the strokes of a page. Anything unreadable is an empty page. A plain scanner, not a
     * regex: pages hold hundreds of thousands of numbers and the Android regex engine is far too
     * slow for that on the main thread.
     */
    fun parse(svg: String): List<InkStroke> {
        val loaded = ArrayList<InkStroke>()
        var at = svg.indexOf("<polyline")
        while (at >= 0) {
            val end = svg.indexOf('>', at)
            if (end < 0) break
            val stroke = attribute(svg, at, end, "stroke")
            val pointsAt = valueStart(svg, at, end, "points")
            if (pointsAt >= 0) {
                val xy = numbers(svg, pointsAt, svg.indexOf('"', pointsAt).takeIf { it in 0..end } ?: end)
                    ?: return emptyList()
                if (xy.size >= 2 && xy.size % 2 == 0) {
                    loaded.add(InkStroke(stroke?.equals(HIGHLIGHT_HEX, ignoreCase = true) == true, xy))
                }
            }
            at = svg.indexOf("<polyline", end)
        }
        return loaded
    }

    /** Where the value of name="..." starts inside the tag [from, to), or -1. */
    private fun valueStart(s: String, from: Int, to: Int, name: String): Int {
        var i = from
        val key = "$name=\""
        while (true) {
            i = s.indexOf(key, i)
            if (i < 0 || i >= to) return -1
            // must be a whole attribute name, not the tail of another one (stroke-width)
            if (s[i - 1].isWhitespace()) return i + key.length
            i += key.length
        }
    }

    private fun attribute(s: String, from: Int, to: Int, name: String): String? {
        val start = valueStart(s, from, to, name)
        if (start < 0) return null
        val close = s.indexOf('"', start)
        return if (close in 0..to) s.substring(start, close) else null
    }

    /** The numbers in s[from, to), separated by spaces, commas or newlines; null if one is not a number. */
    private fun numbers(s: String, from: Int, to: Int): FloatArray? {
        var out = FloatArray(64)
        var n = 0
        var i = from
        while (i < to) {
            val c = s[i]
            if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r') { i++; continue }
            var j = i
            while (j < to && s[j] != ' ' && s[j] != ',' && s[j] != '\n' && s[j] != '\t' && s[j] != '\r') j++
            val v = s.substring(i, j).toFloatOrNull() ?: return null
            if (n == out.size) out = out.copyOf(n * 2)
            out[n++] = v
            i = j
        }
        return out.copyOf(n)
    }
}
