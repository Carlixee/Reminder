package com.ac.reminder.dati

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferitiTest {

    @Test
    fun andata_e_ritorno_dal_testo() {
        val lista = listOf(PreAvviso(10, Unita.MINUTI), PreAvviso(2, Unita.ORE), PreAvviso(1, Unita.SETTIMANE))
        assertEquals("10:MINUTI,2:ORE,1:SETTIMANE", Preferiti.aTesto(lista))
        assertEquals(lista, Preferiti.daTesto(Preferiti.aTesto(lista)))
    }

    @Test
    fun testo_vuoto_o_rovinato() {
        // empty = you removed them all
        assertEquals(emptyList<PreAvviso>(), Preferiti.daTesto(""))
        // broken or out of range pieces (60 minutes, 5 weeks) are thrown away, the others stay
        assertEquals(
            listOf(PreAvviso(3, Unita.GIORNI)),
            Preferiti.daTesto("60:MINUTI,abc,3:GIORNI,5:SETTIMANE,2:MESI,:ORE")
        )
    }

    @Test
    fun ordine_e_nomi_corti() {
        val ordinati = Preferiti.ordina(listOf(PreAvviso(1, Unita.GIORNI), PreAvviso(10, Unita.MINUTI), PreAvviso(1, Unita.GIORNI)))
        assertEquals(listOf(PreAvviso(10, Unita.MINUTI), PreAvviso(1, Unita.GIORNI)), ordinati)
        assertEquals("10 min", PreAvviso(10, Unita.MINUTI).breve())
        assertEquals("1 ora", PreAvviso(1, Unita.ORE).breve())
        assertEquals("2 settimane", PreAvviso(2, Unita.SETTIMANE).breve())
    }

    @Test
    fun tasti_rapidi_dentro_i_limiti() {
        for (u in Unita.values()) for (v in u.rapidi) assert(v in 1..u.massimo) { "$u: $v" }
    }
}
