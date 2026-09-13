package com.mangotv.app.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the selection half of the black-screen fix: an undecodable video
 * format must never reach ExoPlayer, because the track selector drops it
 * silently and playback continues as audio over a black screen rather than
 * raising any error.
 */
class InAppYouTubeExtractorCodecFilterTest {

    @Test
    fun `undecodable formats are dropped even when they rank highest`() {
        // Only H.264 decodes on this fake device -- the shape of a Fire TV
        // stick with no AV1 decoder.
        val extractor = InAppYouTubeExtractor(onlyCodecPrefix("avc1"))
        val av1FourK = videoCandidate(itag = "401", height = 2160, codecs = "av01.0.12M.08")
        val vp9FourK = videoCandidate(itag = "313", height = 2160, codecs = "vp09.00.50.08")
        val h264HighDef = videoCandidate(itag = "137", height = 1080, codecs = "avc1.640028")

        val decodable = extractor.decodableOnly(listOf(av1FourK, vp9FourK, h264HighDef), "adaptive video")

        assertEquals(listOf("137"), decodable.map { it.itag })
        assertEquals("137", extractor.sortCandidates(decodable).first().itag)
    }

    @Test
    fun `the highest-ranked format still wins when the device can decode it`() {
        val extractor = InAppYouTubeExtractor(allSupported())
        val av1FourK = videoCandidate(itag = "401", height = 2160, codecs = "av01.0.12M.08")
        val h264HighDef = videoCandidate(itag = "137", height = 1080, codecs = "avc1.640028")

        val decodable = extractor.decodableOnly(listOf(h264HighDef, av1FourK), "adaptive video")

        assertEquals(2, decodable.size)
        assertEquals("401", extractor.sortCandidates(decodable).first().itag)
    }

    @Test
    fun `nothing decodable leaves an empty list for the caller to fall back from`() {
        val extractor = InAppYouTubeExtractor(onlyCodecPrefix("hev1"))
        val av1FourK = videoCandidate(itag = "401", height = 2160, codecs = "av01.0.12M.08")

        assertTrue(extractor.decodableOnly(listOf(av1FourK), "adaptive video").isEmpty())
    }

    @Test
    fun `codecs are parsed out of the format's mimeType`() {
        val extractor = InAppYouTubeExtractor(allSupported())

        assertEquals("avc1.640028", extractor.parseCodecs("video/mp4; codecs=\"avc1.640028\""))
        assertEquals("vp09.00.50.08", extractor.parseCodecs("video/webm; codecs=\"vp09.00.50.08\""))
        assertEquals("", extractor.parseCodecs("video/mp4"))
    }

    private fun allSupported() = VideoFormatSupport { _, _, _, _ -> true }

    private fun onlyCodecPrefix(prefix: String) =
        VideoFormatSupport { codecs, _, _, _ -> codecs.startsWith(prefix) }

    private fun videoCandidate(itag: String, height: Int, codecs: String): StreamCandidate {
        return StreamCandidate(
            client = "visionos",
            priority = 0,
            url = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=$itag",
            // Matches videoScore()'s own weighting: resolution dominates, so
            // the 2160p entries outrank the 1080p one before filtering.
            score = height * 1_000_000_000.0 + 30 * 1_000_000.0,
            hasN = false,
            itag = itag,
            height = height,
            fps = 30,
            ext = if (codecs.startsWith("vp09")) "webm" else "mp4",
            width = height * 16 / 9,
            codecs = codecs
        )
    }
}
