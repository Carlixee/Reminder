package com.ac.reminder.notifiche

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ac.reminder.MainActivity
import com.ac.reminder.R

/**
 * Everything about SHOWING the notifications is here.
 */
object NotificationHelper {

    const val CANALE_ID = "promemoria"

    /**
     * The "channel" is the category our notifications belong to.
     * IMPORTANCE_HIGH is the key to everything: it makes the drop-down pop-up appear
     * (heads-up) even when you're using the phone. It's the piece that
     * Google Calendar "was missing".
     */
    fun creaCanale(context: Context) {
        val canale = NotificationChannel(
            CANALE_ID,
            context.getString(R.string.canale_nome),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.canale_descrizione)
            enableVibration(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(canale)   // if it already exists, it only updates name and description
    }

    /** Builds and shows the actual notification. */
    fun mostra(context: Context, id: Int, titolo: String, testo: String) {

        // Tapping the notification opens the app
        val apriApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifica = NotificationCompat.Builder(context, CANALE_ID)
            .setSmallIcon(R.drawable.ic_notifica)                         // the timeline dot
            .setColor(ContextCompat.getColor(context, R.color.azzurro_chiaro)) // its colour inside the notification
            .setContentTitle(titolo)
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)   // system sound + vibration
            .setAutoCancel(true)                           // goes away when you tap it
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // readable on the lock screen too
            .setContentIntent(apriApp)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notifica)
    }
}
