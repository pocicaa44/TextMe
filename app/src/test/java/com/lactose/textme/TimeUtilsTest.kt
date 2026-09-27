package com.lactose.textme

import com.lactose.textme.core.common.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    private val baseNow = 1_700_000_000_000L

    @Test
    fun testJustNow() {
        assertEquals("just now", TimeUtils.formatRelativeTime(baseNow - 10_000L, baseNow))
        assertEquals("just now", TimeUtils.formatRelativeTime(baseNow - 59_000L, baseNow))
        assertEquals("just now", TimeUtils.formatRelativeTime(baseNow + 5_000L, baseNow))
    }

    @Test
    fun testMinutesAgo() {
        assertEquals("1 minute ago", TimeUtils.formatRelativeTime(baseNow - 65_000L, baseNow))
        assertEquals("5 minutes ago", TimeUtils.formatRelativeTime(baseNow - 300_000L, baseNow))
        assertEquals("59 minutes ago", TimeUtils.formatRelativeTime(baseNow - 59 * 60_000L, baseNow))
    }

    @Test
    fun testHoursAgo() {
        assertEquals("1 hour ago", TimeUtils.formatRelativeTime(baseNow - 65 * 60_000L, baseNow))
        assertEquals("5 hours ago", TimeUtils.formatRelativeTime(baseNow - 5 * 3600_000L, baseNow))
        assertEquals("23 hours ago", TimeUtils.formatRelativeTime(baseNow - 23 * 3600_000L, baseNow))
    }

    @Test
    fun testDaysAgo() {
        assertEquals("last day", TimeUtils.formatRelativeTime(baseNow - 25 * 3600_000L, baseNow))
        assertEquals("2 days ago", TimeUtils.formatRelativeTime(baseNow - 49 * 3600_000L, baseNow))
        assertEquals("6 days ago", TimeUtils.formatRelativeTime(baseNow - 6 * 86400_000L, baseNow))
    }

    @Test
    fun testWeeksAgo() {
        assertEquals("last week", TimeUtils.formatRelativeTime(baseNow - 7 * 86400_000L, baseNow))
        assertEquals("last week", TimeUtils.formatRelativeTime(baseNow - 12 * 86400_000L, baseNow))
        assertEquals("2 weeks ago", TimeUtils.formatRelativeTime(baseNow - 14 * 86400_000L, baseNow))
        assertEquals("3 weeks ago", TimeUtils.formatRelativeTime(baseNow - 25 * 86400_000L, baseNow))
    }

    @Test
    fun testMonthsAgo() {
        assertEquals("last month", TimeUtils.formatRelativeTime(baseNow - 35 * 86400_000L, baseNow))
        assertEquals("2 months ago", TimeUtils.formatRelativeTime(baseNow - 65 * 86400_000L, baseNow))
        assertEquals("11 months ago", TimeUtils.formatRelativeTime(baseNow - 330 * 86400_000L, baseNow))
    }

    @Test
    fun testYearsAgo() {
        assertEquals("last year", TimeUtils.formatRelativeTime(baseNow - 370 * 86400_000L, baseNow))
        assertEquals("2 years ago", TimeUtils.formatRelativeTime(baseNow - 750 * 86400_000L, baseNow))
    }
}
