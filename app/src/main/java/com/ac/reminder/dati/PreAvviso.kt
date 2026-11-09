package com.ac.reminder.dati

/**
 * The units you choose an alert with, and how far each one goes:
 * from 1 minute before up to about a month before (31 days / 4 weeks).
 */
enum class Unita(
    val minuti: Int,
    val massimo: Int,
    /** singular and plural, in Italian and in English */
    private val italiano: Pair<String, String>,
    private val inglese: Pair<String, String>,
    /** The quick buttons that appear when this unit is chosen */
    val rapidi: List<Int>
) {
    MINUTI(1, 59, "minuto" to "minuti", "minute" to "minutes", listOf(5, 10, 15, 30, 45)),
    ORE(60, 48, "ora" to "ore", "hour" to "hours", listOf(1, 2, 3, 6, 12)),
    GIORNI(1440, 31, "giorno" to "giorni", "day" to "days", listOf(1, 2, 3, 5)),
    SETTIMANE(10080, 4, "settimana" to "settimane", "week" to "weeks", listOf(1, 2, 3, 4));

    fun nome(n: Int): String {
        val (uno, tanti) = Lingua.testo(italiano, inglese)
        return if (n == 1) uno else tanti
    }
}

/**
 * An alert chosen by you: "n units before". It's saved as it is
 * (24 hours stays "24 hours", it doesn't become "1 day"): you read it back as you wrote it.
 */
data class PreAvviso(val n: Int, val unita: Unita) {

    val minuti: Long get() = n.toLong() * unita.minuti

    /** "30 minutes before", "1 hour before", "2 weeks before" */
    fun etichetta() = "$n ${unita.nome(n)} " + Lingua.testo("prima", "before")

    /** For the favorites pills: "10 min", "1 hour", "2 days" */
    fun breve() = if (unita == Unita.MINUTI) "$n min" else "$n ${unita.nome(n)}"

    companion object {
        /** The ones already on in a new reminder. */
        val PREDEFINITI = listOf(PreAvviso(24, Unita.ORE), PreAvviso(30, Unita.MINUTI))

        /** More than this becomes noise (and every reminder has 10 alarm slots). */
        const val MASSIMO = 5

        /** In order from the farthest to the nearest, without duplicates of the same moment. */
        fun ordina(lista: List<PreAvviso>) = lista.distinctBy { it.minuti }.sortedByDescending { it.minuti }
    }
}
