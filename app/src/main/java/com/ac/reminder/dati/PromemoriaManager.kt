package com.ac.reminder.dati

import android.content.Context
import android.util.AtomicFile
import androidx.core.app.NotificationManagerCompat
import com.ac.reminder.notifiche.AlarmScheduler
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The reminders' archive and their alarms, always kept in step.
 *
 * Every reminder "owns" 10 alarm ids, starting from
 * 1,000,000 + (position + 1) × 10: fixed numbers, so every alarm
 * can always be found again, replaced or switched off.
 */
object PromemoriaManager {

    // The file with all the reminders. DON'T rename it: anyone who already has the app would lose them
    private const val NOME_FILE = "promemoria.json"

    private const val BASE_ID = 1_000_000
    private const val FINE_ZONA = 2_000_000
    private const val ID_PER_PROMEMORIA = 10

    private fun idBase(indice: Int) = BASE_ID + (indice + 1) * ID_PER_PROMEMORIA

    /**
     * Up to when the alerts are "in order" (rung, or queued again from there on).
     * When the phone is switched on it says from when to look for alerts missed while it was off.
     */
    private const val CHIAVE_FINO_A = "avvisi_fino_a"
    private fun preferenze(context: Context) = context.getSharedPreferences("preferenze", Context.MODE_PRIVATE)
    private fun millis(q: LocalDateTime) = q.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** `soloAvanti`: an alert that rang late doesn't move the mark back. */
    private fun segnaFinoA(context: Context, quando: LocalDateTime, soloAvanti: Boolean = false) {
        val p = preferenze(context)
        if (soloAvanti && millis(quando) <= p.getLong(CHIAVE_FINO_A, 0L)) return
        p.edit().putLong(CHIAVE_FINO_A, millis(quando)).apply()
    }

    /**
     * AtomicFile: a new copy is written first and only at the end does it take the place
     * of the old one. If the phone switches off halfway through saving, the previous
     * version stays whole, instead of a half-written file that can't be opened any more.
     */
    private fun file(context: Context) = AtomicFile(File(context.filesDir, NOME_FILE))

    fun carica(context: Context): MutableList<Promemoria> {
        val testo = try {
            String(file(context).readFully(), Charsets.UTF_8)
        } catch (e: FileNotFoundException) {
            return mutableListOf()   // first launch: no reminders yet
        }
        return Promemoria.listaDaJson(testo)
    }

    private fun salva(context: Context, lista: List<Promemoria>) {
        val f = file(context)
        val flusso = f.startWrite()
        try {
            flusso.write(Promemoria.listaAJson(lista).toByteArray(Charsets.UTF_8))
            f.finishWrite(flusso)    // only now does the new copy take the place of the old one
        } catch (e: IOException) {
            f.failWrite(flusso)      // something went wrong: the previous version stays
            throw e
        }
    }

    /** The ones still to come, with their next moment, nearest first. */
    fun inArrivo(context: Context, adesso: LocalDateTime): List<Pair<Promemoria, LocalDateTime>> =
        carica(context)
            .mapNotNull { p -> p.prossimaVolta(adesso)?.let { p to it } }
            .sortedBy { it.second }

    fun aggiungi(context: Context, nuovo: Promemoria) = cambia(context) { it.add(nuovo) }

    fun modifica(context: Context, indice: Int, nuovo: Promemoria) =
        cambia(context) { if (indice in it.indices) it[indice] = nuovo }

    /** Deletes for good (if it repeats, it goes away with all its occurrences). */
    fun rimuovi(context: Context, indice: Int) =
        cambia(context) { if (indice in it.indices) it.removeAt(indice) }

    /** Empties the archive: away with the past reminders that don't repeat. */
    fun rimuoviPassati(context: Context) = cambia(context) { lista ->
        val adesso = LocalDateTime.now()
        lista.removeAll { it.prossimaVolta(adesso) == null }
    }

    /**
     * Every change goes through here, always in the same order:
     * switch off the old alarms → change → save in order → switch on again.
     * (Positions change, and so do the ids: everything is redone.)
     */
    private fun cambia(context: Context, modifica: (MutableList<Promemoria>) -> Unit) {
        val lista = carica(context)
        val prima = lista.toList()
        lista.indices.forEach { spegni(context, it) }
        modifica(lista)
        lista.sortBy { it.partenza }
        salva(context, lista)
        togliNotificheVecchie(context, prima, lista)
        val adesso = LocalDateTime.now()
        segnaFinoA(context, adesso)
        val quanti = quota(lista, adesso)
        lista.forEachIndexed { i, p -> accendi(context, i, p, adesso, quanti) }
    }

    /** After a reboot or an app update: everything is scheduled again. */
    fun riprogrammaTutti(context: Context) {
        val adesso = LocalDateTime.now()
        val lista = carica(context)
        val quanti = quota(lista, adesso)
        lista.forEachIndexed { i, p ->
            spegni(context, i)
            accendi(context, i, p, adesso, quanti)
        }
        segnaFinoA(context, adesso)
    }

    /**
     * Right after the phone is switched on, BEFORE the alarms are queued again: the alerts that fell
     * while it was off. One per reminder (the most recent). Empty if we don't know since when.
     */
    fun persiASpento(context: Context, adesso: LocalDateTime): List<Pair<Promemoria, Avviso>> {
        val finoA = preferenze(context).getLong(CHIAVE_FINO_A, 0L)
        if (finoA == 0L) return emptyList()
        val da = Instant.ofEpochMilli(finoA).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return carica(context).mapNotNull { p -> Avvisi.perso(p, da, adesso)?.let { p to it } }
    }

    /**
     * The notifications of reminders that were deleted, or changed in title, date, time, repetition
     * or on/off, go away: they would say things that are no longer true ("In 30 minutes, at 16:00"
     * for a reminder just moved to 18:00).
     */
    private fun togliNotificheVecchie(context: Context, prima: List<Promemoria>, dopo: List<Promemoria>) {
        fun chiave(p: Promemoria) = listOf(p.titolo, p.partenza, p.ripetizione, p.fino, p.attivo)
        val restano = dopo.map(::chiave).toSet()
        val notifiche = NotificationManagerCompat.from(context)
        prima.filter { chiave(it) !in restano }.forEach { notifiche.cancel(idNotifica(it)) }
    }

    /**
     * The bell has just rung alert `id`, due at `previstoMillis`:
     * the next alerts of THAT reminder are scheduled (the chain of the ones that
     * repeat). It starts again from the due time, not from "now", so the same
     * alert is never scheduled twice.
     */
    fun dopoUnAvviso(context: Context, id: Int, previstoMillis: Long) {
        if (id < BASE_ID + ID_PER_PROMEMORIA || id >= FINE_ZONA) return
        val indice = (id - BASE_ID) / ID_PER_PROMEMORIA - 1
        val lista = carica(context)
        if (indice !in lista.indices) return

        val previsto = Instant.ofEpochMilli(previstoMillis)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        spegni(context, indice)
        accendi(context, indice, lista[indice], previsto, quota(lista, LocalDateTime.now()))
        segnaFinoA(context, previsto, soloAvanti = true)
    }

    private fun spegni(context: Context, indice: Int) {
        for (posto in 0 until ID_PER_PROMEMORIA) AlarmScheduler.cancella(context, idBase(indice) + posto)
    }

    /**
     * The NOTIFICATION's number: tied to the reminder (title + start date and time),
     * not to its position in the list like the alarm's. So, if the list changes
     * order, another reminder's notification doesn't take the place of one not read yet.
     * Alerts of the same reminder, instead, replace each other (the most recent stays).
     */
    fun idNotifica(p: Promemoria) = "${p.titolo}|${p.partenza}".hashCode()

    /** How many alarms each, counting only the reminders that still have something to ring. */
    private fun quota(lista: List<Promemoria>, adesso: LocalDateTime) =
        Avvisi.quantiPerOgnuno(lista.count { it.attivo && it.prossimaVolta(adesso) != null })

    private fun accendi(context: Context, indice: Int, p: Promemoria, dopo: LocalDateTime, quanti: Int) {
        if (!p.attivo) return
        Avvisi.prossimi(p, dopo, quanti).forEachIndexed { posto, avviso ->
            AlarmScheduler.programmaA(
                context,
                avviso.quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                id = idBase(indice) + posto,
                notifica = idNotifica(p),
                titolo = p.titolo,
                testo = avviso.testo
            )
        }
    }
}
