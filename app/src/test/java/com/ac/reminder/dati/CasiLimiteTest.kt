package com.ac.reminder.dati

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate

/** The awkward dates: end of month, 29 February, change of year, "the last Friday". */
class CasiLimiteTest {

    private fun d(testo: String) = LocalDate.parse(testo)

    @Test
    fun ogni_mese_dal_31_non_scivola() {
        val r = Ripetizione.OgniMesi(1)   // starts on 31 January: the day is the starting one
        assertEquals(d("2027-02-28"), r.primaVoltaDa(d("2027-01-31"), d("2027-02-01")))
        // after February (28) it goes back to the 31st, it doesn't stay at the 28th
        assertEquals(d("2027-03-31"), r.primaVoltaDa(d("2027-01-31"), d("2027-03-01")))
        assertEquals(d("2027-04-30"), r.primaVoltaDa(d("2027-01-31"), d("2027-04-01")))
    }

    @Test
    fun ogni_anno_il_29_febbraio() {
        val r = Ripetizione.OgniMesi(12)
        assertEquals(d("2029-02-28"), r.primaVoltaDa(d("2028-02-29"), d("2028-03-01")))
        assertEquals(d("2032-02-29"), r.primaVoltaDa(d("2028-02-29"), d("2031-03-01")))
    }

    @Test
    fun ogni_due_settimane_a_cavallo_dell_anno() {
        val r = Ripetizione.OgniSettimane(2, setOf(MONDAY))   // 28/12/2026 is a Monday
        assertEquals(d("2027-01-11"), r.primaVoltaDa(d("2026-12-28"), d("2027-01-01")))
    }

    @Test
    fun l_ultimo_venerdi_del_mese() {
        val r = Ripetizione.OgniMesiSettimana(1, -1, FRIDAY)
        assertEquals(d("2026-11-27"), r.primaVoltaDa(d("2026-11-27"), d("2026-11-27")))
        assertEquals(d("2026-12-25"), r.primaVoltaDa(d("2026-11-27"), d("2026-11-28")))
    }

    @Test
    fun fine_della_ripetizione_compresa() {
        // "until the 23rd" means that the 23rd still rings, the 30th not
        val base = Promemoria(d("2026-11-02"), java.time.LocalTime.of(9, 0), "Palestra",
            ripetizione = Ripetizione.OgniSettimane(1, setOf(MONDAY)), fino = d("2026-11-23"))
        assertEquals(d("2026-11-23").atTime(9, 0), base.prossimaVolta(d("2026-11-17").atTime(10, 0)))
        assertEquals(null, base.prossimaVolta(d("2026-11-23").atTime(9, 1)))
    }

    @Test
    fun tante_sveglie_mai_oltre_il_tetto() {
        assertEquals(5, Avvisi.quantiPerOgnuno(0))
        assertEquals(5, Avvisi.quantiPerOgnuno(80))     // 80 × 5 = 400: they all fit
        assertEquals(4, Avvisi.quantiPerOgnuno(81))
        assertEquals(1, Avvisi.quantiPerOgnuno(400))
        assertEquals(1, Avvisi.quantiPerOgnuno(1000))   // never zero: at least the next one
    }
}
