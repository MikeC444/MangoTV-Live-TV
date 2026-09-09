package com.mangotv.app.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import com.mangotv.app.R

/**
 * Short, low-latency UI feedback: a "nav" tick on focus move and a "click"
 * tone on select -- SoundPool rather than MediaPlayer since these fire
 * constantly and can overlap (e.g. a held D-pad scrolling fast through a
 * row), which MediaPlayer isn't built for. Both sounds are tiny and loaded
 * eagerly on construction; SoundPool.play() on a sound that hasn't finished
 * loading yet is a silent no-op rather than a crash, so there's no need to
 * gate playback on the load callback for something this small.
 */
class UiSoundPlayer(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val navSoundId = soundPool.load(appContext, R.raw.ui_nav_sound, 1)
    private val clickSoundId = soundPool.load(appContext, R.raw.ui_click_sound, 1)

    fun playNav() {
        soundPool.play(navSoundId, 1f, 1f, 0, 0, 1f)
    }

    fun playClick() {
        soundPool.play(clickSoundId, 1f, 1f, 0, 0, 1f)
    }
}

/**
 * Lets TvFocusSurface -- the one shared building block behind every
 * focusable card/button/nav item -- reach the app-scoped UiSoundPlayer
 * without threading it through every composable's parameter list in
 * between. Defaults to null (silent) rather than throwing, so any
 * composable previewed or tested outside the real app tree (which provides
 * a real instance from AppContainer in MangoNavHost) still works, just
 * without sound.
 */
val LocalUiSoundPlayer = staticCompositionLocalOf<UiSoundPlayer?> { null }
