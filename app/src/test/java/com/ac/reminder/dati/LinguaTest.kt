package com.ac.reminder.dati

import com.ac.reminder.Formati
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

/** Small pieces of text the code writes in both languages: "which one in the month", the tile's line. */
class LinguaTest {

    @After fun italiano() { Lingua.locale = Locale.ITALIAN }

    private fun dt(testo: String) = LocalDateTime.parse(testo)

    @Test
    fun quale_nel_mese_in_italiano() {
        assertEquals("primo", Lingua.ordinale(1))
        assertEquals("seconda", Lingua.ordinale(2, femminile = true))
        assertEquals("terzo", Lingua.ordinale(3))
        assertEquals("quarta", Lingua.ordinale(4, femminile = true))
        assertEquals("ultimo", Lingua.ordinale(-1))
        assertEquals("ultima", Lingua.ordinale(-1, femminile = true))
    }

    @Test
    fun quale_nel_mese_in_inglese() {
        Lingua.locale = Locale.ENGLISH
        assertEquals("first", Lingua.ordinale(1))
        assertEquals("fourth", Lingua.ordinale(4, femminile = true))
        assertEquals("last", Lingua.ordinale(-1))
    }

    @Test
    fun la_riga_sotto_al_riquadro_ha_sempre_la_data() {
        val adesso = dt("2026-11-11T23:31")
        assertEquals("oggi 23:45", Formati.quandoBreve(adesso, dt("2026-11-11T23:45")))
        // tomorrow and two weeks from now are both a Thursday: the date tells them apart
        assertEquals("gio 12/11 15:00", Formati.quandoBreve(adesso, dt("2026-11-12T15:00")))
        assertEquals("gio 26/11 15:00", Formati.quandoBreve(adesso, dt("2026-11-26T15:00")))
        Lingua.locale = Locale.ENGLISH
        assertEquals("today 23:45", Formati.quandoBreve(adesso, dt("2026-11-11T23:45")))
        assertEquals("Thu 12/11 15:00", Formati.quandoBreve(adesso, dt("2026-11-12T15:00")))
    }
}
