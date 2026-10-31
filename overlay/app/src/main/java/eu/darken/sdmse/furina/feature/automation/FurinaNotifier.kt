package eu.darken.sdmse.furina.feature.automation

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.furina.core.bridge.CleanReport
import eu.darken.sdmse.furina.core.bridge.StorageSnapshot
import eu.darken.sdmse.furina.core.persona.PersonaFormatter
import eu.darken.sdmse.furina.ui.FurinaHostActivity
import javax.inject.Inject
import javax.inject.Singleton

/** Persona push notifications. Silently does nothing while the notification permission is not granted. */
@Singleton
class FurinaNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val persona: PersonaFormatter,
) {
    fun scheduledCleanFinished(report: CleanReport) {
        if (report.succeeded.isEmpty()) {
            scheduledCleanFailed()
            return
        }
        post(
            id = ID_CLEAN,
            title = context.getString(R.string.furina_notif_title_done),
            text = persona.scheduledDone(report.freedBytes),
        )
    }

    fun scheduledCleanFailed() {
        post(
            id = ID_CLEAN,
            title = context.getString(R.string.furina_notif_title_failed),
            text = persona.say(PersonaFormatter.Event.SCHEDULED_FAILED),
        )
    }

    fun storageLow(snapshot: StorageSnapshot) {
        post(
            id = ID_STORAGE,
            title = context.getString(R.string.furina_notif_title_storage),
            text = persona.storageLow(snapshot),
        )
    }

    @SuppressLint("MissingPermission") // checked in canNotify()
    private fun post(id: Int, title: String, text: String) {
        if (!canNotify()) return
        ensureChannel()

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, FurinaHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_furina_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.furina_notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "furina_clean"
        const val ID_CLEAN = 0xF001
        const val ID_STORAGE = 0xF002
    }
}
