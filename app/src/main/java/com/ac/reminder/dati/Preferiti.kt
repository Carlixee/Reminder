package com.ac.reminder.dati

import android.content.Context

/**
 * The favorite alerts: the "+ 10 min" pills under the sheet's alerts,
 * which add that alert with one tap.
 *
 * They're kept in the app's "preferences" (a small file on the phone, separate from the
 * reminders), written as plain text: "10:MINUTI,1:ORE,1:GIORNI".
 */
object Preferiti {
    private const val CHIAVE = "avvisi_preferiti"

    /** Beyond this they don't fit in one and a half rows of the sheet */
    const val MASSIMO = 6

    /** The ones you find the first time (they can all be removed) */
    val INIZIALI = listOf(PreAvviso(10, Unita.MINUTI), PreAvviso(1, Unita.ORE), PreAvviso(1, Unita.GIORNI))

    fun leggi(c: Context): List<PreAvviso> {
        // Nothing ever saved = the first time: the initial ones.
        // Saved empty ("") = you removed them: none.
        val testo = Preferenze.apri(c).getString(CHIAVE, null)
            ?: return INIZIALI
        return daTesto(testo)
    }

    fun scrivi(c: Context, lista: List<PreAvviso>) {
        Preferenze.apri(c).edit().putString(CHIAVE, aTesto(lista)).apply()
    }

    /** From the nearest to the farthest, without duplicates */
    fun ordina(lista: List<PreAvviso>) = lista.distinct().sortedBy { it.minuti }

    fun aTesto(lista: List<PreAvviso>) = lista.joinToString(",") { "${it.n}:${it.unita.name}" }

    /** Damaged or out of range pieces are thrown away, without crashing the app. */
    fun daTesto(testo: String): List<PreAvviso> = testo.split(",").mapNotNull { pezzo ->
        val parti = pezzo.split(":")
        if (parti.size != 2) return@mapNotNull null
        val unita = Unita.values().firstOrNull { it.name == parti[1] } ?: return@mapNotNull null
        val n = parti[0].toIntOrNull() ?: return@mapNotNull null
        if (n in 1..unita.massimo) PreAvviso(n, unita) else null
    }
}
