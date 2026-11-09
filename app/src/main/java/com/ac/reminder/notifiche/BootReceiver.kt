package com.ac.reminder.notifiche

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ac.reminder.Formati
import com.ac.reminder.dati.Lingua
import com.ac.reminder.dati.PromemoriaManager
import java.time.LocalDateTime

/**
 * Schedules all the alarms again by reading the saved reminders. Needed when:
 * - the phone is switched on again or the app is updated (Android DELETES them all);
 * - on Android 12 the exact alarms are switched back on;
 * - the phone's time zone or time changes.
 * Without it: total silence.
 *
 * At switch-on, BEFORE queueing them again, it reports the ones that fell while the phone was off.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            // Android 12: the exact alarms were switched back on from Settings
            intent.action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ||
            // Change of time zone (a trip) or of time: "10:00" stays 10:00 of the place where you are
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED
        ) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) avvisaPersi(context)
            PromemoriaManager.riprogrammaTutti(context)
        }
    }

    /** "Ethical – Missed while the phone was off: today 16:00" (the time is the reminder's) */
    private fun avvisaPersi(context: Context) {
        val adesso = LocalDateTime.now()
        for ((p, avviso) in PromemoriaManager.persiASpento(context, adesso)) {
            val testo = Lingua.testo("Avviso perso a telefono spento: ", "Missed while the phone was off: ") +
                Formati.momento(adesso, avviso.volta)
            NotificationHelper.mostra(context, PromemoriaManager.idNotifica(p), p.titolo, testo)
        }
    }
}
