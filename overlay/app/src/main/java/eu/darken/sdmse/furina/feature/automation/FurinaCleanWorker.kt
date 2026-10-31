package eu.darken.sdmse.furina.feature.automation

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.furina.core.bridge.CleanerGateway
import eu.darken.sdmse.furina.core.bridge.StorageProbe
import eu.darken.sdmse.furina.core.data.FurinaSettings
import kotlinx.coroutines.CancellationException

/** Silent scheduled clean + persona notification. */
@HiltWorker
class FurinaCleanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val gateway: CleanerGateway,
    private val notifier: FurinaNotifier,
    private val settings: FurinaSettings,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val report = gateway.clean(settings.scheduledTargets)
        notifier.scheduledCleanFinished(report)

        val storage = StorageProbe.snapshot()
        if (storage.freePercent < LOW_STORAGE_PERCENT) notifier.storageLow(storage)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Scheduled clean failed (attempt ${runAttemptCount + 1})", e)
        if (runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            notifier.scheduledCleanFailed()
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "Furina:Worker"
        const val MAX_RETRIES = 2
        const val LOW_STORAGE_PERCENT = 10
    }
}
