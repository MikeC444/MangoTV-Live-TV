package com.mangotv.app.data.trailer

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * Answers whether this device can actually decode a given video format.
 *
 * [InAppYouTubeExtractor] needs this because YouTube offers the same video
 * in several codecs and it is *not* free to pick whichever is highest
 * resolution: above 1080p YouTube only publishes VP9 and AV1, and plenty of
 * Fire TV hardware has no AV1 decoder at all (and older sticks cap VP9 well
 * below 4K). Handing ExoPlayer a stream this device can't decode does not
 * produce an error -- the track selector simply finds no supported video
 * track, selects nothing for the video renderer, and playback continues with
 * audio only over a black screen. Filtering those formats out before they're
 * ever chosen is what keeps that from happening.
 *
 * An interface (rather than a direct MediaCodec call at the point of use) so
 * the extractor's selection logic stays unit-testable off-device.
 */
fun interface VideoFormatSupport {
    /**
     * @param codecs the RFC 6381 codecs string YouTube reports for the
     *   format (e.g. `av01.0.05M.08`, `avc1.640028`), as parsed out of its
     *   `mimeType`. Blank/unrecognised means "can't tell", which callers
     *   treat as supported rather than discarding a possibly-fine format.
     */
    fun canDecode(codecs: String, width: Int, height: Int, frameRate: Int): Boolean
}

/**
 * The real, device-backed [VideoFormatSupport], asking the same Media3
 * capability logic the track selector itself will apply a moment later --
 * so the format this picks is by construction one that won't get silently
 * dropped during track selection.
 */
@OptIn(UnstableApi::class)
object DeviceVideoFormatSupport : VideoFormatSupport {

    private const val TAG = "TrailerCodecSupport"

    override fun canDecode(codecs: String, width: Int, height: Int, frameRate: Int): Boolean {
        // No codecs string, or one Media3 doesn't recognise -- no basis to
        // rule the format out, so don't. Worst case is the behaviour this
        // check replaced.
        val sampleMimeType = MimeTypes.getVideoMediaMimeType(codecs.ifBlank { null }) ?: return true

        val decoders = try {
            MediaCodecUtil.getDecoderInfos(sampleMimeType, /* secure= */ false, /* tunneling= */ false)
        } catch (error: MediaCodecUtil.DecoderQueryException) {
            Log.w(TAG, "Decoder query failed for $sampleMimeType, assuming supported: ${error.message}")
            return true
        }
        if (decoders.isEmpty()) return false

        val format = Format.Builder()
            .setSampleMimeType(sampleMimeType)
            .setCodecs(codecs.ifBlank { null })
            .setWidth(if (width > 0) width else Format.NO_VALUE)
            .setHeight(if (height > 0) height else Format.NO_VALUE)
            .setFrameRate(if (frameRate > 0) frameRate.toFloat() else Format.NO_VALUE.toFloat())
            .build()

        return decoders.any { decoder ->
            runCatching { decoder.isFormatSupported(format) }.getOrDefault(true)
        }
    }
}
