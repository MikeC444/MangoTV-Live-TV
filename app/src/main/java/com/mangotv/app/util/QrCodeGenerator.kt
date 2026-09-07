package com.mangotv.app.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    fun generate(content: String, sizePx: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                val color = if (matrix.get(x, y)) BLACK else WHITE
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    private val BLACK = android.graphics.Color.BLACK
    private val WHITE = android.graphics.Color.WHITE
}
