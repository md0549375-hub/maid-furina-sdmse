package eu.darken.sdmse.furina.core.bridge

import android.os.Environment
import android.os.StatFs

/** The cleaning capabilities Furina can ask for; maps onto SD Maid SE's tools. */
enum class CleanTarget {
    APP_CACHE,   // AppCleaner
    CORPSES,     // CorpseFinder
    SYSTEM_JUNK, // SystemCleaner
}

data class StorageSnapshot(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedPercent: Int get() = if (totalBytes <= 0) 0 else ((usedBytes * 100) / totalBytes).toInt()
    val freePercent: Int get() = 100 - usedPercent
}

data class CleanReport(
    val freedBytes: Long,
    val succeeded: Set<CleanTarget>,
    val failed: Map<CleanTarget, String>,
)

interface CleanerGateway {
    /** Runs the requested SD Maid tools sequentially; never overlaps another running clean. */
    suspend fun clean(targets: Set<CleanTarget>): CleanReport
}

object StorageProbe {
    fun snapshot(): StorageSnapshot {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageSnapshot(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }
}
