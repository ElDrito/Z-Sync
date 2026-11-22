package app.zsync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*

// An inexact idle-allowed alarm wakes the app for the next due task; periodic WorkManager is the fallback.
object AutoScheduler {
    private const val ONCE = "auto-once"
    private const val MIN_DELAY_MS = 60_000L

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AutoAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun sync(ctx: Context, minDelayMs: Long = MIN_DELAY_MS) {
        val app = ctx.applicationContext as App
        val alarms = app.getSystemService(AlarmManager::class.java)

        if (app.repo.accounts.value.isEmpty()) {
            alarms.cancel(pending(app))
            return
        }

        val next = app.engine.nextWakeAt()
        val at = maxOf(next, System.currentTimeMillis() + maxOf(minDelayMs, MIN_DELAY_MS))
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app)) }
    }

    fun enqueueRun(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<AutoWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, req)
    }
}

class AutoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AutoScheduler.enqueueRun(context)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AutoScheduler.sync(context)
}
