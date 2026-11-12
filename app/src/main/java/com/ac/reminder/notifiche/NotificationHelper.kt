package com.ac.reminder.notifiche

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ac.reminder.Formati
import com.ac.reminder.MainActivity
import com.ac.reminder.R
import com.ac.reminder.dati.Avvisi
import com.ac.reminder.dati.Avviso
import com.ac.reminder.dati.Lingua
import com.ac.reminder.dati.Promemoria
import com.ac.reminder.dati.PromemoriaManager
import java.time.LocalDateTime

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

    /**
     * Builds and shows the actual notification. Closed it says the alert ("Tra 30 minuti, alle 16:00");
     * opened up, the reminder's note is underneath.
     */
    fun mostra(context: Context, id: Int, titolo: String, testo: String, nota: String = "") {

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
            .apply {
                if (nota.isEmpty()) setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                else {
                    setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    setCustomBigContentView(conNota(context, titolo, testo, nota))
                }
            }
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

    /**
     * The opened notification of a reminder with a note: title, alert, a gap, the note.
     * The gap can't be written in the text: recent Android versions remove the empty lines from a
     * notification (tried on the phone, also with invisible characters in the line). So the opened
     * part is laid out here, in notifica_con_nota.xml, and the system keeps drawing the frame around
     * it (icon, app name, time, the arrow). Closed, and in the pop-up, the notification is the usual one.
     */
    private fun conNota(context: Context, titolo: String, testo: String, nota: String) =
        RemoteViews(context.packageName, R.layout.notifica_con_nota).apply {
            setTextViewText(R.id.notificaTitolo, titolo)
            setTextViewText(R.id.notificaTesto, testo)
            setTextViewText(R.id.notificaNota, nota)
        }

    /**
     * An alert that never rang, found when the alarms are queued again (the time is the reminder's):
     * - the phone was off: "Avviso perso a telefono spento: oggi 16:00";
     * - the phone was on and Android delivered it late or not at all: "Avviso in ritardo: oggi 16:00"
     *   (under a minute late it's simply shown as itself, "Tra 30 minuti, alle 16:00").
     */
    fun mostraNonSuonato(context: Context, p: Promemoria, avviso: Avviso, spento: Boolean, adesso: LocalDateTime) {
        val quando = Formati.momento(adesso, avviso.volta, conOra = !p.senzaOrario)
        val testo = when {
            spento -> Lingua.testo("Avviso perso a telefono spento: ", "Missed while the phone was off: ") + quando
            Avvisi.inRitardo(avviso, adesso) -> Lingua.testo("Avviso in ritardo: ", "Late alert: ") + quando
            else -> avviso.testo
        }
        mostra(context, PromemoriaManager.idNotifica(p), p.titolo, testo, p.nota)
    }
}
