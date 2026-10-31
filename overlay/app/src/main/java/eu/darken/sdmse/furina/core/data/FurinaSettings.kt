package eu.darken.sdmse.furina.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.furina.core.bridge.CleanTarget
import javax.inject.Inject
import javax.inject.Singleton

/** Non-secret Furina preferences. The API key lives in [SecretStore], never here. */
@Singleton
class FurinaSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("furina_settings", Context.MODE_PRIVATE)

    var model: String
        get() = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(KEY_MODEL, value.trim()).apply()
        }

    var schedule: ScheduleSpec?
        get() = ScheduleSpec.parse(prefs.getString(KEY_SCHEDULE, null))
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_SCHEDULE) else putString(KEY_SCHEDULE, value.serialize())
            }.apply()
        }

    /** What the silent background run cleans. Defaults to caches only. */
    var scheduledTargets: Set<CleanTarget>
        get() {
            val stored = prefs.getString(KEY_SCHEDULED_TARGETS, null)
                ?.split(',')
                ?.mapNotNull { name -> CleanTarget.entries.firstOrNull { it.name == name } }
                ?.toSet()
            return if (stored.isNullOrEmpty()) setOf(CleanTarget.APP_CACHE) else stored
        }
        set(value) {
            prefs.edit().putString(KEY_SCHEDULED_TARGETS, value.joinToString(",") { it.name }).apply()
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        private const val KEY_MODEL = "model"
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_SCHEDULED_TARGETS = "scheduled_targets"
    }
}
