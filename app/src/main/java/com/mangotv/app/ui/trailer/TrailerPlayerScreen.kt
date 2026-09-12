package com.mangotv.app.ui.trailer

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.mangotv.app.ui.theme.MangoAmber

/**
 * Plays a trailer inside the app via YouTube's own official embedded web
 * player (its IFrame Player), reached from Detail's Trailer button
 * instead of handing off to an external YouTube app/browser.
 *
 * Deliberately NOT MangoTV's own ExoPlayer-based player: showing YouTube
 * content via YouTube's own sanctioned embed is the only legitimate way
 * to keep this in-app at all -- extracting a raw playable stream URL
 * from YouTube directly would mean circumventing their player, which is
 * against YouTube's Terms of Service and breaks on a rolling basis as
 * YouTube changes things specifically to defeat that kind of extraction.
 * So this looks and controls like YouTube's own embedded player, not a
 * custom MangoTV one -- the closest legitimate approximation of "plays
 * inside the app" available here.
 *
 * The global BackHandler in MangoNavHost already pops this route like
 * any other on BACK (this screen has no menus/overlays of its own that
 * would need first-press-closes-that, later-press-exits handling the way
 * PlayerScreen does), so nothing extra is wired here for that.
 */
@SuppressLint("SetJavaScriptEnabled") // Only ever loads a fixed youtube.com/embed/ URL built from a server-verified video id, never arbitrary/user-supplied HTML.
@Composable
fun TrailerPlayerScreen(videoId: String) {
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Fire TV has no touchscreen to satisfy the platform's
                    // default "autoplay needs a real user gesture" rule --
                    // without this, the embedded player just sits on its
                    // own paused thumbnail forever, never actually
                    // starting on its own.
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    loadUrl(
                        "https://www.youtube.com/embed/$videoId" +
                            "?autoplay=1&playsinline=1&modestbranding=1&rel=0&fs=0"
                    )
                }
            },
            onRelease = { it.destroy() }
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MangoAmber
            )
        }
    }
}
