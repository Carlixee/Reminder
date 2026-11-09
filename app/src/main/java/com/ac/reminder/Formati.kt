package com.ac.reminder

import android.content.Context
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.ac.reminder.dati.Avvisi
import com.ac.reminder.dati.Lingua
import com.ac.reminder.dati.Promemoria
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How dates and times are written in the whole app, in one place only:
 * list, blue card, edit sheet and tile all say things the same way.
 *
 * The language is decided by Lingua (Italian or English). Android writes the names of days
 * and months with a capital letter ("Gio", "Novembre"): in Italian here everything always goes through
 * minuscolo() and, where needed, only the first letter is raised again. In English they stay
 * capitalised, as they're written there ("Thu", "November").
 */
object Formati {

    private val lingua: Locale get() = Lingua.locale
    private fun formato(schema: String) = DateTimeFormatter.ofPattern(schema, lingua)

    private fun minuscolo(s: String) = Lingua.nome(s).replace(".", "")
    fun maiuscola(s: String) = s.replaceFirstChar { it.titlecase(lingua) }

    /** "16:00" */
    fun ora(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    /** "MAR" / "TUE" (the dates column in the list) */
    fun giornoCorto(d: LocalDate) =
        d.dayOfWeek.getDisplayName(TextStyle.SHORT, lingua).uppercase(lingua).take(3)

    /** "venerdì" / "Friday" */
    fun nomeGiorno(d: LocalDate) = minuscolo(d.dayOfWeek.getDisplayName(TextStyle.FULL, lingua))

    /** "10/11" */
    fun dataBreve(d: LocalDate): String = d.format(formato("dd/MM"))

    /** " 2027" if it isn't the current year; this year isn't written */
    private fun anno(d: LocalDate) = if (d.year == LocalDate.now().year) "" else " ${d.year}"

    /** "martedì 10 novembre" (another year: "martedì 10 novembre 2027") */
    fun giornoLungo(d: LocalDate) = minuscolo(d.format(formato("EEEE d MMMM"))) + anno(d)

    /** "Martedì 10 novembre · 16:00" (the blue card) */
    fun quandoLungo(q: LocalDateTime) = maiuscola(giornoLungo(q.toLocalDate())) + " · " + ora(q.toLocalTime())

    /** "martedì 10 novembre alle 16:00" / "Tuesday 10 November at 16:00" */
    fun giornoEOra(c: Context, q: LocalDateTime) =
        c.getString(R.string.giorno_e_ora, giornoLungo(q.toLocalDate()), ora(q.toLocalTime()))

    /**
     * "gio 12 nov" (the date box in the sheet). Another year: "4 gen 2027",
     * without the day of the week, so it fits on one line like the time box.
     */
    fun dataCasella(d: LocalDate) =
        if (anno(d).isEmpty()) minuscolo(d.format(formato("EEE d MMM")))
        else minuscolo(d.format(formato("d MMM"))) + anno(d)

    /** "tra 5 minuti", "tra 3 ore", "domani", "tra 3 giorni" / "in 5 minutes"… */
    fun traQuanto(c: Context, adesso: LocalDateTime, quando: LocalDateTime): String {
        val minuti = Duration.between(adesso, quando).toMinutes().toInt()
        val giorni = ChronoUnit.DAYS.between(adesso.toLocalDate(), quando.toLocalDate()).toInt()
        val r = c.resources
        return when {
            minuti < 1 -> c.getString(R.string.adesso)
            minuti < 60 -> r.getQuantityString(R.plurals.tra_minuti, minuti, minuti)
            giorni == 0 -> (minuti / 60).let { r.getQuantityString(R.plurals.tra_ore, it, it) }
            giorni == 1 -> c.getString(R.string.domani)
            else -> r.getQuantityString(R.plurals.tra_giorni, giorni, giorni)
        }
    }

    /** An alert's moment, said briefly: "oggi 15:30", "domani 16:00", "lunedì 20:00", "sab 14/11 20:00" */
    fun momento(adesso: LocalDateTime, q: LocalDateTime): String {
        val giorni = ChronoUnit.DAYS.between(adesso.toLocalDate(), q.toLocalDate())
        val o = ora(q.toLocalTime())
        val d = q.toLocalDate()
        return when (giorni) {
            0L -> Lingua.testo("oggi", "today") + " $o"
            1L -> Lingua.testo("domani", "tomorrow") + " $o"
            in 2L..6L -> "${nomeGiorno(d)} $o"
            else -> "${minuscolo(d.dayOfWeek.getDisplayName(TextStyle.SHORT, lingua))} ${dataBreve(d)} $o"
        }
    }

    /** The line with the bell in the blue card: "Avvisi: lunedì 16:00 · martedì 15:30" */
    fun rigaAvvisi(c: Context, p: Promemoria, volta: LocalDateTime, adesso: LocalDateTime): String = when {
        !p.attivo -> c.getString(R.string.avvisi_spenti)
        p.avvisi.isEmpty() -> c.getString(R.string.avvisi_nessuno_riga)
        else -> {
            val prossimi = Avvisi.diUnaVolta(p, volta, adesso)
            if (prossimi.isEmpty()) c.getString(R.string.avvisi_finiti)
            else c.getString(R.string.avvisi_riga, prossimi.joinToString(" · ") { momento(adesso, it) })
        }
    }

    /** "ogni settimana: sab" (lowercase: it goes at the end of other text) */
    fun ripetizioneBreve(p: Promemoria) = p.descrizioneRipetizione().replaceFirstChar { it.lowercase(lingua) }

    /** "Fino al 30 nov", "Fino all'8 dic" (the segment in the sheet); "Until 30 Nov" */
    fun finoAl(d: LocalDate) =
        Lingua.testo("Fino " + Promemoria.al(d.dayOfMonth), "Until ") + minuscolo(d.format(formato("d MMM"))) + anno(d)
}

/**
 * The "Delete" button of the confirmation dialogs, in red: you understand at a glance
 * that it's an action with no way back. It must be called AFTER show(): before, the button doesn't exist.
 */
fun AlertDialog.eliminaInRosso(): AlertDialog = apply {
    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(context, R.color.rosso))
}

/**
 * The dialog's title wraps as much as needed. By itself Android stops it at two
 * lines with dots ("Vuoi eliminare questo prome…"), which with large text
 * aren't enough. This one too must be called AFTER show().
 */
fun AlertDialog.titoloIntero(): AlertDialog = apply {
    findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
        setSingleLine(false)
        maxLines = 6
    }
}
