package com.ac.reminder.dati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** The alerts that fell while the phone was off (Android deletes the alarms at shutdown). */
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
}
