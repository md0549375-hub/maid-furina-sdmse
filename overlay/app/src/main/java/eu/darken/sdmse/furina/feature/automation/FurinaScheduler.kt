package eu.darken.sdmse.furina.feature.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.furina.core.data.FurinaSettings
import eu.darken.sdmse.furina.core.data.ScheduleSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recurring silent cleaning on WorkManager. Android decides the exact moment (Doze can delay runs, a daily plan
 * can shift an hour across DST) and runs are skipped while the battery is low.
 */
@Singleton
class FurinaScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: FurinaSettings,
) {
    fun current(): ScheduleSpec? = settings.schedule

    fun schedule(spec: ScheduleSpec) {
        val request = PeriodicWorkRequestBuilder<FurinaCleanWorker>(spec.repeatInterval())
            .setInitialDelay(spec.initialDelay())
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        settings.schedule = spec
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        settings.schedule = null
    }

    companion object {
        const val UNIQUE_NAME = "furina_periodic_clean"
        const val TAG = "furina"
    }
}
