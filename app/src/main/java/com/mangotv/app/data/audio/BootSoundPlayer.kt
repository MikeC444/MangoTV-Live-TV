package com.mangotv.app.data.audio

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.delay

/**
 * Plays a boot chime. MediaPlayer (not SoundPool -- see UiSoundPlayer)
 * since this is a single longer clip rather than short, overlapping UI
 * blips.
 *
 * Two independent instances of this class exist in the app, each used for
 * a different purpose: MangoNavHost owns one for the real cold-boot chime
 * ([startAndAwaitMidpoint]); Settings > Sounds owns its own, short-lived
 * one purely for previewing a pick before it's saved ([playPreview]).
 */
class BootSoundPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Starts playback immediately and suspends only until the clip reaches
     * its own halfway point, not until it finishes -- playback keeps
     * running in the background past that point and is never stopped
     * early by this class (it releases itself on natural completion).
     * MangoNavHost awaits this alongside Home's own data/image readiness
     * before swapping LoadingScreen out for the real UI, so in the common
     * case (data loads faster than half the chime's length) the reveal
     * lands right on the chime's midpoint, the way a splash sound is
     * supposed to land. On a slow connection where data takes longer than
     * that, the chime will already be past its midpoint by the time Home
     * appears -- an accepted tradeoff, since forcing a LONGER wait than the
     * real loading time purely to keep chasing the midpoint would make
     * cold boot feel slower for no benefit.
     *
     * [BootSound.NONE] has no [BootSound.rawResId] to play -- this returns
     * immediately, so the "midpoint" wait is simply skipped rather than
     * needing special-casing at every call site.
     */
    suspend fun startAndAwaitMidpoint(sound: BootSound) {
        val resId = sound.rawResId ?: return
        val player = start(resId) ?: return
        val halfwayMs = (player.duration / 2).coerceAtLeast(0)
        if (halfwayMs > 0) delay(halfwayMs.toLong())
    }

    /**
     * Fire-and-forget: Settings > Sounds plays a full preview of whichever
     * chime the user just picked. Picking [BootSound.NONE] instead stops
     * whatever preview might already be playing, rather than leaving it
     * running -- "silence" should take effect immediately too.
     */
    fun playPreview(sound: BootSound) {
        val resId = sound.rawResId
        if (resId == null) {
            release()
            return
        }
        start(resId)
    }

    /** Stops and releases whatever's currently playing on this instance, if anything. */
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun start(rawResId: Int): MediaPlayer? {
        release()
        val player = runCatching { MediaPlayer.create(context.applicationContext, rawResId) }.getOrNull()
            ?: return null
        mediaPlayer = player
        player.setOnCompletionListener { it.release() }
        player.start()
        return player
    }
}
