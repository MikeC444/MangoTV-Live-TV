package com.mangotv.app.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the one ISO-8601 shape the backend actually emits
 * (`Date.toISOString()`'s fixed `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` format —
 * see server/src/routes/auth.ts's serializeTokens) into epoch
 * milliseconds. Not a general-purpose date library: minSdk 23 predates
 * java.time (needs API 26+ or desugaring, neither set up in this
 * project), and the format here is fixed and fully controlled by our own
 * backend, so a minimal, dependency-free parser is enough — a full
 * date/time library would be solving a more general problem than this
 * app actually has.
 */
object Iso8601 {
    // SimpleDateFormat is not thread-safe; a ThreadLocal gives each
    // coroutine-dispatcher thread its own instance without re-allocating
    // one per call.
    private val formatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun parseToEpochMillis(iso: String): Long = formatter.get()!!.parse(iso)!!.time
}
