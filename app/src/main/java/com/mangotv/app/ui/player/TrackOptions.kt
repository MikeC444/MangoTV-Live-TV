package com.mangotv.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

/**
 * A null [trackGroup]/[trackIndex] represents the synthetic "Off" (subtitles)
 * or "Auto" (quality) option rather than a real track.
 */
data class AudioTrackOption(
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

data class SubtitleTrackOption(
    val trackGroup: TrackGroup?,
    val trackIndex: Int?,
    val label: String,
    val isSelected: Boolean
)

data class QualityOption(
    val trackGroup: TrackGroup?,
    val trackIndex: Int?,
    val label: String,
    val height: Int?,
    val isSelected: Boolean
)

private fun languageDisplayName(language: String?): String? =
    language?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Locale(it).displayLanguage }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }

private fun channelCountLabel(channelCount: Int): String? = when (channelCount) {
    1 -> "Mono"
    2 -> "Stereo"
    6, 8 -> "5.1"
    else -> null
}

/** Every actually-decodable audio track — there's always at least the one currently playing. */
fun Tracks.toAudioTrackOptions(): List<AudioTrackOption> {
    val options = mutableListOf<AudioTrackOption>()
    for (group in groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val language = languageDisplayName(format.language)
            val channelLabel = channelCountLabel(format.channelCount)
            val label = format.label
                ?: listOfNotNull(language ?: "Unknown", channelLabel).joinToString(" — ")
            options += AudioTrackOption(group.mediaTrackGroup, trackIndex, label, group.isTrackSelected(trackIndex))
        }
    }
    return options
}

/** Always includes a leading "Off" option; empty otherwise means no embedded subtitles exist. */
fun Tracks.toSubtitleTrackOptions(): List<SubtitleTrackOption> {
    val options = mutableListOf<SubtitleTrackOption>()
    var anySelected = false
    for (group in groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val label = format.label ?: languageDisplayName(format.language) ?: "Unknown"
            val selected = group.isTrackSelected(trackIndex)
            if (selected) anySelected = true
            options += SubtitleTrackOption(group.mediaTrackGroup, trackIndex, label, selected)
        }
    }
    return listOf(SubtitleTrackOption(null, null, "Off", isSelected = !anySelected)) + options
}

/** Always includes a leading "Auto" option; only worth showing a menu when >1 real rendition exists. */
fun Tracks.toQualityOptions(): List<QualityOption> {
    val options = mutableListOf<QualityOption>()
    var anySelected = false
    for (group in groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val height = format.height.takeIf { it > 0 }
            val selected = group.isTrackSelected(trackIndex)
            if (selected) anySelected = true
            options += QualityOption(group.mediaTrackGroup, trackIndex, height?.let { "${it}p" } ?: "Unknown", height, selected)
        }
    }
    val sorted = options.sortedByDescending { it.height ?: 0 }
    return listOf(QualityOption(null, null, "Auto", null, isSelected = !anySelected)) + sorted
}

fun ExoPlayer.selectAudioTrack(option: AudioTrackOption) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndex))
        .build()
}

fun ExoPlayer.selectSubtitleTrack(option: SubtitleTrackOption) {
    val builder = trackSelectionParameters.buildUpon()
    if (option.trackGroup == null || option.trackIndex == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        builder.setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndex))
    }
    trackSelectionParameters = builder.build()
}

fun ExoPlayer.selectQuality(option: QualityOption) {
    val builder = trackSelectionParameters.buildUpon()
    if (option.trackGroup == null || option.trackIndex == null) {
        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    } else {
        builder.setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndex))
    }
    trackSelectionParameters = builder.build()
}
