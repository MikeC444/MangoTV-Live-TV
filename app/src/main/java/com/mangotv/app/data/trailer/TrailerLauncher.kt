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
     * registers for, so every one of them can offer to open it.
     */
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /**
     * Opens [videoId] in whichever app the user picks.
     *
     * A bare ACTION_VIEW rather than `Intent.createChooser`, because the two
     * produce different system pickers and this is the one worth having.
     * A plain view intent with no default set yet gets the "Open with...
     * JUST ONCE / ALWAYS" disambiguation dialog, so the user can make the
     * choice stick and never be asked again. `createChooser` deliberately
     * strips the ALWAYS option -- a chooser is by definition a one-off
     * choice -- and forces its own full-screen picker in front of the user
     * on every single trailer, forever.
     *
     * The trade is that once a default *is* set (by ALWAYS here, or by a
     * verified app link, or in system settings) no picker appears at all
     * and the trailer just opens. That's the point of ALWAYS, and the user
     * can still change it later from the system's app settings.
     */
    fun launch(context: Context, videoId: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl(videoId)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Reachable in a way the chooser version wasn't: without a chooser
        // wrapped around it, a view intent no app can handle throws
        // ActivityNotFoundException rather than landing on a system screen
        // that explains itself. Catching it keeps a tap on Trailer from
        // being silent.
        if (runCatching { context.startActivity(view) }.isFailure) {
            Toast.makeText(context, "No app on this device can open trailers", Toast.LENGTH_LONG).show()
        }
    }
}
