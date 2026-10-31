package eu.darken.sdmse.furina.core.persona

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.ArrayRes
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.furina.core.bridge.CleanReport
import eu.darken.sdmse.furina.core.bridge.CleanTarget
import eu.darken.sdmse.furina.core.bridge.StorageSnapshot
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Persona String Formatter: every sentence Furina says outside of Gemini comes from here.
 * Wording lives in res/values/furina_persona.xml (string-arrays with positional format args).
 */
@Singleton
class PersonaFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Event(@ArrayRes val lines: Int) {
        GREETING(R.array.furina_greeting),
        CLEAN_DONE(R.array.furina_clean_done),
        CLEAN_NOTHING(R.array.furina_clean_nothing),
        CLEAN_FAILED(R.array.furina_clean_failed),
        CLEAN_PARTIAL(R.array.furina_clean_partial),
        CLEAN_CANCELLED(R.array.furina_clean_cancelled),
        CONFIRM_CLEAN(R.array.furina_confirm_clean),
        STORAGE_STATUS(R.array.furina_storage_status),
        STORAGE_LOW(R.array.furina_storage_low),
        SCHEDULE_SET(R.array.furina_schedule_set),
        SCHEDULE_CANCELLED(R.array.furina_schedule_cancelled),
        SCHEDULE_INVALID(R.array.furina_schedule_invalid),
        SCHEDULED_DONE_NIGHT(R.array.furina_scheduled_done_night),
        SCHEDULED_DONE_DAY(R.array.furina_scheduled_done_day),
        SCHEDULED_FAILED(R.array.furina_scheduled_failed),
        NO_KEY(R.array.furina_no_key),
        KEY_INVALID(R.array.furina_key_invalid),
        OFFLINE_NOTE(R.array.furina_offline_note),
        NOT_UNDERSTOOD(R.array.furina_not_understood),
        ERROR_GENERIC(R.array.furina_error_generic),
    }

    fun say(event: Event, vararg args: Any, random: Random = Random.Default): String {
        val variants = context.resources.getStringArray(event.lines)
        val template = variants[random.nextInt(variants.size)]
        return String.format(Locale.getDefault(), template, *args)
    }

    fun bytes(size: Long): String = Formatter.formatShortFileSize(context, size.coerceAtLeast(0))

    fun targetLabels(targets: Set<CleanTarget>): String =
        CleanTarget.entries.filter { it in targets }.joinToString(", ") { target ->
            context.getString(
                when (target) {
                    CleanTarget.APP_CACHE -> R.string.furina_target_app_cache
                    CleanTarget.CORPSES -> R.string.furina_target_corpses
                    CleanTarget.SYSTEM_JUNK -> R.string.furina_target_system_junk
                }
            )
        }

    fun cleanReport(report: CleanReport): String {
        if (report.succeeded.isEmpty() && report.failed.isNotEmpty()) return say(Event.CLEAN_FAILED)
        val base = if (report.freedBytes > 0) say(Event.CLEAN_DONE, bytes(report.freedBytes)) else say(Event.CLEAN_NOTHING)
        return if (report.failed.isEmpty()) base else base + "\n\n" + say(Event.CLEAN_PARTIAL, targetLabels(report.failed.keys))
    }

    fun storageStatus(snapshot: StorageSnapshot): String =
        say(Event.STORAGE_STATUS, bytes(snapshot.freeBytes), bytes(snapshot.totalBytes), snapshot.usedPercent)

    fun storageLow(snapshot: StorageSnapshot): String =
        say(Event.STORAGE_LOW, bytes(snapshot.freeBytes), snapshot.freePercent)

    /** "Gece sen uyurken…" only at night; during the day the line does not claim you were asleep. */
    fun scheduledDone(freedBytes: Long, hourOfDay: Int = LocalTime.now().hour): String {
        if (freedBytes <= 0) return say(Event.CLEAN_NOTHING)
        val night = hourOfDay >= 22 || hourOfDay < 7
        return say(if (night) Event.SCHEDULED_DONE_NIGHT else Event.SCHEDULED_DONE_DAY, bytes(freedBytes))
    }
}
