package dev.sketch

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
import androidx.print.PrintHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

object Exporter {
    // Go 6 Gen2 panel density; PDF pages are in 1/72 inch
    private const val PANEL_PPI = 300f
    private const val EMAIL_TO = "bomberstudios@gmail.com"

    fun copyToClipboard(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "sketch.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val clip = ClipData.newUri(context.contentResolver, "Sketch", uri)
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
        val name = "sketch-${timestamp()}.pdf"
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

    /** Sends the PDF straight to [EMAIL_TO] over SMTP. Blocks: call off the main thread. */
    fun emailPdf(bitmap: Bitmap): String {
        if (BuildConfig.SMTP_USER.isEmpty() || BuildConfig.SMTP_PASSWORD.isEmpty()) {
            return "Set smtp.user and smtp.password in local.properties, then rebuild"
        }
        val pdf = ByteArrayOutputStream().also { writePdf(bitmap, it) }.toByteArray()

        val props = Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "465")
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.writetimeout", "30000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(BuildConfig.SMTP_USER, BuildConfig.SMTP_PASSWORD)
        })
        return try {
            val body = MimeBodyPart().apply { setText("Sketch attached.") }
            val attachment = MimeBodyPart().apply {
                dataHandler = DataHandler(ByteArrayDataSource(pdf, "application/pdf"))
                fileName = "sketch-${timestamp()}.pdf"
            }
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(BuildConfig.SMTP_USER))
                setRecipient(Message.RecipientType.TO, InternetAddress(EMAIL_TO))
                subject = "Sketch"
                setContent(MimeMultipart().apply { addBodyPart(body); addBodyPart(attachment) })
            }
            Transport.send(message)
            "Sent to $EMAIL_TO"
        } catch (e: Exception) {
            "Email failed: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Opens Android's share sheet with the sketch as a PDF (LocalSend, Drive, etc.). */
    fun share(activity: Activity, bitmap: Bitmap): String? {
        val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("sketch-") && f.name.endsWith(".pdf") }?.forEach { it.delete() }
        val file = File(dir, "sketch-${timestamp()}.pdf")
        file.outputStream().use { writePdf(bitmap, it) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.runOnUiThread { activity.startActivity(Intent.createChooser(send, "Share sketch")) }
        return null
    }

    fun print(activity: Activity, bitmap: Bitmap) {
        PrintHelper(activity).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
            colorMode = PrintHelper.COLOR_MODE_MONOCHROME
        }.printBitmap("Sketch", bitmap)
    }
}
