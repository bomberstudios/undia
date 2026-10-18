package com.bomberstudios.undia

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFileTest {
    private fun svg(list: List<InkStroke>) = PageFile.toSvg(list, 1448, 1072, 2.4f, 47.2f)

    @Test fun `strokes survive a round trip`() {
        val pen = InkStroke(false, floatArrayOf(10f, 20f, 30.5f, 40.5f, 100f, 200f))
        val marker = InkStroke(true, floatArrayOf(5f, 6f, 7f, 8f))
        val back = PageFile.parse(svg(listOf(pen, marker)))
        assertEquals(2, back.size)
        assertFalse(back[0].highlight)
        assertTrue(back[1].highlight)
        assertArrayEquals(pen.xy, back[0].xy, 0.05f)
        assertArrayEquals(marker.xy, back[1].xy, 0.05f)
    }

    @Test fun `an empty page has no strokes`() {
        assertEquals(0, PageFile.parse(svg(emptyList())).size)
    }

    @Test fun `a single point becomes a dot that reads back`() {
        val back = PageFile.parse(svg(listOf(InkStroke(false, floatArrayOf(50f, 60f)))))
        assertEquals(1, back.size)
        assertArrayEquals(floatArrayOf(50f, 60f, 50f, 60f), back[0].xy, 0.05f)
    }

    @Test fun `file is a valid svg with the page size`() {
        val s = svg(emptyList())
        assertTrue(s.contains("width=\"1448\" height=\"1072\" viewBox=\"0 0 1448 1072\""))
        assertTrue(s.trimEnd().endsWith("</svg>"))
    }

    @Test fun `highlighter keeps its colour and blend mode`() {
        val s = svg(listOf(InkStroke(true, floatArrayOf(1f, 2f, 3f, 4f))))
        assertTrue(s.contains("stroke=\"#ffcc00\""))
        assertTrue(s.contains("mix-blend-mode:darken"))
    }

    @Test fun `highlight colour is matched case-insensitively`() {
        val back = PageFile.parse("""<polyline stroke="#FFCC00" points="1,2 3,4"/>""")
        assertTrue(back.single().highlight)
    }

    @Test fun `garbage reads as an empty page`() {
        assertEquals(0, PageFile.parse("not an svg").size)
        assertEquals(0, PageFile.parse("""<polyline stroke="#000000" points="1,2 x,4"/>""").size)
    }

    @Test fun `odd or empty point lists are skipped`() {
        val s = """<polyline points="1,2,3"/><polyline points=""/><polyline stroke="#000000" points="5,6 7,8"/>"""
        assertEquals(1, PageFile.parse(s).size)
    }

    @Test fun `many points keep their order`() {
        val xy = FloatArray(2000) { (it * 3 % 1000).toFloat() }
        assertArrayEquals(xy, PageFile.parse(svg(listOf(InkStroke(false, xy)))).single().xy, 0.05f)
    }
}
