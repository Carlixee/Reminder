package com.ac.reminder.dati

import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * Thousands of reminders made at random (repetitions, end dates, pauses, no time, several alerts)
 * and, for each, the quick calculation the app uses compared with a slow and obvious one:
 * every occurrence written out one by one, every alert of every occurrence, then sorted.
 * The numbers are always the same ones (fixed seed): a failure here can be run again as it is.
 */
class ForzaBrutaTest {

    private val caso = Random(20261112)

    private fun ripetizione(): Ripetizione = when (caso.nextInt(6)) {
        0 -> Ripetizione.Nessuna
        1 -> Ripetizione.OgniGiorni(caso.nextInt(1, 11))
        2 -> Ripetizione.OgniSettimane(
            caso.nextInt(1, 5),
            DayOfWeek.values().filter { caso.nextInt(3) == 0 }.toSet().ifEmpty { setOf(DayOfWeek.of(caso.nextInt(1, 8))) }
        )
        3 -> Ripetizione.OgniMesi(caso.nextInt(1, 13), if (caso.nextBoolean()) null else caso.nextInt(1, 32))
        4 -> Ripetizione.OgniMesiSettimana(caso.nextInt(1, 7), listOf(1, 2, 3, 4, -1).random(caso), DayOfWeek.of(caso.nextInt(1, 8)))
        else -> Ripetizione.OgniGiorni(1)
    }

    private fun promemoria(): Promemoria {
        val data = LocalDate.of(2026, 1, 1).plusDays(caso.nextLong(0, 700))
        val ripetizione = ripetizione()
        val senzaOrario = caso.nextInt(3) == 0
        val avvisi = PreAvviso.ordina(List(caso.nextInt(0, 5)) {
            val u = Unita.values()[caso.nextInt(4)]
            PreAvviso(caso.nextInt(1, u.massimo + 1), u)
        }).filter { !senzaOrario || it.giorniInteri }
        val base = Promemoria(
            data, LocalTime.of(caso.nextInt(24), caso.nextInt(60)), "x",
            ripetizione = ripetizione, avvisi = avvisi,
            fino = if (ripetizione !is Ripetizione.Nessuna && caso.nextInt(3) == 0) data.plusDays(caso.nextLong(0, 500)) else null,
            senzaOrario = senzaOrario,
            pausa = if (caso.nextInt(3) == 0) data.plusDays(caso.nextLong(-30, 400)) else null
        )
        // sometimes it also rings at the exact time of one real occurrence
        val allOra = if (!senzaOrario && avvisi.isNotEmpty() && caso.nextInt(4) == 0)
            base.prossimaVolta(data.atStartOfDay().plusDays(caso.nextLong(0, 200))) else null
        return base.copy(allOra = allOra)
    }

    private fun momento(p: Promemoria): LocalDateTime =
        p.data.atStartOfDay().plusMinutes(caso.nextLong(-60L * 24 * 40, 60L * 24 * 600))

    /** Every occurrence from `da` (after the pause) up to `finoA`, one by one. */
    private fun volte(p: Promemoria, da: LocalDate, finoA: LocalDate): List<LocalDateTime> {
        val tutte = mutableListOf<LocalDateTime>()
        var giorno = p.primoGiornoDa(da)
        while (giorno != null && !giorno.isAfter(finoA) && (p.fino == null || !giorno.isAfter(p.fino)) && tutte.size < 6000) {
            tutte += giorno.atTime(p.ora)
            giorno = p.ripetizione.primaVoltaDa(p.data, giorno.plusDays(1))
        }
        return tutte
    }

    /** Every alert of those occurrences: the ones before, and the one at the time itself when it's due. */
    private fun avvisi(p: Promemoria, volte: List<LocalDateTime>): List<LocalDateTime> = volte.flatMap { volta ->
        p.avvisi.map { volta.minusMinutes(it.minuti) } + listOfNotNull(volta.takeIf { p.senzaOrario || p.allOra == volta })
    }

    private fun boccia(errori: List<String>) {
        if (errori.isNotEmpty()) fail("${errori.size} casi diversi. I primi:\n\n" + errori.take(4).joinToString("\n\n"))
    }

    @Test
    fun i_prossimi_avvisi() {
        val errori = mutableListOf<String>()
        repeat(20000) {
            val p = promemoria()
            val dopo = momento(p)
            val quanti = caso.nextInt(1, 6)
            // alerts of different occurrences can fall on the same minute: they ring once
            val attesi = avvisi(p, volte(p, dopo.toLocalDate(), dopo.toLocalDate().plusYears(12)))
                .filter { it.isAfter(dopo) }.distinct().sorted().take(quanti)
            val avuti = Avvisi.prossimi(p, dopo, quanti).map { it.quando }
            if (attesi != avuti) errori += "PROSSIMI $p\n dopo=$dopo quanti=$quanti\n attesi=$attesi\n avuti =$avuti"
        }
        boccia(errori)
    }

    @Test
    fun l_avviso_perso() {
        val errori = mutableListOf<String>()
        repeat(20000) {
            val p = promemoria()
            val da = momento(p)
            val a = da.plusMinutes(caso.nextLong(-600, 60L * 24 * 90))
            val atteso = if (!a.isAfter(da)) null
            else avvisi(p, volte(p, da.toLocalDate(), a.toLocalDate().plusDays(40)))
                .filter { it.isAfter(da) && !it.isAfter(a) }.maxOrNull()
            val avuto = Avvisi.perso(p, da, a)?.quando
            if (atteso != avuto) errori += "PERSO $p\n da=$da a=$a\n atteso=$atteso avuto=$avuto"
        }
        boccia(errori)
    }

    @Test
    fun la_prossima_volta() {
        val errori = mutableListOf<String>()
        repeat(20000) {
            val p = promemoria()
            val adesso = momento(p)
            val attesa = volte(p, adesso.toLocalDate(), adesso.toLocalDate().plusYears(12))
                .firstOrNull { p.senzaOrario || !it.isBefore(adesso) }
            val avuta = p.prossimaVolta(adesso)
            if (attesa != avuta) errori += "PROSSIMA VOLTA $p\n adesso=$adesso attesa=$attesa avuta=$avuta"
        }
        boccia(errori)
    }

    /** The repetition by itself: never backwards, never a day skipped, and every day found is a right one. */
    @Test
    fun i_giorni_della_ripetizione() {
        val errori = mutableListOf<String>()
        repeat(6000) {
            val r = ripetizione()
            val partenza = LocalDate.of(2026, 1, 1).plusDays(caso.nextLong(0, 700))
            val da = partenza.plusDays(caso.nextLong(-50, 900))
            val giorno = r.primaVoltaDa(partenza, da)
            fun errore(cosa: String) { errori += "GIORNI $r partenza=$partenza da=$da giorno=$giorno: $cosa" }
            if (giorno == null) {
                if (r !is Ripetizione.Nessuna || !partenza.isBefore(da)) errore("nessun giorno")
                return@repeat
            }
            if (giorno.isBefore(da) || giorno.isBefore(partenza)) errore("prima della partenza o di 'da'")
            if (r.primaVoltaDa(partenza, giorno) != giorno) errore("cercando dal giorno trovato non torna lui")
            val seguente = r.primaVoltaDa(partenza, giorno.plusDays(1))
            if (seguente != null && !seguente.isAfter(giorno)) errore("il seguente ($seguente) non viene dopo")
            // no day between the start of the search and the day found is itself a day it falls on
            var g = if (da.isAfter(partenza)) da else partenza
            while (g.isBefore(giorno)) {
                if (r.primaVoltaDa(partenza, g) == g) { errore("saltato $g"); break }
                g = g.plusDays(1)
            }
            // and the day found is a right one, said the long way
            val mesi = ChronoUnit.MONTHS.between(YearMonth.from(partenza), YearMonth.from(giorno))
            when (r) {
                Ripetizione.Nessuna -> if (giorno != partenza) errore("non e' la partenza")
                is Ripetizione.OgniGiorni ->
                    if (ChronoUnit.DAYS.between(partenza, giorno) % r.ogni != 0L) errore("non e' un multiplo di ${r.ogni} giorni")
                is Ripetizione.OgniSettimane -> {
                    if (giorno.dayOfWeek !in r.giorni) errore("giorno della settimana non scelto")
                    val lunedi = { d: LocalDate -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                    if (ChronoUnit.WEEKS.between(lunedi(partenza), lunedi(giorno)) % r.ogni != 0L) errore("settimana fuori passo")
                }
                is Ripetizione.OgniMesi -> {
                    val voluto = minOf(r.giorno ?: partenza.dayOfMonth, giorno.lengthOfMonth())
                    if (giorno.dayOfMonth != voluto) errore("giorno del mese ${giorno.dayOfMonth}, voluto $voluto")
                    if (mesi % r.ogni != 0L) errore("mese fuori passo")
                }
                is Ripetizione.OgniMesiSettimana -> {
                    val voluto = YearMonth.from(giorno).atDay(1)
                        .with(TemporalAdjusters.dayOfWeekInMonth(r.ordinale, r.giornoSettimana))
                    if (giorno != voluto) errore("voluto $voluto")
                    if (mesi % r.ogni != 0L) errore("mese fuori passo")
                }
            }
        }
        boccia(errori)
    }
}
