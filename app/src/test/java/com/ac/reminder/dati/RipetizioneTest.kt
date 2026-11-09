package com.ac.reminder.dati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Tests of the repetitions: dates only, runs on the PC in a second. */
class RipetizioneTest {

    private fun d(testo: String) = LocalDate.parse(testo)
    private fun dt(testo: String) = LocalDateTime.parse(testo)

    @Test
    fun nessuna_cade_solo_il_suo_giorno() {
        val r = Ripetizione.Nessuna
        assertEquals(d("2026-11-13"), r.primaVoltaDa(d("2026-11-13"), d("2026-11-06")))
        assertNull(r.primaVoltaDa(d("2026-11-13"), d("2026-11-14")))
    }

    @Test
    fun ogni_3_giorni() {
        val r = Ripetizione.OgniGiorni(3)   // 1, 4, 7, 10 November…
        assertEquals(d("2026-11-07"), r.primaVoltaDa(d("2026-11-01"), d("2026-11-06")))
        assertEquals(d("2026-11-04"), r.primaVoltaDa(d("2026-11-01"), d("2026-11-04")))
        // before the start it never falls
        assertEquals(d("2026-11-01"), r.primaVoltaDa(d("2026-11-01"), d("2026-10-20")))
    }

    @Test
    fun ogni_settimana_lun_mer_ven() {
        val r = Ripetizione.OgniSettimane(1, setOf(MONDAY, WEDNESDAY, FRIDAY))
        // start Friday 6/11: from Saturday 7 the next one is Monday 9
        assertEquals(d("2026-11-09"), r.primaVoltaDa(d("2026-11-06"), d("2026-11-07")))
        assertEquals(d("2026-11-11"), r.primaVoltaDa(d("2026-11-06"), d("2026-11-10")))
        // the very day of the start, if it's among the chosen ones
        assertEquals(d("2026-11-06"), r.primaVoltaDa(d("2026-11-06"), d("2026-11-06")))
    }

    @Test
    fun ogni_2_settimane_il_martedi() {
        val r = Ripetizione.OgniSettimane(2, setOf(TUESDAY))   // 10/11, 24/11, 8/12…
        assertEquals(d("2026-11-24"), r.primaVoltaDa(d("2026-11-10"), d("2026-11-11")))
        assertEquals(d("2026-12-08"), r.primaVoltaDa(d("2026-11-10"), d("2026-11-25")))
    }

    @Test
    fun partenza_in_un_giorno_non_scelto() {
        // start on Friday, but it only repeats on Monday: the first time is Monday 9
        val r = Ripetizione.OgniSettimane(1, setOf(MONDAY))
        assertEquals(d("2026-11-09"), r.primaVoltaDa(d("2026-11-06"), d("2026-11-06")))
    }

    @Test
    fun ogni_mese_il_31() {
        val r = Ripetizione.OgniMesi(1)
        assertEquals(d("2026-02-28"), r.primaVoltaDa(d("2026-01-31"), d("2026-02-01")))
        // after a short month it goes back to the 31st, it doesn't stay at the 28th
        assertEquals(d("2026-03-31"), r.primaVoltaDa(d("2026-01-31"), d("2026-03-01")))
        assertEquals(d("2028-02-29"), r.primaVoltaDa(d("2026-01-31"), d("2028-02-01")))   // leap year
    }

    @Test
    fun ogni_12_mesi_e_ogni_anno() {
        val r = Ripetizione.OgniMesi(12)
        assertEquals(d("2027-03-15"), r.primaVoltaDa(d("2026-03-15"), d("2026-03-16")))
        assertEquals("Ogni anno, il 15 marzo", r.descrizione(d("2026-03-15")))
    }

    @Test
    fun ogni_mese_un_giorno_scelto() {
        // starts on 20/11 but the chosen day is the 15th: November is already gone, it goes to December
        assertEquals(d("2026-12-15"), Ripetizione.OgniMesi(1, 15).primaVoltaDa(d("2026-11-20"), d("2026-11-20")))
        // the 31st chosen, November has 30
        assertEquals(d("2026-11-30"), Ripetizione.OgniMesi(1, 31).primaVoltaDa(d("2026-11-06"), d("2026-11-06")))
    }

    @Test
    fun ogni_mese_il_secondo_giovedi_e_l_ultimo_venerdi() {
        val secondoGiovedi = Ripetizione.OgniMesiSettimana(1, 2, THURSDAY)
        assertEquals(d("2026-11-12"), secondoGiovedi.primaVoltaDa(d("2026-11-06"), d("2026-11-06")))
        assertEquals(d("2026-12-10"), secondoGiovedi.primaVoltaDa(d("2026-11-06"), d("2026-11-13")))
        assertEquals("Ogni mese, il secondo giovedì", secondoGiovedi.descrizione(d("2026-11-06")))

        val ultimoVenerdi = Ripetizione.OgniMesiSettimana(1, -1, FRIDAY)
        assertEquals(d("2026-11-27"), ultimoVenerdi.primaVoltaDa(d("2026-11-06"), d("2026-11-07")))
        assertEquals("Ogni mese, l'ultima domenica",
            Ripetizione.OgniMesiSettimana(1, -1, SUNDAY).descrizione(d("2026-11-06")))
        assertEquals("Ogni 2 mesi, la prima domenica",
            Ripetizione.OgniMesiSettimana(2, 1, SUNDAY).descrizione(d("2026-11-06")))
    }

    @Test
    fun descrizioni() {
        val partenza = d("2026-11-06")
        assertEquals("Ogni giorno", Ripetizione.OgniGiorni(1).descrizione(partenza))
        assertEquals("Ogni 2 settimane: lun, mer",
            Ripetizione.OgniSettimane(2, setOf(WEDNESDAY, MONDAY)).descrizione(partenza))
        assertEquals("Ogni mese, il giorno 6", Ripetizione.OgniMesi(1).descrizione(partenza))
    }

    @Test
    fun prossima_volta_salta_l_ora_gia_passata() {
        val p = Promemoria(d("2026-11-01"), LocalTime.of(10, 0), "Palestra",
            ripetizione = Ripetizione.OgniGiorni(1))
        assertEquals(dt("2026-11-06T10:00"), p.prossimaVolta(dt("2026-11-06T09:00")))
        assertEquals(dt("2026-11-07T10:00"), p.prossimaVolta(dt("2026-11-06T11:00")))

        val unaVolta = Promemoria(d("2026-11-06"), LocalTime.of(10, 0), "Visita")
        assertNull(unaVolta.prossimaVolta(dt("2026-11-06T10:01")))   // past: it goes into the archive
    }

    @Test
    fun avvisi_predefiniti_24_ore_e_30_minuti() {
        val p = Promemoria(d("2026-11-07"), LocalTime.of(10, 0), "Visita")
        val avvisi = Avvisi.prossimi(p, dt("2026-11-06T08:00"))
        assertEquals(listOf(dt("2026-11-06T10:00"), dt("2026-11-07T09:30")), avvisi.map { it.quando })
        assertEquals("Domani alle 10:00", avvisi[0].testo)
        assertEquals("Tra 30 minuti, alle 10:00", avvisi[1].testo)
        // the last one rang, nothing is left
        assertEquals(0, Avvisi.prossimi(p, dt("2026-11-07T09:30")).size)
    }

    @Test
    fun la_catena_di_chi_si_ripete() {
        val p = Promemoria(d("2026-11-01"), LocalTime.of(10, 0), "Palestra",
            ripetizione = Ripetizione.OgniGiorni(1))
        // the "30 minutes before" of the 6th has just rung: it goes on with the following occurrences
        val avvisi = Avvisi.prossimi(p, dt("2026-11-06T09:30"))
        assertEquals(
            listOf(dt("2026-11-06T10:00"), dt("2026-11-07T09:30"), dt("2026-11-07T10:00"),
                dt("2026-11-08T09:30"), dt("2026-11-08T10:00")),
            avvisi.map { it.quando }
        )
    }

    @Test
    fun avviso_lontano_su_chi_si_ripete_spesso() {
        // every day at 10, alerts "2 weeks before" and "30 minutes before":
        // on the 7th at 10:00 the "2 weeks before" of the 21st's occurrence rings
        val p = Promemoria(d("2026-11-01"), LocalTime.of(10, 0), "Pastiglia",
            ripetizione = Ripetizione.OgniGiorni(1),
            avvisi = listOf(PreAvviso(2, Unita.SETTIMANE), PreAvviso(30, Unita.MINUTI)))
        val avvisi = Avvisi.prossimi(p, dt("2026-11-06T12:00"))
        assertEquals(dt("2026-11-07T09:30"), avvisi[0].quando)
        assertEquals(dt("2026-11-07T10:00"), avvisi[1].quando)
        assertEquals("Tra 2 settimane: sabato 21 novembre alle 10:00", avvisi[1].testo)
    }

    @Test
    fun due_avvisi_allo_stesso_minuto_diventano_uno() {
        val p = Promemoria(d("2026-11-10"), LocalTime.of(16, 0), "Ethical",
            avvisi = listOf(PreAvviso(24, Unita.ORE), PreAvviso(1, Unita.GIORNI)))
        assertEquals(1, Avvisi.prossimi(p, dt("2026-11-06T12:00")).size)
    }

    @Test
    fun etichette_degli_avvisi() {
        assertEquals("1 minuto prima", PreAvviso(1, Unita.MINUTI).etichetta())
        assertEquals("24 ore prima", PreAvviso(24, Unita.ORE).etichetta())
        assertEquals("1 settimana prima", PreAvviso(1, Unita.SETTIMANE).etichetta())
        assertEquals("Tra 3 ore, alle 16:00", Avvisi.testo(PreAvviso(3, Unita.ORE), dt("2026-11-10T16:00")))
    }

    @Test
    fun fino_al_giorno() {
        // Every Monday from 9 November, until the 23rd (included): 9, 16, 23 and then stop
        val p = Promemoria(
            d("2026-11-09"), LocalTime.of(10, 0), "Corso",
            ripetizione = Ripetizione.OgniSettimane(1, setOf(MONDAY)), fino = d("2026-11-23")
        )
        assertEquals(dt("2026-11-23T10:00"), p.prossimaVolta(dt("2026-11-17T08:00")))
        assertNull(p.prossimaVolta(dt("2026-11-23T11:00")))            // over: it goes among the past ones
        assertEquals(dt("2026-11-23T10:00"), p.ultimaVolta())          // and shows with its last occurrence
        // The alerts stop there: 24 hours and 30 minutes before 9, 16 and 23 = 6, not 5 "endless" after
        val tutti = Avvisi.prossimi(p, dt("2026-11-01T00:00"), quanti = 50)
        assertEquals(6, tutti.size)
        assertEquals(dt("2026-11-23T09:30"), tutti.last().quando)
        assertEquals("Ogni settimana: lun · fino al 23 novembre", p.descrizioneRipetizione())
        assertEquals("Ogni settimana: lun · fino all'8 gennaio 2027",
            p.copy(fino = d("2027-01-08")).descrizioneRipetizione())
    }
}
