package eu.darken.sdmse.furina.core.bridge

import android.util.Log
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * THE ONLY FILE THAT TOUCHES SD MAID SE INTERNALS.
 * Submits SD Maid's own one-click tasks through its TaskManager. SD Maid's result notification is off
 * (notifyOnFinish = false) because Furina reports in her own voice.
 * Freed space comes from the tasks' affectedSpace; without it, the free-space delta of /data is used.
 */
@Singleton
class SdMaidCleanerGateway @Inject constructor(
    private val taskManager: TaskManager,
) : CleanerGateway {

    private val lock = Mutex()

    override suspend fun clean(targets: Set<CleanTarget>): CleanReport = lock.withLock {
        val freeBefore = StorageProbe.snapshot().freeBytes
        val succeeded = linkedSetOf<CleanTarget>()
        val failed = linkedMapOf<CleanTarget, String>()
        var reportedBytes = 0L
        var anyReported = false

        for (target in CleanTarget.entries.filter { it in targets }) {
            try {
                val task: SDMTool.Task = when (target) {
                    CleanTarget.APP_CACHE -> AppCleanerOneClickTask()
                    CleanTarget.CORPSES -> CorpseFinderOneClickTask()
                    CleanTarget.SYSTEM_JUNK -> SystemCleanerOneClickTask()
                }
                val result = taskManager.submit(task, notifyOnFinish = false)
                (result as? ReportDetails.AffectedSpace)?.let {
                    reportedBytes += it.affectedSpace
                    anyReported = true
                }
                succeeded += target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Clean target $target failed", e)
                failed[target] = e.message?.take(160) ?: e.javaClass.simpleName
            }
        }

        val freeDelta = (StorageProbe.snapshot().freeBytes - freeBefore).coerceAtLeast(0)
        CleanReport(
            freedBytes = if (anyReported) reportedBytes else freeDelta,
            succeeded = succeeded,
            failed = failed,
        )
    }

    private companion object {
        const val TAG = "Furina:Gateway"
    }
}
