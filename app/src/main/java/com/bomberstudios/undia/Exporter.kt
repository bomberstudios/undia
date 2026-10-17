package com.bomberstudios.undia

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {
    // Go 6 Gen2 panel density; PDF pages are in 1/72 inch
    private const val PANEL_PPI = 300f

    fun copyToClipboard(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "undia.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val clip = ClipData.newUri(context.contentResolver, "undia", uri)
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    private fun writePdf(bitmap: Bitmap, out: OutputStream) {
        val widthPt = (bitmap.width / PANEL_PPI * 72).toInt()
        val heightPt = (bitmap.height / PANEL_PPI * 72).toInt()
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(widthPt, heightPt, 1).create())
            page.canvas.drawBitmap(
                bitmap, null, Rect(0, 0, widthPt, heightPt), Paint(Paint.FILTER_BITMAP_FLAG)
            )
            pdf.finishPage(page)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /** Saves to Downloads; returns the file name. */
    fun saveAsPdf(context: Context, bitmap: Bitmap): String {
        val name = "undia-${timestamp()}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create $name")
        resolver.openOutputStream(uri)!!.use { writePdf(bitmap, it) }
        return name
    }

    /** Opens Android's share sheet with the page as a PDF (LocalSend, Drive, etc.). */
    fun share(activity: Activity, bitmap: Bitmap): String? {
        val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("undia-") && f.name.endsWith(".pdf") }?.forEach { it.delete() }
        val file = File(dir, "undia-${timestamp()}.pdf")
        file.outputStream().use { writePdf(bitmap, it) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.runOnUiThread { activity.startActivity(Intent.createChooser(send, "Share page")) }
        return null
    }
}
