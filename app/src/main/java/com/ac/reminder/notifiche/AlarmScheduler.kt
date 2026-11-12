package com.ac.reminder.notifiche

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Everything about WHEN is here: it schedules (or cancels)
 * the "alarms" that will then make the notifications appear.
 */
object AlarmScheduler {

    /**
     * Schedules an EXACT alarm at a precise time.
     * setExactAndAllowWhileIdle = it goes off at the right minute even if the phone
     * is in deep power saving (Doze). Every alarm has an "id":
     * scheduling the same id again REPLACES the previous alarm
     * (that's what the "overwrite everything" rule relies on).
     */
    fun programmaA(context: Context, quandoMillis: Long, id: Int, notifica: Int, titolo: String, testo: String, nota: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("notifica", notifica)     // the notification's number (tied to the reminder)
            putExtra("titolo", titolo)
            putExtra("testo", testo)
            putExtra("nota", nota)
            putExtra("quando", quandoMillis)   // the bell needs it to schedule the following ones
        }
        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // On Android 12 the exact alarms permission can be removed from Settings:
        // in that case the alarm rings anyway, but it can be up to an hour late (the notification
        // check reports it), instead of making the app close with an error
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quandoMillis, pi)
            else
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quandoMillis, pi)
        } catch (e: IllegalStateException) {
            // Android already has 500 alarms from this app and refuses the others: this one is skipped,
            // but the app doesn't close (before, it closed at launch, while queueing the alarms again)
            Log.w("Reminder", "Sveglia rifiutata da Android: troppe sveglie", e)
        }
    }

    /**
     * Cancels the alarm with that id. NO_CREATE: Android is only asked whether that alarm's
     * "ticket" exists; if it doesn't there's nothing to cancel, and no ticket is created just to throw it away.
     */
    fun cancella(context: Context, id: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
    }
}
