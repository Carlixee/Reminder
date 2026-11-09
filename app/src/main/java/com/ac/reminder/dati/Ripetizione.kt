package com.ac.reminder.dati

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * How often a reminder repeats. The three forms are your three menus:
 *  - every N days
 *  - every N weeks, on the chosen days (Mon … Sun)
 *  - every N months, on the same day of the month as the start date
 *    (the 31st in shorter months becomes the last day of the month;
 *     with N = 12 it's "every year": birthdays, deadlines, car tax…)
 *
 * In here there's only pure calculation on dates, no Android:
 * that's why it's tested on the PC (RipetizioneTest).
 */
sealed class Ripetizione {

    object Nessuna : Ripetizione()
    data class OgniGiorni(val ogni: Int) : Ripetizione()
    data class OgniSettimane(val ogni: Int, val giorni: Set<DayOfWeek>) : Ripetizione()
    /** Every N months, on day `giorno` (1…31). null = the one of the start date. */
    data class OgniMesi(val ogni: Int, val giorno: Int? = null) : Ripetizione()

    /**
     * Every N months, on a day of the week: "the second Thursday",
     * "the last Friday". `ordinale` goes from 1 to 4, or -1 = the last.
     */
    data class OgniMesiSettimana(val ogni: Int, val ordinale: Int, val giornoSettimana: DayOfWeek) : Ripetizione()

    /**
     * The first day on which the reminder "falls", searching from `da` onwards
     * (included). `partenza` is the date chosen in the dialog: before
     * it, it never falls. null = it doesn't fall any more (one that doesn't repeat and is past).
     */
    fun primaVoltaDa(partenza: LocalDate, da: LocalDate): LocalDate? {
        val inizio = if (da.isAfter(partenza)) da else partenza

        return when (this) {
            Nessuna -> if (partenza.isBefore(da)) null else partenza

            is OgniGiorni -> {
                // how many "steps" of N days are needed to reach at least `inizio`
                val giorni = ChronoUnit.DAYS.between(partenza, inizio)
                val passi = (giorni + ogni - 1) / ogni
                partenza.plusDays(passi * ogni)
            }

            is OgniSettimane -> primoSettimanale(partenza, inizio)

            is OgniMesi -> primoMensile(ogni, partenza, inizio) { mese ->
                giornoDelMese(mese, giorno ?: partenza.dayOfMonth)
            }

            is OgniMesiSettimana -> primoMensile(ogni, partenza, inizio) { mese ->
                mese.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(ordinale, giornoSettimana))
            }
        }
    }

    /**
     * Every N months: the months are counted from the starting one (only the multiples
     * of N) and in each one `cade` says which day. The first one not yet past wins.
     */
    private fun primoMensile(
        ogni: Int, partenza: LocalDate, inizio: LocalDate, cade: (YearMonth) -> LocalDate
    ): LocalDate {
        val meseZero = YearMonth.from(partenza)
        var mesi = ChronoUnit.MONTHS.between(meseZero, YearMonth.from(inizio)) / ogni * ogni
        var giorno = cade(meseZero.plusMonths(mesi))
        while (giorno.isBefore(inizio)) {
            mesi += ogni
            giorno = cade(meseZero.plusMonths(mesi))
        }
        return giorno
    }

    /** "Ogni 2 settimane: lun, mer", "Ogni mese, il giorno 31"… (empty if it doesn't repeat). */
    fun descrizione(partenza: LocalDate): String = if (Lingua.italiano) descrizioneItaliana(partenza) else descrizioneInglese(partenza)

    private fun descrizioneItaliana(partenza: LocalDate): String = when (this) {
        Nessuna -> ""
        is OgniGiorni -> if (ogni == 1) "Ogni giorno" else "Ogni $ogni giorni"
        is OgniSettimane -> (if (ogni == 1) "Ogni settimana" else "Ogni $ogni settimane") + ": " + nomiGiorni(partenza)
        is OgniMesi -> {
            val g = giorno ?: partenza.dayOfMonth
            when (ogni) {
                1 -> "Ogni mese, il giorno $g"
                12 -> "Ogni anno, il $g ${nomeMese(partenza)}"
                else -> "Ogni $ogni mesi, il giorno $g"
            }
        }
        is OgniMesiSettimana -> {
            val quale = nelMese(ordinale, giornoSettimana)
            when (ogni) {
                1 -> "Ogni mese, $quale"
                12 -> "Ogni anno, $quale di ${nomeMese(partenza)}"
                else -> "Ogni $ogni mesi, $quale"
            }
        }
    }

    /** "Every 2 weeks: Mon, Wed", "Every month, on the 31st", "Every year, on the last Sunday of March" */
    private fun descrizioneInglese(partenza: LocalDate): String = when (this) {
        Nessuna -> ""
        is OgniGiorni -> if (ogni == 1) "Every day" else "Every $ogni days"
        is OgniSettimane -> (if (ogni == 1) "Every week" else "Every $ogni weeks") + ": " + nomiGiorni(partenza)
        is OgniMesi -> {
            val g = giorno ?: partenza.dayOfMonth
            when (ogni) {
                1 -> "Every month, on the ${Lingua.ordinaleInglese(g)}"
                12 -> "Every year, on $g ${nomeMese(partenza)}"
                else -> "Every $ogni months, on the ${Lingua.ordinaleInglese(g)}"
            }
        }
        is OgniMesiSettimana -> {
            val quale = "the " + listOf("first", "second", "third", "fourth").getOrElse(ordinale - 1) { "last" } +
                " " + giornoSettimana.getDisplayName(TextStyle.FULL, Lingua.locale)
            when (ogni) {
                1 -> "Every month, on $quale"
                12 -> "Every year, on $quale of ${nomeMese(partenza)}"
                else -> "Every $ogni months, on $quale"
            }
        }
    }

    /** "lun, mer" / "Mon, Wed": the chosen days, in order from Monday */
    private fun OgniSettimane.nomiGiorni(partenza: LocalDate) =
        (giorni.ifEmpty { setOf(partenza.dayOfWeek) }).sorted()
            .joinToString(", ") { Lingua.nome(it.getDisplayName(TextStyle.SHORT, Lingua.locale)).replace(".", "") }

    /** The month of the FIRST occurrence (with "every year" it's the one that counts). */
    private fun nomeMese(partenza: LocalDate): String =
        Lingua.nome((primaVoltaDa(partenza, partenza) ?: partenza).month.getDisplayName(TextStyle.FULL, Lingua.locale))

    /** "il secondo giovedì", "l'ultimo venerdì", "la prima domenica" (domenica is feminine). */
    private fun nelMese(ordinale: Int, g: DayOfWeek): String {
        val nome = g.getDisplayName(TextStyle.FULL, Locale.ITALIAN).lowercase()
        val femminile = g == DayOfWeek.SUNDAY
        val radice = when (ordinale) { 1 -> "prim"; 2 -> "second"; 3 -> "terz"; 4 -> "quart"; else -> "ultim" }
        val articolo = when {
            ordinale !in 1..4 -> "l'"
            femminile -> "la "
            else -> "il "
        }
        return articolo + radice + (if (femminile) "a" else "o") + " " + nome
    }

    /** Every N weeks on the chosen days: the first good day from `inizio` onwards. */
    private fun OgniSettimane.primoSettimanale(partenza: LocalDate, inizio: LocalDate): LocalDate {
        val scelti = (giorni.ifEmpty { setOf(partenza.dayOfWeek) }).sorted()
        // weeks are counted from the Monday of the starting week:
        // it only falls in the ones that are "multiples of N" (0, N, 2N…)
        val lunediZero = partenza.minusDays(partenza.dayOfWeek.value - 1L)
        val lunediInizio = inizio.minusDays(inizio.dayOfWeek.value - 1L)
        var settimana = ChronoUnit.WEEKS.between(lunediZero, lunediInizio) / ogni * ogni
        while (true) {
            val lunedi = lunediZero.plusWeeks(settimana)
            for (g in scelti) {
                val giorno = lunedi.plusDays(g.value - 1L)
                if (!giorno.isBefore(inizio)) return giorno
            }
            settimana += ogni   // week over without finding it: skip to the next good one
        }
    }

    private fun giornoDelMese(mese: YearMonth, giorno: Int): LocalDate =
        mese.atDay(minOf(giorno, mese.lengthOfMonth()))
}
