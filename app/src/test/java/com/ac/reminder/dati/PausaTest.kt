package com.ac.reminder.dati

import com.ac.reminder.Formati
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** The pause of a reminder that repeats: "skip the next one" and "paused until…". */
class PausaTest {

    private fun dt(testo: String) = LocalDateTime.parse(testo)
    private fun g(testo: String) = LocalDate.parse(testo)

    /** Gym every Thursday at 18:00 (12, 19, 26 November 2026…), with "1 hour before". */
    private val palestra = Promemoria(
        g("2026-11-05"), dt("2026-11-05T18:00").toLocalTime(), "Palestra",
        ripetizione = Ripetizione.OgniSettimane(1, setOf(DayOfWeek.THURSDAY)),
        avvisi = listOf(PreAvviso(1, Unita.ORE))
    )

    @Test
    fun salta_la_prossima_volta() {
        val adesso = dt("2026-11-10T09:00")
        assertEquals(dt("2026-11-12T18:00"), palestra.prossimaVolta(adesso))
        // "skip the next one" = paused up to the day of the next occurrence
        val saltata = palestra.copy(pausa = g("2026-11-12"))
        assertEquals(dt("2026-11-19T18:00"), saltata.prossimaVolta(adesso))
        // and its alerts skip it too
        assertEquals(dt("2026-11-19T17:00"), Avvisi.prossimi(saltata, adesso).first().quando)
    }

    @Test
    fun in_pausa_fino_a_un_giorno_poi_riparte_da_sola() {
        val inFerie = palestra.copy(pausa = g("2026-11-25"))   // back on Thursday the 26th
        assertTrue(inFerie.inPausa(g("2026-11-10")))
        assertTrue(inFerie.inPausa(g("2026-11-25")))
        assertFalse(inFerie.inPausa(g("2026-11-26")))
        assertEquals(dt("2026-11-26T18:00"), inFerie.prossimaVolta(dt("2026-11-10T09:00")))
        // once the pause is over it counts for nothing: the reminder is as it was
        assertEquals(dt("2026-12-03T18:00"), inFerie.prossimaVolta(dt("2026-11-27T09:00")))
        assertEquals(
            Avvisi.prossimi(palestra, dt("2026-11-27T09:00")),
            Avvisi.prossimi(inFerie, dt("2026-11-27T09:00"))
        )
    }

    @Test
    fun le_volte_in_pausa_non_hanno_avvisi_persi() {
        val inFerie = palestra.copy(pausa = g("2026-11-25"))
        // phone off from the 11th to the 20th: the alerts of the 12th and the 19th were never due
        assertNull(Avvisi.perso(inFerie, dt("2026-11-11T09:00"), dt("2026-11-20T09:00")))
        // without the pause the one of the 19th would be missed
        assertEquals(dt("2026-11-19T17:00"), Avvisi.perso(palestra, dt("2026-11-11T09:00"), dt("2026-11-20T09:00"))!!.quando)
    }

    @Test
    fun chi_non_si_ripete_non_va_in_pausa() {
        val unaVolta = Promemoria(g("2026-11-12"), dt("2026-11-12T18:00").toLocalTime(), "Dentista", pausa = g("2026-11-30"))
        assertFalse(unaVolta.inPausa(g("2026-11-10")))
        assertEquals(dt("2026-11-12T18:00"), unaVolta.prossimaVolta(dt("2026-11-10T09:00")))
    }

    @Test
    fun la_pausa_non_scavalca_la_fine_della_ripetizione() {
        // it ends on the 20th and it's paused until the 25th: nothing is left
        assertNull(palestra.copy(fino = g("2026-11-20"), pausa = g("2026-11-25")).prossimaVolta(dt("2026-11-10T09:00")))
    }

    @Test
    fun la_lista_dice_fino_a_quando() {
        val inFerie = palestra.copy(pausa = g("2026-11-25"))
        assertEquals("Ogni settimana: gio · in pausa fino al 25 nov", Formati.ripetizioneEPausa(inFerie, g("2026-11-10")))
        assertEquals("Ogni settimana: gio", Formati.ripetizioneEPausa(inFerie, g("2026-11-26")))
        assertEquals("Ogni settimana: gio", Formati.ripetizioneEPausa(palestra, g("2026-11-10")))
    }
}
