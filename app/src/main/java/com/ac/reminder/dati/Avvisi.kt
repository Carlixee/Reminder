package com.ac.reminder.dati

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TreeMap

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
        if (p.avvisi.isEmpty() && !p.senzaOrario) return emptyList()
        val anticipoMassimo = p.avvisi.maxOfOrNull { it.minuti } ?: 0L

        // In order of time, one per minute. Two alerts at the same minute become just one: "24 hours"
        // and "1 day", or "1 week before" of one occurrence and "2 weeks before" of the next. The first
        // one found stays, that is the one of the nearest occurrence (and its text).
        val trovati = TreeMap<LocalDateTime, Avviso>()
        fun aggiungi(quando: LocalDateTime, scritta: () -> String, volta: LocalDateTime) {
            if (quando.isAfter(dopo) && quando !in trovati) trovati[quando] = Avviso(quando, scritta(), volta)
        }
        var giorno = p.primoGiornoDa(dopo.toLocalDate())   // a paused one starts again after the pause
        var giri = 0
        while (giorno != null && giri < 2000) {
            if (p.fino != null && giorno.isAfter(p.fino)) break   // repetition over
            val volta = giorno.atTime(p.ora)
            // We stop when not even the earliest alert of this occurrence
            // could arrive before the ones already found. (With "2 weeks before"
            // on a daily reminder you need to look 14 occurrences ahead.)
            if (trovati.size >= quanti) {
                val soglia = trovati.keys.elementAt(quanti - 1)
                if (volta.minusMinutes(anticipoMassimo).isAfter(soglia)) break
            }
            for (a in p.avvisi) aggiungi(volta.minusMinutes(a.minuti), { testo(a, volta, p.senzaOrario) }, volta)
            if (suonaAllOra(p, volta)) aggiungi(volta, { testoAllOra(volta, p.senzaOrario) }, volta)
            giorno = p.ripetizione.primaVoltaDa(p.data, giorno.plusDays(1))
            giri++
        }
        return trovati.values.take(quanti)
    }

    /**
     * The most recent alert that had to ring after `da` and not later than `a`, and didn't:
     * the phone was off (Android deletes the alarms at shutdown), or Android hadn't delivered it yet.
     * null = none. Only the occurrences that could have an alert in that stretch are looked at.
     */
    fun perso(p: Promemoria, da: LocalDateTime, a: LocalDateTime): Avviso? {
        if (!p.attivo || (p.avvisi.isEmpty() && !p.senzaOrario) || !a.isAfter(da)) return null
        val anticipoMassimo = p.avvisi.maxOfOrNull { it.minuti } ?: 0L
        var ultimo: Avviso? = null
        fun prova(quando: LocalDateTime, scritta: () -> String, volta: LocalDateTime) {
            if (!quando.isAfter(da) || quando.isAfter(a)) return
            if (ultimo?.quando?.isBefore(quando) != false) ultimo = Avviso(quando, scritta(), volta)
        }
        var giorno = p.primoGiornoDa(da.toLocalDate())   // the occurrences skipped by a pause had no alerts
        var giri = 0
        while (giorno != null && giri++ < 2000) {
            if (p.fino != null && giorno.isAfter(p.fino)) break   // repetition over
            val volta = giorno.atTime(p.ora)
            // from here on not even the earliest alert was due yet
            if (volta.minusMinutes(anticipoMassimo).isAfter(a)) break
            for (x in p.avvisi) prova(volta.minusMinutes(x.minuti), { testo(x, volta, p.senzaOrario) }, volta)
            if (suonaAllOra(p, volta)) prova(volta, { testoAllOra(volta, p.senzaOrario) }, volta)
            giorno = p.ripetizione.primaVoltaDa(p.data, giorno.plusDays(1))
        }
        return ultimo
    }

    /**
     * An alert found not rung with the phone on all the time: is it late enough to say so?
     * Under a minute it's shown as itself (Android was just about to deliver it).
     */
    fun inRitardo(avviso: Avviso, adesso: LocalDateTime) = !avviso.quando.plusMinutes(1).isAfter(adesso)

    /**
     * Could alarms of this reminder be queued in Android? Only if it's on and, at the bookmark
     * (`segno`: up to when the alerts are in order), it still had an occurrence to come. One that
     * was already past then has had all its alerts: there's nothing to cancel or to queue again.
     */
    fun puoAvereSveglie(p: Promemoria, segno: LocalDateTime) = p.attivo && p.prossimaVolta(segno) != null

    /**
     * Does it also ring at the time of this occurrence? One without a time always does: that's its
     * alert of the day. The others only when this occurrence's alerts had already passed when you saved it.
     */
    private fun suonaAllOra(p: Promemoria, volta: LocalDateTime) = p.senzaOrario || p.allOra == volta

    /** The alerts still to ring for ONE specific occurrence (for the card at the top). */
    fun diUnaVolta(p: Promemoria, volta: LocalDateTime, adesso: LocalDateTime): List<LocalDateTime> =
        (p.avvisi.map { volta.minusMinutes(it.minuti) } + listOfNotNull(volta.takeIf { suonaAllOra(p, it) }))
            .filter { it.isAfter(adesso) }.distinct().sorted()

    /**
     * If ALL the alerts of the next occurrence have already passed (at 15:50 a reminder
     * at 16:00 with "30 minutes before"), returns that occurrence: it will ring at the exact time.
     * null = not needed (there are alerts still to ring, or no alerts, or it's all past).
     */
    fun daSuonareAllOra(p: Promemoria, adesso: LocalDateTime): LocalDateTime? {
        if (p.avvisi.isEmpty() || p.senzaOrario) return null   // without a time it rings then anyway
        val volta = p.prossimaVolta(adesso) ?: return null
        return volta.takeIf { p.avvisi.none { a -> volta.minusMinutes(a.minuti).isAfter(adesso) } }
    }

    /** "Adesso, alle 16:00" / "Now, at 16:00" (the alert at the exact time). Without a time: "Oggi" / "Today" */
    fun testoAllOra(volta: LocalDateTime, senzaOrario: Boolean = false) =
        if (senzaOrario) Lingua.testo("Oggi", "Today")
        else Lingua.testo("Adesso, alle", "Now, at") + " ${volta.toLocalTime()}"

    /** The notification text: "Tra 30 minuti, alle 16:00", "Domani alle 16:00"…
     *  In English: "In 30 minutes, at 16:00", "Tomorrow at 16:00"…
     *  Without a time (the alerts are whole days before): "Domani", "Tra 3 giorni: sabato 14 novembre". */
    fun testo(a: PreAvviso, volta: LocalDateTime, senzaOrario: Boolean = false): String {
        val tra = Lingua.testo("Tra", "In") + " ${a.n} ${a.unita.nome(a.n)}"
        val alleOra = if (senzaOrario) "" else " " + Lingua.testo("alle", "at") + " ${volta.toLocalTime()}"
        return when {
            // "24 hours" and "1 day"
            a.minuti == Unita.GIORNI.minuti.toLong() -> Lingua.testo("Domani", "Tomorrow") + alleOra
            !senzaOrario && (a.unita == Unita.MINUTI || a.unita == Unita.ORE) -> "$tra,$alleOra"
            else -> {
                // The year only if the alert arrives the year before (on 30 December for 2 January)
                val anno = if (volta.year != volta.minusMinutes(a.minuti).year) " ${volta.year}" else ""
                val giorno = Lingua.nome(volta.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Lingua.locale))) + anno
                "$tra: $giorno$alleOra"
            }
        }
    }
}
