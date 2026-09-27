package com.lactose.textme.core.common

object TimeUtils {

    /**
     * Formats a millisecond timestamp into a human-readable relative time string:
     * - "just now" (< 1 min)
     * - "... minutes ago" (1..59 mins)
     * - "... hours ago" (1..23 hours)
     * - "last day" (1 day ago)
     * - "... days ago" (2..6 days ago)
     * - "last week" (7..13 days ago)
     * - "... weeks ago" (2..3 weeks ago)
     * - "last month" (30..59 days ago)
     * - "... months ago" (2..11 months ago)
     * - "last year" (1 year ago)
     * - "... years ago" (2+ years ago)
     */
    fun formatRelativeTime(timestampMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diffMs = now - timestampMillis
        if (diffMs < 0L) return "just now"

        val seconds = diffMs / 1000L
        if (seconds < 60L) {
            return "just now"
        }

        val minutes = seconds / 60L
        if (minutes < 60L) {
            return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        }

        val hours = minutes / 60L
        if (hours < 24L) {
            return if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }

        val days = hours / 24L
        if (days == 1L) {
            return "last day"
        }
        if (days < 7L) {
            return "$days days ago"
        }

        val weeks = days / 7L
        if (weeks == 1L) {
            return "last week"
        }
        if (weeks < 4L) {
            return "$weeks weeks ago"
        }

        val months = days / 30L
        if (months == 1L) {
            return "last month"
        }
        if (months < 12L) {
            return "$months months ago"
        }

        val years = days / 365L
        if (years == 1L) {
            return "last year"
        }
        return "$years years ago"
    }
}
