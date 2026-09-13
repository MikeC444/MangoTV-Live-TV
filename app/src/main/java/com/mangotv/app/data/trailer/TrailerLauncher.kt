package com.mangotv.app.data.trailer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Hands a trailer off to whatever app on the device the user wants to watch
 * it in -- the YouTube app, a browser, anything else registered for YouTube
 * links -- instead of playing it inside MangoTV.
 *
 * MangoTV used to play trailers itself, first through a WebView wrapped
 * around YouTube's own embedded player and then through its own ExoPlayer
 * fed by a YouTube stream extractor. Both fought YouTube rather than working
 * with it: the embed never got past its referrer/origin validation ("video
 * player configuration error", IFrame API onError 152), and the extractor
 * traded that for an ongoing dependency on YouTube's internal, undocumented
 * player API -- outside YouTube's Terms of Service, and still landing on a
 * black screen. Handing off to an app that is *meant* to play YouTube video
 * ends that whole class of problem: no extraction, no codec guessing, no
 * embed validation, and the user gets their own player, signed in, with
 * their own settings.
 */
object TrailerLauncher {

    /**
     * The canonical watch URL rather than YouTube's `vnd.youtube:` scheme:
     * this is deliberately not a YouTube-app-only handoff, and an https
     * youtube.com link is what every browser and every YouTube client
     * registers for, so the chooser can offer all of them.
     */
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /**
     * Opens the app chooser for [videoId].
     *
     * Always a chooser, never a bare ACTION_VIEW: a plain view intent goes
     * straight to whatever the user has already set as their default handler
     * for web links, which on a TV is usually a browser and is exactly the
     * choice this is meant to put back in the user's hands each time.
     */
    fun launch(context: Context, videoId: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl(videoId)))
        val chooser = Intent.createChooser(view, "Watch trailer with")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // The chooser itself is a system activity, so this practically never
        // fails -- with no app able to open the link the chooser says so
        // itself rather than throwing. The fallback is for the case where
        // even that can't be started, so a tap on Trailer is never silent.
        if (runCatching { context.startActivity(chooser) }.isFailure) {
            Toast.makeText(context, "No app on this device can open trailers", Toast.LENGTH_LONG).show()
        }
    }
}
