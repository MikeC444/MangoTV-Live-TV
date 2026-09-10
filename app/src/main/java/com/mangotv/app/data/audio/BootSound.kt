package com.mangotv.app.data.audio

import com.mangotv.app.R

/**
 * The selectable app-boot chimes (Settings > Sounds), plus [NONE] to turn
 * the chime off entirely. Labels are plain "Boot Sound N" for now -- rename
 * any of them here to whatever's more fitting once there's something to
 * call them by. [rawResId] is null only for [NONE] -- every caller that
 * plays a BootSound (BootSoundPlayer) treats a null resId as "nothing to
 * play", so NONE never needs special-casing anywhere else.
 */
enum class BootSound(val label: String, val rawResId: Int?) {
    NONE("No Boot Sound", null),
    BOOT_SOUND_1("Boot Sound 1", R.raw.boot_sound_1),
    BOOT_SOUND_2("Boot Sound 2", R.raw.boot_sound_2),
    BOOT_SOUND_3("Boot Sound 3", R.raw.boot_sound_3),
    BOOT_SOUND_4("Boot Sound 4", R.raw.boot_sound_4),
    BOOT_SOUND_5("Boot Sound 5", R.raw.boot_sound_5)
}
