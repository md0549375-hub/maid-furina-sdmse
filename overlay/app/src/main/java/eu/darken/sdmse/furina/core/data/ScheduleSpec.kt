package eu.darken.sdmse.furina.core.data

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** A recurring cleaning plan, persisted as a short string (see [serialize] / [parse]). */
sealed interface ScheduleSpec {

    data class Daily(val hour: Int, val minute: Int) : ScheduleSpec {
        init {
            require(hour in 0..23) { "hour out of range: $hour" }
            require(minute in 0..59) { "minute out of range: $minute" }
        }
    }

    data class Interval(val hours: Int) : ScheduleSpec {
        init {
            require(hours in 1..MAX_INTERVAL_HOURS) { "interval out of range: $hours" }
        }
    }

    fun serialize(): String = when (this) {
        is Daily -> String.format(Locale.ROOT, "daily:%02d:%02d", hour, minute)
        is Interval -> String.format(Locale.ROOT, "interval:%d", hours)
    }

    /** Human readable (Turkish) description used in persona lines. */
    fun describe(): String = when (this) {
        is Daily -> String.format(Locale.ROOT, "her gün %02d:%02d", hour, minute)
        is Interval -> if (hours == 1) "her saat" else String.format(Locale.ROOT, "her %d saatte bir", hours)
    }

    fun initialDelay(now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): Duration = when (this) {
        is Daily -> {
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            Duration.between(now, next)
        }
        is Interval -> Duration.ofHours(hours.toLong())
    }

    fun repeatInterval(): Duration = when (this) {
        is Daily -> Duration.ofDays(1)
        is Interval -> Duration.ofHours(hours.toLong())
    }

    companion object {
        const val MAX_INTERVAL_HOURS = 168

        fun parse(raw: String?): ScheduleSpec? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(':')
            return try {
                when (parts.firstOrNull()) {
                    "daily" -> if (parts.size == 3) Daily(parts[1].toInt(), parts[2].toInt()) else null
                    "interval" -> if (parts.size == 2) Interval(parts[1].toInt()) else null
                    else -> null
                }
            } catch (e: IllegalArgumentException) { // NumberFormatException is a subclass
                null
            }
        }
    }
}
