package com.ac.reminder.notifiche

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ac.reminder.dati.PromemoriaManager

/**
 * Schedules all the alarms again by reading the saved reminders. Needed when:
 * - the phone is switched on again (Android DELETES them all) or the app is updated;
 * - on Android 12 the exact alarms are switched back on;
 * - the phone's time zone or time changes.
 * Without it: total silence.
 *
 * Queueing them again also reports the alerts that never rang (see PromemoriaManager.recupera):
 * at switch-on they're the ones that fell while the phone was off.
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
            PromemoriaManager.riprogrammaTutti(context, accensione = intent.action == Intent.ACTION_BOOT_COMPLETED)
        }
    }
}
