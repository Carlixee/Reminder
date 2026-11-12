package com.ac.reminder.notifiche

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ac.reminder.dati.PromemoriaManager

/**
 * The "bell": Android calls it when one of our alarms goes off,
 * even if the app is closed. It shows the notification and queues the next alerts.
 * (Whether the screen lights up for it is up to the phone's own settings.)
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        NotificationHelper.mostra(
            context,
            // alarms scheduled by the previous version don't have it: the old number is used
            id = intent.getIntExtra("notifica", id),
            titolo = intent.getStringExtra("titolo") ?: "Promemoria",
            testo = intent.getStringExtra("testo") ?: "",
            nota = intent.getStringExtra("nota") ?: ""
        )

        // The chain: schedules this reminder's next alerts
        // (for the ones that repeat, this is how they're never left without)
        val previsto = intent.getLongExtra("quando", 0L)
        if (previsto > 0L) PromemoriaManager.dopoUnAvviso(context, id, previsto)
    }
}
