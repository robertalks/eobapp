package com.eobapp.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eobapp.app.network.EobApi
import com.eobapp.app.settings.SettingsRepository
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "override_monitor"
private const val NOTIF_ID_STARTED = 1001
private const val NOTIF_ID_ENDED   = 1002
private const val WORK_NAME         = "override_monitor"
private const val WORK_NAME_BOOT    = "override_monitor_boot"

private const val PREF_FILE       = "override_monitor_state"
private const val KEY_INITIALIZED = "initialized"
private const val KEY_ACTIVE      = "override_active"
private const val KEY_TEMP        = "override_temp"

class OverrideMonitorWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(ctx)
        if (!settings.isConfigured()) return Result.success()

        val api = EobApi(
            settings.getHost(),
            settings.getClientId(),
            settings.getClientSecret(),
            settings.getControlToken(),
        )

        val status = runCatching { api.status() }.getOrNull() ?: return Result.retry()

        val override = status.control?.override
        val nowActive = override?.active == true
        val temp = override?.temp

        val prefs = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            // First ever run — seed state without notifying so we don't fire a spurious
            // "Override started" for an override that was already active before the app installed.
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_ACTIVE, nowActive)
                .putString(KEY_TEMP, temp?.toString())
                .apply()
            return Result.success()
        }

        val wasActive = prefs.getBoolean(KEY_ACTIVE, false)

        when {
            !wasActive && nowActive -> {
                val label = if (temp != null) "%.1f °C".format(temp) else "custom temperature"
                notify(NOTIF_ID_STARTED, "Override started", "Heating to $label until the override ends.")
            }
            wasActive && !nowActive -> {
                notify(NOTIF_ID_ENDED, "Override ended", "Returned to the scheduled target temperature.")
            }
        }

        prefs.edit()
            .putBoolean(KEY_ACTIVE, nowActive)
            .putString(KEY_TEMP, temp?.toString())
            .apply()

        return Result.success()
    }

    private fun notify(id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel()

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(id, notif)
    }

    private fun ensureChannel() {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Override monitor", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Notifies when a scheduled temperature override starts or ends." }
        )
    }

    companion object {
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)

            // Run once immediately to seed state (or catch a transition the periodic job missed).
            wm.enqueueUniqueWork(
                WORK_NAME_BOOT,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OverrideMonitorWorker>().build(),
            )

            // Then repeat every 15 minutes (WorkManager's minimum). KEEP so re-launches don't
            // reset the interval timer.
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<OverrideMonitorWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
