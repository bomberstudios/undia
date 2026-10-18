package com.bomberstudios.undia

import java.util.Locale

/** One stroke of a page, in panel pixels: x and y alternate in [xy]. */
open class InkStroke(val highlight: Boolean, val xy: FloatArray)

/** A page as a plain SVG: one polyline per stroke. No Android classes, so it runs in unit tests. */
object PageFile {
    const val HIGHLIGHT_HEX = "#ffcc00"

    private val polyline = Regex("<polyline\\b([^>]*)>")
    private val attribute = Regex("([\\w:-]+)\\s*=\\s*\"([^\"]*)\"")

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

    /** Reads the strokes of a page. Anything unreadable is an empty page. */
    fun parse(svg: String): List<InkStroke> {
        val loaded = ArrayList<InkStroke>()
        try {
            for (tag in polyline.findAll(svg)) {
                val attrs = attribute.findAll(tag.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
                val highlight = attrs["stroke"]?.equals(HIGHLIGHT_HEX, ignoreCase = true) == true
                val nums = attrs["points"]
                    ?.split(' ', ',', '\n', '\t')?.filter { it.isNotEmpty() }?.map { it.toFloat() }
                    ?: continue
                if (nums.size >= 2 && nums.size % 2 == 0) loaded.add(InkStroke(highlight, nums.toFloatArray()))
            }
        } catch (t: NumberFormatException) {
            return emptyList()
        }
        return loaded
    }
}
