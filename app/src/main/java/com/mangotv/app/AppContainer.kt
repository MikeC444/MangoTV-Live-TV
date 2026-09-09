package com.mangotv.app

import android.content.Context
import com.mangotv.app.data.addon.AddonRepository
import com.mangotv.app.data.audio.SoundPreferencesRepository
import com.mangotv.app.data.audio.UiSoundPlayer
import com.mangotv.app.data.player.PlayerPreferencesRepository
import com.mangotv.app.data.provider.HomeCacheRepository
import com.mangotv.app.data.provider.HomeRowPreferencesRepository
import com.mangotv.app.data.provider.MyListRepository

/**
 * A small hand-rolled container instead of a DI framework: this app only has
 * a couple of app-scoped singletons so far, and Hilt/Dagger would be a lot
 * of ceremony for that.
 *
 * addonRepository is constructed eagerly, not lazily: its init block kicks
 * off restoring installed addons from disk back into ProviderRegistry, and
 * Home reads ProviderRegistry directly without ever touching this
 * repository. A lazy property would only construct (and start that
 * restore) the first time something visits Settings > Addons — meaning on
 * a fresh process (e.g. right after a device reboot) Home would show
 * "library is empty" indefinitely even with addons already installed,
 * since nothing ever re-registered them.
 *
 * playerPreferencesRepository has no such cross-screen dependency — only
 * the player itself ever reads it — so it stays lazy and is only
 * constructed (and only then restores its DataStore-backed record) the
 * first time playback actually starts.
 *
 * homeRowPreferencesRepository is the same story as playerPreferencesRepository
 * — only Home and its Settings > Home Rows screen ever touch it, so it's
 * constructed lazily the first time either is visited rather than eagerly
 * at app startup.
 *
 * myListRepository is the same story again — only Home's hero, Detail's
 * hero, and the My List screen ever touch it.
 *
 * homeCacheRepository is the same story again — only HomeViewModel ever
 * touches it (to paint instantly from the last successful fetch on cold
 * boot instead of a blank skeleton every launch; see its own doc).
 *
 * soundPreferencesRepository/uiSoundPlayer follow the same lazy pattern
 * once more, even though MangoNavHost ends up touching both on the very
 * first frame (to pick/play the boot chime and provide UiSoundPlayer to
 * the rest of the tree via LocalUiSoundPlayer) -- lazy still costs nothing
 * there since that first access happens immediately either way, and it
 * keeps every property in this class following the same rule rather than
 * carving out a one-off exception.
 */
class AppContainer(context: Context) {
    val addonRepository: AddonRepository = AddonRepository(context)
    val playerPreferencesRepository: PlayerPreferencesRepository by lazy { PlayerPreferencesRepository(context) }
    val homeRowPreferencesRepository: HomeRowPreferencesRepository by lazy { HomeRowPreferencesRepository(context) }
    val myListRepository: MyListRepository by lazy { MyListRepository(context) }
    val homeCacheRepository: HomeCacheRepository by lazy { HomeCacheRepository(context) }
    val soundPreferencesRepository: SoundPreferencesRepository by lazy { SoundPreferencesRepository(context) }
    val uiSoundPlayer: UiSoundPlayer by lazy { UiSoundPlayer(context) }
}
