package com.ac.reminder.dati

import com.ac.reminder.Formati
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The reminders without a time: they're for the whole day, and ring at the time chosen for the alert. */
class SenzaOrarioTest {

    private fun dt(testo: String) = LocalDateTime.parse(testo)

    /** "Bollo" on Saturday 14 November, no time: its alert rings at 09:00 of that day. */
    private val bollo = Promemoria(
        dt("2026-11-14T09:00").toLocalDate(), dt("2026-11-14T09:00").toLocalTime(), "Bollo",
        avvisi = emptyList(), senzaOrario = true
    )

    @Test
    fun resta_in_arrivo_per_tutto_il_giorno() {
        assertEquals(dt("2026-11-14T09:00"), bollo.prossimaVolta(dt("2026-11-13T22:00")))
        // the time of the alert has gone, but the day is still its day
        assertEquals(dt("2026-11-14T09:00"), bollo.prossimaVolta(dt("2026-11-14T23:59")))
        assertNull(bollo.prossimaVolta(dt("2026-11-15T00:00")))
        // with a time it would already be past at 09:01
        assertNull(bollo.copy(senzaOrario = false).prossimaVolta(dt("2026-11-14T09:01")))
    }

    @Test
    fun ogni_giorno_passa_a_domani_solo_a_mezzanotte() {
        val quotidiano = bollo.copy(ripetizione = Ripetizione.OgniGiorni(1))
        assertEquals(dt("2026-11-14T09:00"), quotidiano.prossimaVolta(dt("2026-11-14T18:00")))
        assertEquals(dt("2026-11-15T09:00"), quotidiano.prossimaVolta(dt("2026-11-15T00:00")))
    }

    @Test
    fun suona_il_giorno_stesso_anche_senza_altri_avvisi() {
        val avvisi = Avvisi.prossimi(bollo, dt("2026-11-10T12:00"))
        assertEquals(1, avvisi.size)
        assertEquals(dt("2026-11-14T09:00"), avvisi[0].quando)
        assertEquals("Oggi", avvisi[0].testo)
        // once it has rung, there's nothing else
        assertTrue(Avvisi.prossimi(bollo, dt("2026-11-14T09:00")).isEmpty())
        assertEquals(listOf(dt("2026-11-14T09:00")), Avvisi.diUnaVolta(bollo, dt("2026-11-14T09:00"), dt("2026-11-14T08:00")))
    }

    @Test
    fun gli_avvisi_prima_sono_a_giorni_interi_e_non_dicono_l_ora() {
        val conAvvisi = bollo.copy(avvisi = listOf(PreAvviso(3, Unita.GIORNI), PreAvviso(24, Unita.ORE)))
        val avvisi = Avvisi.prossimi(conAvvisi, dt("2026-11-10T12:00"))
        assertEquals(
            listOf(dt("2026-11-11T09:00"), dt("2026-11-13T09:00"), dt("2026-11-14T09:00")),
            avvisi.map { it.quando }
        )
        assertEquals("Tra 3 giorni: sabato 14 novembre", avvisi[0].testo)
        assertEquals("Domani", avvisi[1].testo)
        assertEquals("Oggi", avvisi[2].testo)
    }

    @Test
    fun con_l_orario_i_testi_restano_quelli_di_prima() {
        val volta = dt("2026-11-14T16:00")
        assertEquals("Tra 30 minuti, alle 16:00", Avvisi.testo(PreAvviso(30, Unita.MINUTI), volta))
        assertEquals("Tra 48 ore, alle 16:00", Avvisi.testo(PreAvviso(48, Unita.ORE), volta))
        assertEquals("Domani alle 16:00", Avvisi.testo(PreAvviso(24, Unita.ORE), volta))
        assertEquals("Domani alle 16:00", Avvisi.testo(PreAvviso(1, Unita.GIORNI), volta))
        assertEquals("Tra 3 giorni: sabato 14 novembre alle 16:00", Avvisi.testo(PreAvviso(3, Unita.GIORNI), volta))
        assertEquals("Adesso, alle 16:00", Avvisi.testoAllOra(volta))
    }

    @Test
    fun l_avviso_del_giorno_puo_essere_perso() {
        val perso = Avvisi.perso(bollo, dt("2026-11-14T08:00"), dt("2026-11-14T10:00"))!!
        assertEquals(dt("2026-11-14T09:00"), perso.quando)
        assertEquals("Oggi", perso.testo)
        assertNull(Avvisi.perso(bollo, dt("2026-11-14T09:30"), dt("2026-11-14T10:00")))
    }

    @Test
    fun non_gli_serve_l_avviso_all_ora_esatta() {
        // it rings at that time anyway: nothing to add when the alerts before have already gone
        val conAvviso = bollo.copy(avvisi = listOf(PreAvviso(1, Unita.GIORNI)))
        assertNull(Avvisi.daSuonareAllOra(conAvviso, dt("2026-11-14T08:00")))
    }

    @Test
    fun solo_gli_avvisi_a_giorni_interi() {
        assertTrue(PreAvviso(1, Unita.GIORNI).giorniInteri)
        assertTrue(PreAvviso(2, Unita.SETTIMANE).giorniInteri)
        assertTrue(PreAvviso(24, Unita.ORE).giorniInteri)
        assertFalse(PreAvviso(12, Unita.ORE).giorniInteri)
        assertFalse(PreAvviso(30, Unita.MINUTI).giorniInteri)
    }

    @Test
    fun i_testi_senza_ora_e_la_nota_in_una_riga() {
        val adesso = dt("2026-11-11T23:31")
        assertEquals("oggi", Formati.quandoBreve(adesso, dt("2026-11-11T09:00"), conOra = false))
        assertEquals("sab 14/11", Formati.quandoBreve(adesso, dt("2026-11-14T09:00"), conOra = false))
        assertEquals("oggi", Formati.momento(adesso, dt("2026-11-11T09:00"), conOra = false))
        assertEquals("domani", Formati.momento(adesso, dt("2026-11-12T09:00"), conOra = false))
        assertEquals("sabato", Formati.momento(adesso, dt("2026-11-14T09:00"), conOra = false))
        assertEquals("latte · pane", Formati.notaInRiga("latte\n pane\n"))
        assertEquals("Via Roma 3", Formati.notaInRiga("Via Roma 3"))
    }
}
