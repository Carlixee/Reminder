package com.ac.reminder.dati

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** The sentences the code writes, in English. At the end it goes back to Italian for the other tests. */
class IngleseTest {

    @Before fun inglese() { Lingua.locale = Locale.ENGLISH }
    @After fun italiano() { Lingua.locale = Locale.ITALIAN }

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun numeri_con_la_coda() {
        val attesi = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th",
            13 to "13th", 21 to "21st", 22 to "22nd", 23 to "23rd", 31 to "31st")
        attesi.forEach { (n, testo) -> assertEquals(testo, Lingua.ordinaleInglese(n)) }
    }

    @Test
    fun ripetizioni() {
        val partenza = d("2026-11-06")
        assertEquals("Every day", Ripetizione.OgniGiorni(1).descrizione(partenza))
        assertEquals("Every 3 days", Ripetizione.OgniGiorni(3).descrizione(partenza))
        assertEquals("Every 2 weeks: Mon, Wed",
            Ripetizione.OgniSettimane(2, setOf(WEDNESDAY, MONDAY)).descrizione(partenza))
        assertEquals("Every month, on the 6th", Ripetizione.OgniMesi(1).descrizione(partenza))
        assertEquals("Every year, on 15 March", Ripetizione.OgniMesi(12).descrizione(d("2026-03-15")))
        assertEquals("Every month, on the second Thursday",
            Ripetizione.OgniMesiSettimana(1, 2, THURSDAY).descrizione(partenza))
        assertEquals("Every 2 months, on the last Sunday",
            Ripetizione.OgniMesiSettimana(2, -1, SUNDAY).descrizione(partenza))
    }

    @Test
    fun fino_a() {
        val p = Promemoria(d("2026-11-02"), LocalTime.of(9, 0), "Gym",
            ripetizione = Ripetizione.OgniSettimane(1, setOf(MONDAY)), fino = d("2026-11-23"))
        assertEquals("Every week: Mon · until 23 November", p.descrizioneRipetizione())
        assertEquals("Every week: Mon · until 8 January 2027", p.copy(fino = d("2027-01-08")).descrizioneRipetizione())
    }

    @Test
    fun avvisi_e_notifiche() {
        assertEquals("1 minute before", PreAvviso(1, Unita.MINUTI).etichetta())
        assertEquals("24 hours before", PreAvviso(24, Unita.ORE).etichetta())
        assertEquals("1 hour", PreAvviso(1, Unita.ORE).breve())
        assertEquals("10 min", PreAvviso(10, Unita.MINUTI).breve())
        val volta = d("2026-11-10").atTime(16, 0)   // a Tuesday
        assertEquals("In 30 minutes, at 16:00", Avvisi.testo(PreAvviso(30, Unita.MINUTI), volta))
        assertEquals("Tomorrow at 16:00", Avvisi.testo(PreAvviso(24, Unita.ORE), volta))
        assertEquals("In 2 days: Tuesday 10 November at 16:00", Avvisi.testo(PreAvviso(2, Unita.GIORNI), volta))
    }
}
