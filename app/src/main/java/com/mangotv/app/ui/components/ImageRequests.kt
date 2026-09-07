package com.mangotv.app.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest

/**
 * For opaque photographic art only (posters, backdrops, cast photos, episode
 * thumbnails) — never for logo/clearlogo images, which need their alpha
 * channel. Forcing RGB_565 halves per-pixel decode/memory cost, which matters
 * most on the low-RAM Fire TV Stick hardware this app targets (minSdk 23);
 * it also opts devices that would otherwise get a zero-copy HARDWARE bitmap
 * (API 26+) out of that path in favor of this smaller software one, which is
 * the right trade for that hardware.
 */
@Composable
fun rememberOpaqueImageRequest(url: String?): ImageRequest {
    val context = LocalContext.current
    return remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()
    }
}
