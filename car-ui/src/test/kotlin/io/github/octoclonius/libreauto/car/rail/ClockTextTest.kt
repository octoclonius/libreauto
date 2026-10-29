package io.github.octoclonius.libreauto.car.rail

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClockTextTest {

    private val evening = Date(19L * 60 * 60 * 1000 + 34L * 60 * 1000)

    @Test
    fun aTwelveHourClockDropsAmPm() {
        assertEquals("7:34", clockText(format("h:mm a", Locale.US), evening))
        assertEquals("7:34", clockText(format("h:mm\u202Fa", Locale.US), evening))
    }

    @Test
    fun aDayPeriodBeforeTheTimeIsDroppedToo() {
        assertEquals("7:34", clockText(format("a h:mm", Locale.KOREA), evening))
        assertEquals("7:34", clockText(format("ah:mm", Locale.CHINA), evening))
    }

    @Test
    fun aTwentyFourHourClockIsUnchanged() {
        assertEquals("19:34", clockText(format("HH:mm", Locale.US), evening))
        assertEquals("19.34", clockText(format("HH.mm", Locale.forLanguageTag("fi")), evening))
    }

    @Test
    fun quotedTextStays() {
        assertEquals("19 h 34", clockText(format("HH 'h' mm", Locale.FRANCE), evening))
    }

    private fun format(pattern: String, locale: Locale) =
        SimpleDateFormat(pattern, locale).apply { timeZone = TimeZone.getTimeZone("UTC") }
}
