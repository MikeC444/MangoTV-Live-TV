package com.mangotv.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class Iso8601Test {

    @Test
    fun `parses against an independently known epoch value`() {
        // 2024-01-01T00:00:00.000Z, verified independently (date -u -d
        // "2024-01-01T00:00:00.000Z" +%s%3N), not just derived from this
        // same parser.
        val millis = Iso8601.parseToEpochMillis("2024-01-01T00:00:00.000Z")
        assertEquals(1704067200000L, millis)
    }

    @Test
    fun `parses date, time, and milliseconds components correctly`() {
        val millis = Iso8601.parseToEpochMillis("2026-03-15T10:30:45.500Z")

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH))
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE))
        assertEquals(45, calendar.get(Calendar.SECOND))
        assertEquals(500, calendar.get(Calendar.MILLISECOND))
    }
}
