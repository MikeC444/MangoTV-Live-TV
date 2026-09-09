package com.mangotv.app.data.audio

import com.mangotv.app.R

/**
 * The 5 selectable app-boot chimes (Settings > Sounds). [rawResId] points at
 * a placeholder tone generated for this build -- swap the underlying
 * res/raw/boot_sound_N.wav files for real audio without touching this enum
 * or anything that references it.
 */
enum class BootSound(val label: String, val rawResId: Int) {
    CLASSIC("Classic", R.raw.boot_sound_1),
    CINEMATIC("Cinematic", R.raw.boot_sound_2),
    BRIGHT("Bright", R.raw.boot_sound_3),
    AMBIENT("Ambient", R.raw.boot_sound_4),
    RETRO("Retro", R.raw.boot_sound_5)
}
