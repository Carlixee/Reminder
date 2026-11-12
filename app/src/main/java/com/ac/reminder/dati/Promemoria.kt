package com.ac.reminder.dati

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * A reminder. If it repeats, `data` is the START date (the first
 * possible occurrence) and `ripetizione` says how it goes on.
 * `attivo` is the switch in the list: off = no alerts.
 */
data class Promemoria(
    val data: LocalDate,
    val ora: LocalTime,
    val titolo: String,
    var attivo: Boolean = true,
    val ripetizione: Ripetizione = Ripetizione.Nessuna,
    val avvisi: List<PreAvviso> = PreAvviso.PREDEFINITI,
    /** The last day on which the repetition applies (included). null = forever. */
    val fino: LocalDate? = null,
    /**
     * Also rings at the exact time of THIS occurrence. It happens when you save it with all
     * its alerts already past (at 15:50 a reminder at 16:00 with "30 minutes before"):
     * instead of staying silent, it rings at 16:00. The following occurrences go back to normal.
     */
    val allOra: LocalDateTime? = null,
    /** A second text, optional (an address, what to bring): under the title and in the notification. */
    val nota: String = "",
    /**
     * No time: the reminder is for the whole day. `ora` is then the time its alert rings on that
     * day, and the reminder stays among the upcoming ones until midnight.
     */
    val senzaOrario: Boolean = false,
    /**
     * Paused: the last day (included) on which a reminder that repeats does NOT fall. Up to that
     * day its occurrences are skipped, then it starts again by itself. "Skip the next one" is the
     * same thing: a pause up to the day of the next occurrence. null, or a day already past = no pause.
     */
    val pausa: LocalDate? = null
) {
    val siRipete: Boolean get() = ripetizione !is Ripetizione.Nessuna

    /** Is it paused today? (Only the ones that repeat can be.) */
    fun inPausa(oggi: LocalDate) = siRipete && pausa != null && !pausa.isBefore(oggi)

    /** The first day it falls on, from `da` on and after the pause. null = never again. */
    fun primoGiornoDa(da: LocalDate): LocalDate? =
        ripetizione.primaVoltaDa(data, if (inPausa(da)) pausa!!.plusDays(1) else da)

    /** Start date + time: used to keep the file in order. */
    val partenza: LocalDateTime get() = data.atTime(ora)

    /**
     * The next time it "falls", from now on.
     * null = it's past: it doesn't repeat, or the repetition is over (it goes among the past ones).
     */
    fun prossimaVolta(adesso: LocalDateTime): LocalDateTime? {
        var giorno = primoGiornoDa(adesso.toLocalDate()) ?: return null
        if (!senzaOrario && giorno.atTime(ora).isBefore(adesso)) {
            // falls today, but the time has already passed: look from tomorrow
            // (one without a time lasts the whole day: today is still its day)
            giorno = ripetizione.primaVoltaDa(data, giorno.plusDays(1)) ?: return null
        }
        if (fino != null && giorno.isAfter(fino)) return null
        return giorno.atTime(ora)
    }

    /**
     * The last time it fell before `fino`: it's the date with which a
     * finished repetition shows among the past ones (not the start date).
     */
    fun ultimaVolta(): LocalDateTime {
        val limite = fino ?: return partenza
        var giorno = ripetizione.primaVoltaDa(data, data) ?: return partenza
        if (giorno.isAfter(limite)) return partenza
        var giri = 0
        while (giri++ < 5000) {
            val dopo = ripetizione.primaVoltaDa(data, giorno.plusDays(1)) ?: break
            if (dopo.isAfter(limite)) break
            giorno = dopo
        }
        return giorno.atTime(ora)
    }

    /** "Ogni settimana: lun · fino al 30 novembre" (without `fino`: as before).
     *  In English: "Every week: Mon · until 30 November" */
    fun descrizioneRipetizione(): String {
        val base = ripetizione.descrizione(data)
        val f = fino ?: return base
        val mese = Lingua.nome(f.month.getDisplayName(TextStyle.FULL, Lingua.locale))
        val anno = if (f.year != data.year) " ${f.year}" else ""
        val finoA = Lingua.testo("fino ${al(f.dayOfMonth)}", "until ")
        return "$base · $finoA${f.dayOfMonth} $mese$anno"
    }

    // --- Saving to file (JSON) ---
    //
    // {"data":"2026-11-10","ora":"16:00","titolo":"Dentista","attivo":true,"ripeti":{…},"avvisi":[…]}

    fun aJson(): JSONObject = JSONObject().apply {
        put("data", data.toString())
        put("ora", ora.toString())
        put("titolo", titolo)
        put("attivo", attivo)
        // {"tipo":"SETTIMANE","ogni":1,"giorni":[1],"fino":"2026-12-31"}
        ripetizioneAJson(ripetizione)?.let { r ->
            fino?.let { r.put("fino", it.toString()) }
            put("ripeti", r)
        }
        // [{"n":24,"u":"ORE"},{"n":30,"u":"MINUTI"}]
        put("avvisi", JSONArray(avvisi.map { JSONObject().put("n", it.n).put("u", it.unita.name) }))
        allOra?.let { put("allOra", it.toString()) }   // "2026-11-10T16:00"
        if (nota.isNotEmpty()) put("nota", nota)
        if (senzaOrario) put("senzaOrario", true)
        pausa?.let { put("pausa", it.toString()) }     // "2026-11-20"
    }

    companion object {
        fun daJson(o: JSONObject) = Promemoria(
            data = LocalDate.parse(o.getString("data")),
            ora = LocalTime.parse(o.getString("ora")),
            titolo = o.getString("titolo"),
            attivo = o.optBoolean("attivo", true),
            ripetizione = ripetizioneDaJson(o.optJSONObject("ripeti")),
            avvisi = avvisiDaJson(o.optJSONArray("avvisi")),
            fino = o.optJSONObject("ripeti")?.optString("fino", "")
                ?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
            // If it's missing (or damaged) it doesn't ring at the exact time: as before
            allOra = o.optString("allOra", "").takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            // The reminders saved by versions 1.0 and 1.1 have neither: no note, and a time
            nota = o.optString("nota", ""),
            senzaOrario = o.optBoolean("senzaOrario", false),
            pausa = o.optString("pausa", "").takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        )

        /** "fino al 30", but "fino all'1", "all'8", "all'11" (numbers starting with a vowel). */
        fun al(giorno: Int) = if (giorno == 1 || giorno == 8 || giorno == 11) "all'" else "al "

        /** If "avvisi" is missing from the file (it should never happen), the default ones apply. */
        private fun avvisiDaJson(array: JSONArray?): List<PreAvviso> {
            if (array == null) return PreAvviso.PREDEFINITI
            val lista = (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val unita = Unita.values().firstOrNull { it.name == o.optString("u") } ?: return@mapNotNull null
                PreAvviso(o.optInt("n", 1).coerceIn(1, unita.massimo), unita)
            }
            return PreAvviso.ordina(lista)
        }

        fun listaAJson(lista: List<Promemoria>): String {
            val array = JSONArray()
            lista.forEach { array.put(it.aJson()) }
            return array.toString()
        }

        fun listaDaJson(testo: String): MutableList<Promemoria> {
            val array = JSONArray(testo)
            val lista = mutableListOf<Promemoria>()
            for (i in 0 until array.length()) lista.add(daJson(array.getJSONObject(i)))
            return lista
        }

        /** {"tipo":"SETTIMANE","ogni":2,"giorni":[1,3]}  (1 = Monday … 7 = Sunday) */
        private fun ripetizioneAJson(r: Ripetizione): JSONObject? = when (r) {
            Ripetizione.Nessuna -> null
            is Ripetizione.OgniGiorni -> JSONObject().put("tipo", "GIORNI").put("ogni", r.ogni)
            is Ripetizione.OgniMesi -> JSONObject().put("tipo", "MESI").put("ogni", r.ogni)
                .apply { r.giorno?.let { put("giorno", it) } }
            is Ripetizione.OgniMesiSettimana -> JSONObject().put("tipo", "MESI_SETTIMANA").put("ogni", r.ogni)
                .put("ordinale", r.ordinale).put("giornoSettimana", r.giornoSettimana.value)
            is Ripetizione.OgniSettimane -> JSONObject().put("tipo", "SETTIMANE").put("ogni", r.ogni)
                .put("giorni", JSONArray(r.giorni.sorted().map { it.value }))
        }

        /** A reminder that doesn't repeat has no "ripeti" in the file: Nessuna applies. */
        private fun ripetizioneDaJson(o: JSONObject?): Ripetizione {
            if (o == null) return Ripetizione.Nessuna
            val ogni = o.optInt("ogni", 1).coerceAtLeast(1)
            return when (o.optString("tipo")) {
                "GIORNI" -> Ripetizione.OgniGiorni(ogni)
                "MESI" -> Ripetizione.OgniMesi(ogni, if (o.has("giorno")) o.getInt("giorno") else null)
                "MESI_SETTIMANA" -> Ripetizione.OgniMesiSettimana(
                    ogni, o.optInt("ordinale", 1), DayOfWeek.of(o.optInt("giornoSettimana", 1))
                )
                "SETTIMANE" -> {
                    val array = o.optJSONArray("giorni") ?: JSONArray()
                    val giorni = (0 until array.length()).map { DayOfWeek.of(array.getInt(it)) }.toSet()
                    Ripetizione.OgniSettimane(ogni, giorni)
                }
                else -> Ripetizione.Nessuna
            }
        }
    }
}
