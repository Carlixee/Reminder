package com.ac.reminder.notifiche

import com.ac.reminder.R
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * The notifications' "health check".
 *
 * All the value of this app lies in one thing: that the alarms ring.
 * But the phone can silence them quietly — Samsung putting the app back under
 * battery optimization, Android revoking the permission from rarely used apps,
 * the channel switched off by mistake. The app would look healthy and instead be mute.
 * Here we check everything at every opening.
 */
object DiagnosticaNotifiche {

    data class Esito(
        val problemi: List<String>,
        val notificheDaSistemare: Boolean,
        val batteriaDaSistemare: Boolean,
        val sveglieDaSistemare: Boolean,
        val nonDisturbareDaSistemare: Boolean = false
    ) {
        val tuttoOk: Boolean get() = problemi.isEmpty()
    }

    fun controlla(context: Context): Esito {
        val problemi = mutableListOf<String>()
        var notifiche = false
        var batteria = false

        // 1) Are the app's notifications allowed?
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            problemi.add(context.getString(R.string.salute_p_notifiche))
            notifiche = true
        } else {
            // 2) Has our channel been switched off or downgraded?
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canale = nm.getNotificationChannel(NotificationHelper.CANALE_ID)
            if (canale != null && canale.importance == NotificationManager.IMPORTANCE_NONE) {
                problemi.add(context.getString(R.string.salute_p_canale_spento))
                notifiche = true
            } else if (canale != null && canale.importance < NotificationManager.IMPORTANCE_HIGH) {
                problemi.add(context.getString(R.string.salute_p_canale_basso))
                notifiche = true
            }
        }

        // 3) Power saving can delay or skip the alarms
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            problemi.add(context.getString(R.string.salute_p_batteria))
            batteria = true
        }

        // 4) Android 12 only: exact alarms can be switched off from Settings.
        //    The app doesn't freeze, but alerts can arrive up to an hour later.
        var sveglie = false
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            problemi.add(context.getString(R.string.salute_p_sveglie))
            sveglie = true
        }

        // 5) "Do Not Disturb" on: if Reminder isn't among the allowed apps the reminders arrive
        //    silent, and the green line would lie. (With notifications off point 1 comes first.)
        var nonDisturbare = false
        if (!notifiche) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filtro = nm.currentInterruptionFilter
            val consentita = nm.getNotificationChannel(NotificationHelper.CANALE_ID)?.canBypassDnd() == true
            // Whoever lets "events" through lets us through too (the notifications are of the event type)
            val eventiAmmessi = filtro == NotificationManager.INTERRUPTION_FILTER_PRIORITY &&
                (nm.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS) != 0
            if (filtro > NotificationManager.INTERRUPTION_FILTER_ALL && !consentita && !eventiAmmessi) {
                problemi.add(context.getString(R.string.salute_p_non_disturbare))
                nonDisturbare = true
            }
        }

        return Esito(problemi, notifiche, batteria, sveglie, nonDisturbare)
    }
}
