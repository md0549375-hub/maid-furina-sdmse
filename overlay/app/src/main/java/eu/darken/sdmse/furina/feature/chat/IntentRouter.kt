package eu.darken.sdmse.furina.feature.chat

import eu.darken.sdmse.furina.core.bridge.CleanTarget
import eu.darken.sdmse.furina.core.data.ScheduleSpec
import java.util.Locale

/**
 * Regex based intent analysis (Turkish) used when Gemini is unavailable and by the settings screen to parse
 * "her gece 03:00" style schedules. Input is normalised first (lower-case, Turkish letters folded to ASCII).
 */
object IntentRouter {

    sealed interface Intent {
        data object StorageStatus : Intent
        data class Clean(val targets: Set<CleanTarget>) : Intent
        data class Schedule(val spec: ScheduleSpec) : Intent
        data object CancelSchedule : Intent
    }

    private val CANCEL = Regex(
        """(iptal|durdur|kaldir|kapat).*(zamanl|program|otomatik|gorev)|(zamanlama|otomatik\s+temiz|programi?).*(iptal|durdur|kaldir|kapat)"""
    )
    private val CLEAN = Regex("""\b(temizl|bosalt|arindir|optimiz)|\b(?:sil|silsene|silebilir\w*|silelim)\b""")
    private val STORAGE = Regex(
        """(depolama|hafiza|alan|disk|storage|bellek).*(durum|kac|ne\s+kadar|bos|dolu|ne\b)|(durum|kac|ne\s+kadar).*(depolama|hafiza|alan)"""
    )
    private val TARGET_CACHE = Regex("""gecici|onbellek|cache|tmp|temp""")
    private val TARGET_CORPSE = Regex("""artik|kalinti|corpse|silinmis\s+uygulama|kaldirilmis""")
    private val INTERVAL = Regex("""her\s+(\d{1,3})\s*saat(?:te)?\s*bir""")
    private val DAILY = Regex("""her\s*(gece|gun|sabah|aksam|ogle)|gunluk""")
    private val CLOCK = Regex("""(\d{1,2})[:.](\d{2})""")

    private const val DEFAULT_NIGHT_HOUR = 3

    fun route(raw: String): Intent? {
        val text = normalize(raw)
        if (text.isBlank()) return null

        if (CANCEL.containsMatchIn(text)) return Intent.CancelSchedule

        parseNormalizedSchedule(text)?.let { spec ->
            if (CLEAN.containsMatchIn(text)) return Intent.Schedule(spec)
        }
        if (CLEAN.containsMatchIn(text)) return Intent.Clean(targetsFor(text))
        if (STORAGE.containsMatchIn(text)) return Intent.StorageStatus
        return null
    }

    fun parseSchedule(raw: String): ScheduleSpec? = parseNormalizedSchedule(normalize(raw))

    private fun parseNormalizedSchedule(text: String): ScheduleSpec? {
        INTERVAL.find(text)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return null
            return if (hours in 1..ScheduleSpec.MAX_INTERVAL_HOURS) ScheduleSpec.Interval(hours) else null
        }
        if (!DAILY.containsMatchIn(text)) return null

        val clock = CLOCK.find(text)
        if (clock == null) return ScheduleSpec.Daily(DEFAULT_NIGHT_HOUR, 0)
        val hour = clock.groupValues[1].toIntOrNull() ?: return null
        val minute = clock.groupValues[2].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) ScheduleSpec.Daily(hour, minute) else null
    }

    private fun targetsFor(text: String): Set<CleanTarget> {
        val targets = linkedSetOf<CleanTarget>()
        if (TARGET_CACHE.containsMatchIn(text)) {
            targets += CleanTarget.APP_CACHE
            targets += CleanTarget.SYSTEM_JUNK
        }
        if (TARGET_CORPSE.containsMatchIn(text)) targets += CleanTarget.CORPSES
        return if (targets.isEmpty()) CleanTarget.entries.toSet() else targets
    }

    internal fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.forLanguageTag("tr"))
        return buildString(lower.length) {
            for (c in lower) {
                append(
                    when (c) {
                        'ı' -> 'i'
                        'ş' -> 's'
                        'ğ' -> 'g'
                        'ü' -> 'u'
                        'ö' -> 'o'
                        'ç' -> 'c'
                        'â' -> 'a'
                        else -> c
                    }
                )
            }
        }.trim()
    }
}
