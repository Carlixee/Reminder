package com.ac.reminder.dati

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** An alert to ring: when, what it says under the title, and the reminder occurrence it refers to. */
data class Avviso(val quando: LocalDateTime, val testo: String, val volta: LocalDateTime = quando)

/**
 * Works out WHEN to alert, starting from the alerts chosen for each reminder.
 *
 * For the ones that repeat, not ALL future alerts are scheduled (they would be
 * endless): only the next few. When one rings, the bell
 * (AlarmReceiver) asks to schedule the following ones: a chain.
 */
object Avvisi {

    /** How many alerts to keep scheduled for each reminder. */
    const val DA_PROGRAMMARE = 5

    /**
     * Android accepts at most 500 alarms per app: beyond that, it refuses them. We stay below
     * 400 (safety margin) by giving each one fewer when there are many reminders.
     */
    const val TETTO_SVEGLIE = 400

    /**
     * How many alarms each with `attivi` reminders to ring: 5 usually, fewer
     * if there are many, never fewer than 1. One is enough: when it rings, the bell books the next one.
     */
    fun quantiPerOgnuno(attivi: Int) = (TETTO_SVEGLIE / maxOf(attivi, 1)).coerceIn(1, DA_PROGRAMMARE)

    /** The next alerts that ring AFTER the moment `dopo`, in order. */
    fun prossimi(p: Promemoria, dopo: LocalDateTime, quanti: Int = DA_PROGRAMMARE): List<Avviso> {
        if (p.avvisi.isEmpty()) return emptyList()
        val anticipoMassimo = p.avvisi.maxOf { it.minuti }

        val trovati = mutableListOf<Avviso>()
        var giorno = p.ripetizione.primaVoltaDa(p.data, dopo.toLocalDate())
        var giri = 0
        while (giorno != null && giri < 2000) {
            if (p.fino != null && giorno.isAfter(p.fino)) break   // repetition over
            val volta = giorno.atTime(p.ora)
            // We stop when not even the earliest alert of this occurrence
            // could arrive before the ones already found. (With "2 weeks before"
            // on a daily reminder you need to look 14 occurrences ahead.)
            if (trovati.size >= quanti) {
                val soglia = trovati.sortedBy { it.quando }[quanti - 1].quando
                if (volta.minusMinutes(anticipoMassimo).isAfter(soglia)) break
            }
            for (a in p.avvisi) {
                val quando = volta.minusMinutes(a.minuti)
                if (quando.isAfter(dopo)) trovati += Avviso(quando, testo(a, volta), volta)
            }
            // This occurrence's alerts had already passed when you saved it: it rings at the exact time
            if (p.allOra == volta && volta.isAfter(dopo)) trovati += Avviso(volta, testoAllOra(volta), volta)
            giorno = p.ripetizione.primaVoltaDa(p.data, giorno.plusDays(1))
            giri++
        }
        // Two alerts at the same minute (e.g. "24 hours" and "1 day") become just one
        return trovati.sortedBy { it.quando }.distinctBy { it.quando }.take(quanti)
    }

    /**
     * The phone was off between `da` and `a`: the last alert that had to ring in that time
     * (and couldn't, Android deletes the alarms at shutdown). null = none missed.
     */
    fun perso(p: Promemoria, da: LocalDateTime, a: LocalDateTime): Avviso? =
        if (!p.attivo) null
        else prossimi(p, da, quanti = 500).lastOrNull { !it.quando.isAfter(a) }

    /** The alerts still to ring for ONE specific occurrence (for the card at the top). */
    fun diUnaVolta(p: Promemoria, volta: LocalDateTime, adesso: LocalDateTime): List<LocalDateTime> =
        (p.avvisi.map { volta.minusMinutes(it.minuti) } + listOfNotNull(p.allOra?.takeIf { it == volta }))
            .filter { it.isAfter(adesso) }.distinct().sorted()

    /**
     * If ALL the alerts of the next occurrence have already passed (at 15:50 a reminder
     * at 16:00 with "30 minutes before"), returns that occurrence: it will ring at the exact time.
     * null = not needed (there are alerts still to ring, or no alerts, or it's all past).
     */
    fun daSuonareAllOra(p: Promemoria, adesso: LocalDateTime): LocalDateTime? {
        if (p.avvisi.isEmpty()) return null
        val volta = p.prossimaVolta(adesso) ?: return null
        return volta.takeIf { p.avvisi.none { a -> volta.minusMinutes(a.minuti).isAfter(adesso) } }
    }

    /** "Adesso, alle 16:00" / "Now, at 16:00" (the alert at the exact time) */
    fun testoAllOra(volta: LocalDateTime) = Lingua.testo("Adesso, alle", "Now, at") + " ${volta.toLocalTime()}"

    /** The notification text: "Tra 30 minuti, alle 16:00", "Domani alle 16:00"…
     *  In English: "In 30 minutes, at 16:00", "Tomorrow at 16:00"… */
    fun testo(a: PreAvviso, volta: LocalDateTime): String {
        val ora = volta.toLocalTime()
        val tra = Lingua.testo("Tra", "In") + " ${a.n} ${a.unita.nome(a.n)}"
        val alle = Lingua.testo("alle", "at")
        return when {
            a.unita == Unita.MINUTI || (a.unita == Unita.ORE && a.n != 24) -> "$tra, $alle $ora"
            a.unita == Unita.ORE || (a.unita == Unita.GIORNI && a.n == 1) -> Lingua.testo("Domani", "Tomorrow") + " $alle $ora"
            else -> {
                // The year only if the alert arrives the year before (on 30 December for 2 January)
                val anno = if (volta.year != volta.minusMinutes(a.minuti).year) " ${volta.year}" else ""
                val giorno = Lingua.nome(volta.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Lingua.locale))) + anno
                "$tra: $giorno $alle $ora"
            }
        }
    }
}
