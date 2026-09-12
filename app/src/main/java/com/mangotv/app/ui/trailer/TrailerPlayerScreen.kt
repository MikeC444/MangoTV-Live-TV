package com.mangotv.app.ui.trailer

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

private const val TAG = "TrailerPlayerScreen"

// Prefix a plain console.log message with this so onConsoleMessage below can
// tell a real YouTube IFrame Player API error apart from any other console
// noise the embedded page happens to produce.
private const val ERROR_CONSOLE_PREFIX = "MANGOTV_TRAILER_ERROR:"

// The IFrame Player API's own onError event codes -- see
// https://developers.google.com/youtube/iframe_api_reference#onError .
// 101 and 150 are the same condition (the video owner disabled playback on
// other sites); everything else is grouped into one generic message since
// none of them are anything the user can act on from here.
private fun messageForYouTubeErrorCode(code: String): String = when (code) {
    "101", "150" -> "The owner of this video has disabled playback outside YouTube."
    "100" -> "This trailer is no longer available."
    else -> "This trailer couldn't be played right now."
}

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
 * Built on the real IFrame Player API (youtube.com/iframe_api +
 * `new YT.Player(...)`, per Google's own documented integration), not a
 * bare `<iframe src="…/embed/…">`. That's deliberate: only the real API
 * fires an `onError` event with an actual numeric cause (video removed,
 * embedding disabled by the owner, etc.) -- without it, any failure just
 * renders as YouTube's own in-page error card with nothing an app-level
 * WebViewClient/WebChromeClient callback can see or react to, which is
 * exactly what made the first two attempts at this screen impossible to
 * diagnose from Logcat alone. The API's callbacks run as plain JS in the
 * page, so they're wired to call `console.log(...)` with a recognizable
 * prefix, which onConsoleMessage below both logs (tagged, with the real
 * numeric code, for adb logcat) and turns into a clean in-app message --
 * see [messageForYouTubeErrorCode] -- instead of leaving YouTube's raw
 * error card on screen looking like something MangoTV itself got wrong.
 *
 * The user agent string has its "; wv" WebView marker stripped for the
 * same reason Chrome Custom Tabs exist for Google sign-in: Google
 * properties, YouTube included, are known to treat traffic that
 * self-identifies as an embedded WebView differently (and more
 * restrictively) than an ordinary browser. Presenting an ordinary-looking
 * Chrome UA avoids that distinction entirely.
 *
 * The global BackHandler in MangoNavHost already pops this route like
 * any other on BACK (this screen has no menus/overlays of its own that
 * would need first-press-closes-that, later-press-exits handling the way
 * PlayerScreen does), so nothing extra is wired here for that.
 */
@SuppressLint("SetJavaScriptEnabled") // Only ever loads a fixed, locally-built wrapper page embedding a server-verified youtube.com video id, never arbitrary/user-supplied HTML.
@Composable
fun TrailerPlayerScreen(videoId: String) {
    var isLoading by remember { mutableStateOf(true) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    // YouTube's HTML5 player promotes its <video> element into a
    // Chromium "custom view" for actual playback -- a bare
    // WebChromeClient() (no onShowCustomView/onHideCustomView override)
    // has nowhere to attach that view. Holding the callback's own view
    // here and layering it in a second AndroidView above the WebView is
    // the standard fix: give Chromium a real place to put it.
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

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
                    // See this file's own kdoc -- makes this WebView look
                    // like an ordinary browser to YouTube rather than an
                    // embedded WebView.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                            if (customView != null) {
                                callback.onCustomViewHidden()
                                return
                            }
                            customView = view
                            customViewCallback = callback
                        }

                        override fun onHideCustomView() {
                            customViewCallback?.onCustomViewHidden()
                            customView = null
                            customViewCallback = null
                        }

                        // Forwards the embedded page's own JS console into
                        // Logcat -- filter on this class's own tag to find
                        // it. Also watches for the onError bridge message
                        // the wrapper page's YT.Player is wired to log (see
                        // this file's own kdoc), which is what actually
                        // carries YouTube's real numeric error code -- the
                        // one thing a plain <iframe> with no JS API gives
                        // no way to observe at all.
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            val message = consoleMessage.message()
                            Log.d(
                                TAG,
                                "console: $message " +
                                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                            )
                            if (message.startsWith(ERROR_CONSOLE_PREFIX)) {
                                val code = message.removePrefix(ERROR_CONSOLE_PREFIX).trim()
                                Log.w(TAG, "YouTube IFrame API onError code=$code")
                                errorCode = code
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    // baseUrl is youtube.com itself (not this app, and not
                    // blank) so the IFrame Player API script and the real
                    // embed iframe it creates both load as same-origin
                    // youtube.com content, not as something cross-origin
                    // from a mismatched or missing origin. The explicit
                    // <meta name="referrer"> above matters more than it
                    // would on a normally-fetched page: this HTML is
                    // synthetic (handed to the WebView directly, never
                    // actually fetched over the network), and Chromium's
                    // default referrer behavior for that kind of page's own
                    // sub-requests isn't reliable -- YouTube's player
                    // requires a verifiable referrer/origin to authorize
                    // playback at all, and a missing one is exactly what
                    // surfaces as onError code 150/152/153 ("video player
                    // configuration error") regardless of which video.
                    val wrapperHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                          html, body { margin: 0; padding: 0; background: #000; overflow: hidden; }
                          #player { position: fixed; top: 0; left: 0; width: 100%; height: 100%; }
                        </style>
                        </head>
                        <body>
                        <div id="player"></div>
                        <script src="https://www.youtube.com/iframe_api"></script>
                        <script>
                          function onYouTubeIframeAPIReady() {
                            new YT.Player('player', {
                              videoId: '$videoId',
                              playerVars: {
                                autoplay: 1,
                                playsinline: 1,
                                modestbranding: 1,
                                rel: 0,
                                fs: 0
                              },
                              events: {
                                onError: function(event) {
                                  console.log('$ERROR_CONSOLE_PREFIX' + event.data);
                                }
                              }
                            });
                          }
                        </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube.com", wrapperHtml, "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() }
        )

        // Reuses the exact View instance Chromium handed to
        // onShowCustomView -- factory below only runs once (AndroidView
        // keys its recomposition on the View identity, and this
        // composable itself only recomposes when customView changes), so
        // this never tries to create a second, competing view for the
        // same playback.
        customView?.let { view ->
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { view })
        }

        when {
            // Covers YouTube's own in-page error card with a MangoTV-styled
            // message instead of leaving it showing underneath -- it's
            // still there in the WebView, just fully occluded by this.
            errorCode != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Trailer unavailable",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = messageForYouTubeErrorCode(errorCode.orEmpty()),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            isLoading && customView == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MangoAmber
                )
            }
        }
    }
}
