package com.ac.reminder.dati

import java.util.Locale

/**
 * The language of the texts written by the CODE: dates, repetitions, notification text.
 * The screens' texts instead are in the text files: res/values (English)
 * and res/values-it (Italian), and Android chooses by itself.
 *
 * There's nothing from Android in here, so the tests on the PC work:
 * in the app SceltaLingua sets it (at launch and at every change), in the tests it's chosen by hand.
 */
object Lingua {

    /** ITALIAN or ENGLISH: the only two languages the code can write. */
    @Volatile var locale: Locale = Locale.ITALIAN

    val italiano: Boolean get() = locale.language == "it"

    /** The right text of the two: Lingua.testo("Ogni giorno", "Every day") */
    fun <T> testo(italiano: T, inglese: T): T = if (this.italiano) italiano else inglese

    /** Names of days and months: lowercase in Italian ("martedì"), capitalised in English ("Tuesday"). */
    fun nome(s: String): String = if (italiano) s.lowercase(locale) else s

    /**
     * "primo", "seconda", "ultimo" / "first", "second", "last": which one in the month.
     * `quale` goes from 1 to 4, anything else is the last. In Italian it agrees with the
     * day ("la seconda domenica"); English has no masculine and feminine.
     */
    fun ordinale(quale: Int, femminile: Boolean = false): String {
        if (!italiano) return listOf("first", "second", "third", "fourth").getOrElse(quale - 1) { "last" }
        val radice = when (quale) { 1 -> "prim"; 2 -> "second"; 3 -> "terz"; 4 -> "quart"; else -> "ultim" }
        return radice + if (femminile) "a" else "o"
    }

    /** In English day numbers need the suffix: 1st, 2nd, 3rd, 4th… 11th, 12th, 13th, 21st. */
    fun ordinaleInglese(n: Int): String {
        val coda = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
        }
        return "$n$coda"
    }
}
