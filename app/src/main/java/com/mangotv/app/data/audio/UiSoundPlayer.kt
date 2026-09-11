package com.mangotv.app.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import com.mangotv.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Short, low-latency UI feedback: a "nav" tick on focus move, a "click"
 * tone on select, and a distinct "back" tone for leaving a screen (D-pad
 * BACK, or an on-screen Back button -- see TvFocusSurface.ClickSound) --
 * SoundPool rather than MediaPlayer since these fire constantly and can
 * overlap (e.g. a held D-pad scrolling fast through a row), which
 * MediaPlayer isn't built for. All three are loaded eagerly on
 * construction; SoundPool.play() on a sound that hasn't finished loading
 * yet is a silent no-op rather than a crash, so there's no need to gate
 * playback on the load callback for something this small.
 *
 * Volume tracks [SoundPreferencesRepository.preferences].navigationVolume
 * internally (a background collect, not something callers manage) so every
 * play*() call anywhere in the app always uses whatever the user last set
 * in Settings > Sounds, with no caller needing to know volume exists at
 * all.
 */
class UiSoundPlayer(context: Context, soundPreferencesRepository: SoundPreferencesRepository) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    private val backSoundId = soundPool.load(appContext, R.raw.ui_back_sound, 1)

    @Volatile
    private var volume: Float = 0.5f

    init {
        scope.launch {
            soundPreferencesRepository.preferences
                .map { it.navigationVolume }
                .distinctUntilChanged()
                .collect { volume = it }
        }
    }

    fun playNav() {
        soundPool.play(navSoundId, volume, volume, 0, 0, 1f)
    }

    fun playClick() {
        soundPool.play(clickSoundId, volume, volume, 0, 0, 1f)
    }

    fun playBack() {
        soundPool.play(backSoundId, volume, volume, 0, 0, 1f)
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
