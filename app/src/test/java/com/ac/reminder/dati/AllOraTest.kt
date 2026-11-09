package com.ac.reminder.dati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Alerts already past when saving: it rings at the exact time, only that occurrence. */
class AllOraTest {

    private fun dt(testo: String) = LocalDateTime.parse(testo)

    /** A reminder at 16:00 on 10 November with "30 minutes before" (and more, if needed). */
    private fun alle16(vararg avvisi: PreAvviso, ripetizione: Ripetizione = Ripetizione.Nessuna) =
        Promemoria(dt("2026-11-10T16:00").toLocalDate(), dt("2026-11-10T16:00").toLocalTime(), "Prova",
            ripetizione = ripetizione, avvisi = avvisi.toList())

    @Test
    fun avvisi_tutti_passati_suona_all_ora() {
        val p = alle16(PreAvviso(30, Unita.MINUTI))
        // at 15:50 the 15:30 alert has already passed
        assertEquals(dt("2026-11-10T16:00"), Avvisi.daSuonareAllOra(p, dt("2026-11-10T15:50")))
        // at 15:00 not: the 15:30 alert still has to ring
        assertNull(Avvisi.daSuonareAllOra(p, dt("2026-11-10T15:00")))
        // no alert chosen: it stays silent, as whoever wrote it decided
        assertNull(Avvisi.daSuonareAllOra(alle16(), dt("2026-11-10T15:50")))
    }

    @Test
    fun basta_un_avviso_ancora_da_suonare() {
        // 24 hours before has passed, 5 minutes before (15:55) not: no exact time
        val p = alle16(PreAvviso(24, Unita.ORE), PreAvviso(5, Unita.MINUTI))
        assertNull(Avvisi.daSuonareAllOra(p, dt("2026-11-10T15:50")))
    }

    @Test
    fun la_sveglia_c_e_e_poi_sparisce() {
        val p = alle16(PreAvviso(30, Unita.MINUTI)).let { it.copy(allOra = Avvisi.daSuonareAllOra(it, dt("2026-11-10T15:50"))) }
        val avvisi = Avvisi.prossimi(p, dt("2026-11-10T15:50"))
        assertEquals(listOf(dt("2026-11-10T16:00")), avvisi.map { it.quando })
        assertEquals("Adesso, alle 16:00", avvisi[0].testo)
        // after it has rung (the chain restarts from 16:00) it doesn't repeat
        assertTrue(Avvisi.prossimi(p, dt("2026-11-10T16:00")).isEmpty())
    }

    @Test
    fun chi_si_ripete_torna_normale_la_volta_dopo() {
        val p = alle16(PreAvviso(30, Unita.MINUTI), ripetizione = Ripetizione.OgniGiorni(1))
            .let { it.copy(allOra = Avvisi.daSuonareAllOra(it, dt("2026-11-10T15:50"))) }
        val avvisi = Avvisi.prossimi(p, dt("2026-11-10T15:50"), quanti = 3).map { it.quando }
        // today at the exact time, then the following days only "30 minutes before"
        assertEquals(listOf(dt("2026-11-10T16:00"), dt("2026-11-11T15:30"), dt("2026-11-12T15:30")), avvisi)
    }

    @Test
    fun la_scheda_blu_la_mostra_tra_gli_avvisi() {
        val p = alle16(PreAvviso(30, Unita.MINUTI)).copy(allOra = dt("2026-11-10T16:00"))
        assertEquals(listOf(dt("2026-11-10T16:00")), Avvisi.diUnaVolta(p, dt("2026-11-10T16:00"), dt("2026-11-10T15:50")))
    }

    @Test
    fun anno_nel_testo_solo_a_cavallo_di_capodanno() {
        val capodanno = dt("2027-01-02T10:00")
        // the alert arrives on 26 December 2026, for 2 January: the year is needed
        assertEquals("Tra 1 settimana: sabato 2 gennaio 2027 alle 10:00", Avvisi.testo(PreAvviso(1, Unita.SETTIMANE), capodanno))
        // does the alert arrive on 31 December? no: "2 days" before is the 31st, still 2026 → year yes
        assertEquals("Tra 2 giorni: sabato 2 gennaio 2027 alle 10:00", Avvisi.testo(PreAvviso(2, Unita.GIORNI), capodanno))
        // same year: nothing
        assertEquals("Tra 2 giorni: martedì 10 novembre alle 16:00", Avvisi.testo(PreAvviso(2, Unita.GIORNI), dt("2026-11-10T16:00")))
    }
}
