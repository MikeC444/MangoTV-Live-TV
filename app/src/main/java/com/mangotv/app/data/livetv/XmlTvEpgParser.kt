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
 */
object XmlTvEpgParser {

    fun parse(
        reader: Reader,
        knownTvgIds: Set<String>,
        minEpochMs: Long,
        maxEpochMs: Long
    ): Map<String, List<Programme>> {
        val result = mutableMapOf<String, MutableList<Programme>>()
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
                        channelAttr = parser.getAttributeValue(null, "channel")
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
        return result.mapValues { (_, list) -> list.sortedBy { it.startEpochMs } }
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
