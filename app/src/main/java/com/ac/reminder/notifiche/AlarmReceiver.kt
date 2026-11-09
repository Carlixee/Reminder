package com.ac.reminder.notifiche

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.ac.reminder.dati.PromemoriaManager

/**
 * The "bell": Android calls it when one of our alarms goes off,
 * even if the app is closed. It shows the notification and, if needed, turns the screen on.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        NotificationHelper.mostra(
            context,
            // alarms scheduled by the previous version don't have it: the old number is used
            id = intent.getIntExtra("notifica", id),
            titolo = intent.getStringExtra("titolo") ?: "Promemoria",
            testo = intent.getStringExtra("testo") ?: ""
        )

        // If the screen was OFF, we turn it on for a few seconds
        // (as the Messages app does). If it was already on we do nothing.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            @Suppress("DEPRECATION")   // "old style" method but it's the simple way and it works
            val luce = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE,
                "Reminder:accendiSchermo"
            )
            luce.acquire(5_000L)   // 5 seconds, then the phone goes back to managing itself
        }

        // The chain: schedules this reminder's next alerts
        // (for the ones that repeat, this is how they're never left without)
        val previsto = intent.getLongExtra("quando", 0L)
        if (previsto > 0L) PromemoriaManager.dopoUnAvviso(context, id, previsto)
    }
}
