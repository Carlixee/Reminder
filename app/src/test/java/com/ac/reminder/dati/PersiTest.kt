package com.ac.reminder.dati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The alerts that never rang: fallen while the phone was off (Android deletes the alarms at shutdown), or late. */
class PersiTest {

    private fun dt(testo: String) = LocalDateTime.parse(testo)

    /** Every day at 16:00, with "30 minutes before" (at 15:30). */
    private val quotidiano = Promemoria(
        dt("2026-11-01T16:00").toLocalDate(), dt("2026-11-01T16:00").toLocalTime(), "Ethical",
        ripetizione = Ripetizione.OgniGiorni(1), avvisi = listOf(PreAvviso(30, Unita.MINUTI))
    )

    @Test
    fun spento_durante_l_avviso() {
        // off from 15:00 to 15:40: the 15:30 alert is missed, for the 16:00 occurrence
        val perso = Avvisi.perso(quotidiano, dt("2026-11-10T15:00"), dt("2026-11-10T15:40"))!!
        assertEquals(dt("2026-11-10T15:30"), perso.quando)
        assertEquals(dt("2026-11-10T16:00"), perso.volta)
    }

    @Test
    fun spento_senza_avvisi_in_mezzo() {
        assertNull(Avvisi.perso(quotidiano, dt("2026-11-10T15:31"), dt("2026-11-10T23:00")))
        // the alert exactly at the last known moment had already rung
        assertNull(Avvisi.perso(quotidiano, dt("2026-11-10T15:30"), dt("2026-11-10T15:40")))
    }

    @Test
    fun spento_per_giorni_conta_il_piu_recente() {
        val perso = Avvisi.perso(quotidiano, dt("2026-11-10T12:00"), dt("2026-11-13T09:00"))!!
        assertEquals(dt("2026-11-12T15:30"), perso.quando)
    }

    @Test
    fun spento_dall_utente_niente_avviso_perso() {
        assertNull(Avvisi.perso(quotidiano.copy(attivo = false), dt("2026-11-10T15:00"), dt("2026-11-10T15:40")))
    }

    @Test
    fun spento_per_anni_trova_ancora_il_piu_recente() {
        // more than 500 alerts in between: the search doesn't stop at the first ones
        val perso = Avvisi.perso(quotidiano, dt("2026-11-10T12:00"), dt("2028-11-13T09:00"))!!
        assertEquals(dt("2028-11-12T15:30"), perso.quando)
    }

    @Test
    fun orologio_tornato_indietro_niente_di_perso() {
        // after a trip west "now" is earlier than the bookmark: nothing in between
        assertNull(Avvisi.perso(quotidiano, dt("2026-11-10T18:00"), dt("2026-11-10T12:00")))
        assertNull(Avvisi.perso(quotidiano, dt("2026-11-10T15:40"), dt("2026-11-10T15:40")))
    }

    @Test
    fun anche_l_avviso_all_ora_esatta_puo_essere_perso() {
        val conOraEsatta = quotidiano.copy(allOra = dt("2026-11-10T16:00"))
        val perso = Avvisi.perso(conOraEsatta, dt("2026-11-10T15:45"), dt("2026-11-10T16:10"))!!
        assertEquals(dt("2026-11-10T16:00"), perso.quando)
        assertEquals("Adesso, alle 16:00", perso.testo)
    }

    @Test
    fun con_piu_avvisi_vale_l_ultimo_scaduto() {
        // 24 hours and 30 minutes before: at 15:40 of the 10th the last one due is the 15:30,
        // even though the 16:00 of the day before (for the 11th) comes later in the search
        val due = quotidiano.copy(avvisi = listOf(PreAvviso(24, Unita.ORE), PreAvviso(30, Unita.MINUTI)))
        val perso = Avvisi.perso(due, dt("2026-11-10T15:00"), dt("2026-11-10T15:40"))!!
        assertEquals(dt("2026-11-10T15:30"), perso.quando)
        // at 16:05 the latest is the "24 hours before" of tomorrow's occurrence, due at 16:00
        val dopo = Avvisi.perso(due, dt("2026-11-10T15:00"), dt("2026-11-10T16:05"))!!
        assertEquals(dt("2026-11-10T16:00"), dopo.quando)
        assertEquals(dt("2026-11-11T16:00"), dopo.volta)
    }

    @Test
    fun in_ritardo_solo_da_un_minuto_in_su() {
        val avviso = Avviso(dt("2026-11-10T15:30"), "Tra 30 minuti, alle 16:00", dt("2026-11-10T16:00"))
        // Android was just about to deliver it: shown as itself
        assertFalse(Avvisi.inRitardo(avviso, dt("2026-11-10T15:30:40")))
        assertTrue(Avvisi.inRitardo(avviso, dt("2026-11-10T15:31")))
        assertTrue(Avvisi.inRitardo(avviso, dt("2026-11-10T15:50")))
    }

    @Test
    fun sveglie_solo_a_chi_aveva_ancora_qualcosa_da_venire() {
        val unaVolta = Promemoria(dt("2026-11-10T16:00").toLocalDate(), dt("2026-11-10T16:00").toLocalTime(), "Dentista")
        // at the bookmark it was still to come: its alarms may be in the queue
        assertTrue(Avvisi.puoAvereSveglie(unaVolta, dt("2026-11-10T15:00")))
        assertTrue(Avvisi.puoAvereSveglie(unaVolta, dt("2026-11-10T16:00")))
        // already past at the bookmark: all its alerts have been and gone
        assertFalse(Avvisi.puoAvereSveglie(unaVolta, dt("2026-11-10T16:01")))
        // switched off: never
        assertFalse(Avvisi.puoAvereSveglie(unaVolta.copy(attivo = false), dt("2026-11-10T15:00")))
        // one that repeats always has a next time
        assertTrue(Avvisi.puoAvereSveglie(quotidiano, dt("2030-01-01T00:00")))
        // unless the repetition is over
        assertFalse(Avvisi.puoAvereSveglie(quotidiano.copy(fino = dt("2026-11-30T00:00").toLocalDate()), dt("2026-12-01T00:00")))
    }
}
