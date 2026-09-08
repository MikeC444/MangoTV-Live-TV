package com.mangotv.app.data.livetv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Streaming XMLTV parser: reads `<programme channel="" start="" stop="">
 * <title>` entries one at a time via a pull parser rather than building a
 * DOM, and immediately discards anything outside [minEpochMs, maxEpochMs]
 * or for a channel id this app doesn't actually have loaded
 * ([knownTvgIds]) -- a multi-hundred-channel global guide never fully
 * materializes in memory this way, which matters on a low-RAM Fire TV
 * Stick. Any malformed entry (bad timestamp, missing attribute) is skipped
 * rather than aborting the whole guide.
 *
 * `channel` attribute values carry their own "@variant" suffix here (e.g.
 * confirmed against the live default guide: `channel="AlJazeera.qa@English"`
 * -- a language tag, not the "@SD"/"@HD" quality tag this app's own
 * playlist-derived tvg-ids carry for the same base channel). The two
 * sides' suffixes don't agree with each other at all, so both are
 * stripped down to the shared base id ("AlJazeera.qa") before matching --
 * see [knownTvgIds], which Channel.epgChannelId already normalizes the
 * same way.
 */
/**
 * [programmesByChannel] is the actual usable result; [distinctChannelIdCount]
 * and [sampleChannelIds] exist purely for on-device diagnostics (see
 * EpgDiagnostics) -- they record every channel id the guide mentions at all,
 * regardless of whether it matched [XmlTvEpgParser.parse]'s knownTvgIds, so a
 * near-total id-scheme mismatch between this app's playlist and a given EPG
 * source shows up as real strings to compare rather than a bare zero.
 */
data class EpgParseResult(
    val programmesByChannel: Map<String, List<Programme>>,
    val distinctChannelIdCount: Int,
    val sampleChannelIds: List<String>
)

object XmlTvEpgParser {

    private const val CHANNEL_ID_SAMPLE_SIZE = 12

    fun parse(
        reader: Reader,
        knownTvgIds: Set<String>,
        minEpochMs: Long,
        maxEpochMs: Long
    ): EpgParseResult {
        val result = mutableMapOf<String, MutableList<Programme>>()
        val seenChannelIds = LinkedHashSet<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(reader)

        var channelAttr: String? = null
        var startMs: Long? = null
        var stopMs: Long? = null
        var title: String? = null
        var inTitle = false
        var inProgramme = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        inProgramme = true
                        channelAttr = parser.getAttributeValue(null, "channel")?.substringBefore('@')
                        channelAttr?.let { seenChannelIds.add(it) }
                        startMs = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                        stopMs = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                        title = null
                    }
                    "title" -> if (inProgramme) inTitle = true
                }
                XmlPullParser.TEXT -> if (inTitle) {
                    title = (title.orEmpty() + parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> inTitle = false
                    "programme" -> {
                        inProgramme = false
                        val channel = channelAttr
                        val start = startMs
                        val stop = stopMs
                        if (channel != null && channel in knownTvgIds && start != null && stop != null &&
                            stop >= minEpochMs && start <= maxEpochMs
                        ) {
                            result.getOrPut(channel) { mutableListOf() } += Programme(
                                title = title?.trim()?.takeIf { it.isNotBlank() } ?: "Programme",
                                startEpochMs = start,
                                stopEpochMs = stop
                            )
                        }
                    }
                }
            }
            eventType = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
        }
        return EpgParseResult(
            programmesByChannel = result.mapValues { (_, list) -> list.sortedBy { it.startEpochMs } },
            distinctChannelIdCount = seenChannelIds.size,
            sampleChannelIds = seenChannelIds.take(CHANNEL_ID_SAMPLE_SIZE)
        )
    }

    // XMLTV timestamps look like "20240115193000 +0000".
    private val TIME_FORMAT = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    private fun parseXmlTvTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching { TIME_FORMAT.get()!!.parse(raw.trim())?.time }.getOrNull()
    }
}
