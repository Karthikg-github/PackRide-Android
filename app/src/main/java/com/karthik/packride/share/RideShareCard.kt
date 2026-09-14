package com.karthik.packride.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Creates a consistent, platform-shareable PackRide image card. */
object RideShareCard {
    fun share(
        context: Context,
        title: String,
        rider: String,
        distance: String,
        duration: String,
        detail: String? = null
    ) {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(248, 248, 248))

        paint.color = Color.rgb(255, 106, 0)
        canvas.drawRect(0f, 0f, 1080f, 24f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 64f
        paint.color = Color.rgb(26, 26, 28)
        canvas.drawText("PACKRIDE", 72f, 130f, paint)

        paint.textSize = 34f
        paint.color = Color.rgb(105, 105, 110)
        canvas.drawText("RIDE SUMMARY", 72f, 190f, paint)

        paint.textSize = 72f
        paint.color = Color.rgb(26, 26, 28)
        drawWrapped(canvas, title, 72f, 320f, 936f, 84f, paint)

        paint.textSize = 28f
        paint.color = Color.rgb(105, 105, 110)
        canvas.drawText("DISTANCE", 72f, 650f, paint)
        canvas.drawText("DURATION", 570f, 650f, paint)
        paint.textSize = 58f
        paint.color = Color.rgb(43, 110, 133)
        canvas.drawText(distance, 72f, 720f, paint)
        canvas.drawText(duration, 570f, 720f, paint)

        detail?.takeIf { it.isNotBlank() }?.let {
            paint.textSize = 32f
            paint.color = Color.rgb(105, 105, 110)
            canvas.drawText(it.take(48), 72f, 820f, paint)
        }
        paint.textSize = 34f
        paint.color = Color.rgb(26, 26, 28)
        canvas.drawText("$rider • Ride together. Ride safer.", 72f, 970f, paint)

        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(dir, "packride-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val text = "$rider's ride: $title — $distance, $duration on PackRide!"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Ride"))
    }

    private fun drawWrapped(canvas: Canvas, text: String, x: Float, y: Float, width: Float, lineHeight: Float, paint: Paint) {
        var line = ""
        var drawY = y
        text.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > width && line.isNotEmpty()) {
                canvas.drawText(line, x, drawY, paint)
                line = word
                drawY += lineHeight
            } else line = candidate
        }
        if (line.isNotEmpty()) canvas.drawText(line, x, drawY, paint)
    }
}
