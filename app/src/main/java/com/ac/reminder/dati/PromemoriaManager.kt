package com.ac.reminder.dati

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import androidx.core.app.NotificationManagerCompat
import com.ac.reminder.notifiche.AlarmScheduler
import com.ac.reminder.notifiche.NotificationHelper
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
     * The bookmark: up to when the alerts are "in order" (rung, reported, or queued again from
     * there on). Every alert that rings moves it forward, so whatever was due after it never rang.
     * It's the time on the phone's clock ("2026-11-11T15:30"), like the alerts themselves: after a
     * trip to another time zone nothing is taken for missed just because the clock went back.
     */
    private const val CHIAVE_SEGNO = "avvisi_segno"
    /** Versions 1.0 and 1.1 kept the bookmark here, in milliseconds: read once, then replaced. */
    private const val CHIAVE_FINO_A = "avvisi_fino_a"
    /** How many times the phone had been switched on when the bookmark was written. */
    private const val CHIAVE_ACCENSIONI = "avvisi_accensioni"

    /** Android counts the times the phone is switched on; -1 if this phone doesn't say. */
    private fun accensioni(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** The bookmark, or null if it was never written (first launch). */
    private fun segno(context: Context): LocalDateTime? {
        val p = Preferenze.apri(context)
        p.getString(CHIAVE_SEGNO, null)?.let { testo ->
            runCatching { LocalDateTime.parse(testo) }.getOrNull()?.let { return it }
        }
        val millis = p.getLong(CHIAVE_FINO_A, 0L)
        return if (millis == 0L) null
        else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    /** `soloAvanti`: an alert that rang late doesn't move the bookmark back. */
    private fun segnaFinoA(context: Context, quando: LocalDateTime, soloAvanti: Boolean = false) {
        if (soloAvanti && segno(context)?.isBefore(quando) == false) return
        val scrivi = Preferenze.apri(context).edit().putString(CHIAVE_SEGNO, quando.toString()).remove(CHIAVE_FINO_A)
        // An alert that rings doesn't touch the count: only queueing the alarms again "sees" a switch-on
        if (!soloAvanti) scrivi.putInt(CHIAVE_ACCENSIONI, accensioni(context))
        scrivi.apply()
    }

    /**
     * Before the bookmark moves: the alerts due after it that never rang. Either they fell while the
     * phone was off (Android deletes the alarms at shutdown), or they're late in Android's queue and
     * queueing everything again would throw them away in silence. One notification per reminder,
     * for the most recent one.
     *
     * `accensione`: Android has just said the phone was switched on. But it says so also to an app
     * that was only stopped and then started again, with the phone on all along: when the count of
     * switch-ons can be read, the count decides. (It also doesn't wait for that signal, which can
     * arrive when the app is already open.)
     */
    private fun recupera(context: Context, lista: List<Promemoria>, adesso: LocalDateTime, accensione: Boolean = false) {
        val da = segno(context) ?: return
        val contate = accensioni(context)
        val allora = Preferenze.apri(context).getInt(CHIAVE_ACCENSIONI, -1)
        val spento = if (contate != -1 && allora != -1) contate != allora else accensione
        for (p in lista) {
            val avviso = Avvisi.perso(p, da, adesso) ?: continue
            NotificationHelper.mostraNonSuonato(context, p, avviso, spento, adesso)
        }
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

    // These four answer false when the file couldn't be written (phone storage full):
    // nothing has changed, and whoever asked says so.
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
     * report what never rang → switch off the old alarms → change → save in order → switch on again.
     * (Positions change, and so do the ids: the alarms are redone. Only for the reminders that
     * can have any, though: the past ones are left alone, see Avvisi.puoAvereSveglie.)
     * If saving fails the old list stays, and so do its alarms: they are queued again as they were.
     */
    private fun cambia(context: Context, modifica: (MutableList<Promemoria>) -> Unit): Boolean {
        val adesso = LocalDateTime.now()
        val lista = carica(context)
        val prima = lista.toList()
        val segno = segnoNonFuturo(context, adesso)
        recupera(context, prima, adesso)
        prima.forEachIndexed { i, p -> if (Avvisi.puoAvereSveglie(p, segno)) spegni(context, i) }
        modifica(lista)
        lista.sortBy { it.partenza }
        val salvata = try {
            salva(context, lista)
            true
        } catch (e: IOException) {
            false
        }
        val valida = if (salvata) lista else prima   // the list that is in the file now
        if (salvata) togliNotificheVecchie(context, prima, lista)
        segnaFinoA(context, adesso)
        val quanti = quota(valida, adesso)
        valida.forEachIndexed { i, p -> if (Avvisi.puoAvereSveglie(p, adesso)) accendi(context, i, p, adesso, quanti) }
        return salvata
    }

    /**
     * Queues the alarms again: when the app is opened (it repairs any alarm lost along the way),
     * after the phone is switched on or the app updated, when the time or the time zone changes.
     * First it reports the alerts that never rang, then it moves the bookmark to now.
     */
    fun riprogrammaTutti(context: Context, accensione: Boolean = false) {
        val adesso = LocalDateTime.now()
        val lista = carica(context)
        val segno = segnoNonFuturo(context, adesso)
        recupera(context, lista, adesso, accensione)
        val quanti = quota(lista, adesso)
        lista.forEachIndexed { i, p -> if (Avvisi.puoAvereSveglie(p, segno)) accendi(context, i, p, adesso, quanti) }
        segnaFinoA(context, adesso)
    }

    /** The bookmark, but never later than now (the clock may have been put back): the earlier of the two. */
    private fun segnoNonFuturo(context: Context, adesso: LocalDateTime): LocalDateTime =
        segno(context)?.takeIf { it.isBefore(adesso) } ?: adesso

    /**
     * The notifications of reminders that were deleted, or changed in title, note, date, time,
     * repetition or on/off, go away: they would say things that are no longer true ("In 30 minutes,
     * at 16:00" for a reminder just moved to 18:00).
     */
    private fun togliNotificheVecchie(context: Context, prima: List<Promemoria>, dopo: List<Promemoria>) {
        fun chiave(p: Promemoria) = listOf(p.titolo, p.nota, p.partenza, p.senzaOrario, p.ripetizione, p.fino, p.attivo)
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
        accendi(context, indice, lista[indice], previsto, quota(lista, LocalDateTime.now()))
        segnaFinoA(context, previsto, soloAvanti = true)
    }

    /** Of the 10 ids a reminder owns, only the first few are ever used (Avvisi.DA_PROGRAMMARE). */
    private fun spegni(context: Context, indice: Int, daPosto: Int = 0) {
        for (posto in daPosto until Avvisi.DA_PROGRAMMARE) AlarmScheduler.cancella(context, idBase(indice) + posto)
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

    /**
     * Queues the next alerts of one reminder. An alarm set again with the same id REPLACES the one
     * that was there, so nothing is cancelled first: only the places left over are cleared.
     */
    private fun accendi(context: Context, indice: Int, p: Promemoria, dopo: LocalDateTime, quanti: Int) {
        val avvisi = if (p.attivo) Avvisi.prossimi(p, dopo, quanti) else emptyList()
        spegni(context, indice, daPosto = avvisi.size)
        avvisi.forEachIndexed { posto, avviso ->
            AlarmScheduler.programmaA(
                context,
                avviso.quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                id = idBase(indice) + posto,
                notifica = idNotifica(p),
                titolo = p.titolo,
                testo = avviso.testo,
                nota = p.nota
            )
        }
    }
}
