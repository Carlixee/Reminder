package com.ac.reminder

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.ac.reminder.dati.Promemoria
import java.time.LocalDateTime

/** A reminder ready to show: where it is in the file, and when it "falls". */
data class RigaDati(
    val indice: Int,
    val promemoria: Promemoria,
    val quando: LocalDateTime,
    val passato: Boolean
)

/**
 * The list's items, from top to bottom:
 * PAST → past rows → TODAY bookmark → blue card → future rows.
 * (`primo` / `ultimo` keep the timeline from sticking out beyond the ends.)
 */
sealed class Voce {
    object TestataPassati : Voce()
    data class Passato(val riga: RigaDati, val primo: Boolean) : Voce()
    data class Oggi(val testo: String) : Voce()
    data class Prossimo(val riga: RigaDati?, val conCoda: Boolean) : Voce()
    data class Futuro(val riga: RigaDati, val primo: Boolean, val ultimo: Boolean) : Voce()
    /** The change of year: "2027". `conLinea` = the timeline runs through it. */
    data class Anno(val anno: Int, val passato: Boolean, val conLinea: Boolean) : Voce()
    data class Vuoto(val testo: String) : Voce()
}

class PromemoriaAdapter(
    private val allaPressioneLunga: (RigaDati) -> Unit,
    private val allaPressioneLungaPassato: (RigaDati) -> Unit,
    private val eliminaPassati: () -> Unit,
    /** Normal tap on a reminder (true = past): the hint says to press and hold. */
    private val alTocco: (Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var voci: List<Voce> = emptyList()
    private var adesso: LocalDateTime = LocalDateTime.now()

    /** The two font weights that change between past and future (loaded only once). */
    private var semigrassetto: Typeface? = null
    private var grassetto: Typeface? = null

    private class Contenitore(vista: View) : RecyclerView.ViewHolder(vista)

    fun aggiorna(nuove: List<Voce>, adesso: LocalDateTime) {
        voci = nuove
        this.adesso = adesso
        notifyDataSetChanged()
    }

    fun posizioneDi(cerca: (Voce) -> Boolean) = voci.indexOfFirst(cerca)

    /** The reminder shown at that position (null for titles, bookmark, labels). */
    fun rigaIn(posizione: Int): RigaDati? = rigaDi(voci.getOrNull(posizione))

    /** Where reminder number `indice` of the file is now (-1 if it isn't in the list). */
    fun posizioneDiRiga(indice: Int) = voci.indexOfFirst { rigaDi(it)?.indice == indice }

    private fun rigaDi(voce: Voce?): RigaDati? = when (voce) {
        is Voce.Passato -> voce.riga
        is Voce.Futuro -> voce.riga
        is Voce.Prossimo -> voce.riga
        else -> null
    }

    override fun getItemCount() = voci.size

    override fun getItemViewType(posizione: Int) = when (voci[posizione]) {
        Voce.TestataPassati -> R.layout.riga_sezione
        is Voce.Passato, is Voce.Futuro -> R.layout.riga_timeline
        is Voce.Oggi -> R.layout.riga_oggi
        is Voce.Anno -> R.layout.riga_anno
        is Voce.Prossimo -> R.layout.riga_prossimo
        is Voce.Vuoto -> 0
    }

    override fun onCreateViewHolder(genitore: ViewGroup, tipo: Int): RecyclerView.ViewHolder {
        if (semigrassetto == null) {
            semigrassetto = ResourcesCompat.getFont(genitore.context, R.font.jakarta_semibold)
            grassetto = ResourcesCompat.getFont(genitore.context, R.font.jakarta_bold)
        }
        val vista = if (tipo == 0) {
            // "No reminders found": a label is enough, no layout file
            TextView(genitore.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 48), 0, dp(context, 48))
                setTextColor(colore(context, R.color.testo_secondario))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }
        } else {
            LayoutInflater.from(genitore.context).inflate(tipo, genitore, false).apply {
                // Large text: the time goes under the title, "in 3 days" under the label
                if (context.testoGrande()) {
                    findViewById<LinearLayout>(R.id.rigaTitolo)?.impila(2)
                    findViewById<LinearLayout>(R.id.rigaEtichetta)?.impila(8)
                }
            }
        }
        return Contenitore(vista)
    }

    override fun onBindViewHolder(contenitore: RecyclerView.ViewHolder, posizione: Int) {
        val v = contenitore.itemView
        when (val voce = voci[posizione]) {
            Voce.TestataPassati ->
                v.findViewById<View>(R.id.bottoneEliminaPassati).setOnClickListener { eliminaPassati() }
            is Voce.Passato -> legaRiga(v, voce.riga, passato = true, lineaSopra = !voce.primo, lineaSotto = true)
            is Voce.Futuro -> legaRiga(v, voce.riga, passato = false, lineaSopra = !voce.primo, lineaSotto = !voce.ultimo)
            is Voce.Oggi -> v.findViewById<TextView>(R.id.testoOggi).text = voce.testo
            is Voce.Anno -> legaAnno(v, voce)
            is Voce.Prossimo -> legaProssimo(v, voce)
            is Voce.Vuoto -> (v as TextView).text = voce.testo
        }
    }

    /** The year separator: fainter among the past ones, like their rows. */
    private fun legaAnno(v: View, voce: Voce.Anno) {
        val c = v.context
        val tintaLinea = if (voce.passato) R.color.linea_passata else R.color.linea_futura
        v.findViewById<TextView>(R.id.testoAnno).apply {
            text = voce.anno.toString()
            setTextColor(colore(c, if (voce.passato) R.color.testo_tenue else R.color.testo_secondario))
        }
        v.findViewById<View>(R.id.lineaAnno).apply {
            setBackgroundResource(tintaLinea)
            visibility = if (voce.conLinea) View.VISIBLE else View.INVISIBLE
        }
        v.findViewById<View>(R.id.rigaAnno).setBackgroundResource(tintaLinea)
    }

    /**
     * A row of the timeline. The past ones are "just text":
     * empty dot, card with only the border, fainter texts, and they can't be tapped.
     */
    private fun legaRiga(v: View, riga: RigaDati, passato: Boolean, lineaSopra: Boolean, lineaSotto: Boolean) {
        val c = v.context
        val p = riga.promemoria
        val giorno = riga.quando.toLocalDate()

        v.findViewById<TextView>(R.id.testoGiorno).text = Formati.giornoCorto(giorno)
        v.findViewById<TextView>(R.id.testoData).apply {
            text = Formati.dataBreve(giorno)
            setTextColor(colore(c, if (passato) R.color.testo_tenue else R.color.testo_principale))
        }

        val tintaLinea = if (passato) R.color.linea_passata else R.color.linea_futura
        v.findViewById<View>(R.id.lineaSopra).apply {
            setBackgroundResource(tintaLinea)
            visibility = if (lineaSopra) View.VISIBLE else View.INVISIBLE
        }
        v.findViewById<View>(R.id.lineaSotto).apply {
            setBackgroundResource(tintaLinea)
            visibility = if (lineaSotto) View.VISIBLE else View.INVISIBLE
        }
        v.findViewById<View>(R.id.punto)
            .setBackgroundResource(if (passato) R.drawable.punto_passato else R.drawable.punto_futuro)

        val scheda = v.findViewById<View>(R.id.scheda)
        scheda.setBackgroundResource(if (passato) R.drawable.fondo_scheda_passata else R.drawable.fondo_scheda)
        val verticale = dp(c, if (passato) 11 else 12)
        scheda.setPadding(dp(c, 14), verticale, dp(c, 14), verticale)

        v.findViewById<TextView>(R.id.testoTitolo).apply {
            text = p.titolo
            typeface = if (passato) semigrassetto else grassetto
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (passato) 15f else 16f)
            setTextColor(colore(c, if (passato) R.color.testo_secondario else R.color.testo_principale))
        }
        v.findViewById<TextView>(R.id.testoOra).apply {
            isVisible = !p.senzaOrario   // without a time there's only the day, in the column on the left
            text = Formati.ora(riga.quando.toLocalTime())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (passato) 13f else 14f)
            setTextColor(colore(c, if (passato) R.color.testo_tenue else R.color.testo_secondario))
        }
        v.findViewById<TextView>(R.id.testoNota).apply {
            isVisible = p.nota.isNotEmpty()
            text = Formati.notaInRiga(p.nota)
            setTextColor(colore(c, if (passato) R.color.testo_tenue else R.color.testo_secondario))
        }
        v.findViewById<TextView>(R.id.testoRipeti).apply {
            isVisible = p.siRipete
            if (p.siRipete) text = Formati.ripetizioneEPausa(p, adesso.toLocalDate())
            val tinta = colore(c, if (passato) R.color.testo_tenue else R.color.azzurro_chiaro)
            setTextColor(tinta)
            TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(tinta))
        }

        // Alerts off: crossed-out bell and veiled card
        val spento = !passato && !p.attivo
        v.findViewById<View>(R.id.iconaSpenta).isVisible = spento
        scheda.alpha = if (spento) 0.6f else 1f

        // Press and hold: upcoming reminders get edited, past ones can only be deleted.
        // A normal tap reminds you how it's done.
        scheda.setOnLongClickListener {
            if (passato) allaPressioneLungaPassato(riga) else allaPressioneLunga(riga)
            true
        }
        scheda.setOnClickListener { alTocco(passato) }
    }

    /** The blue card at the top of the upcoming ones (or, if there's nothing, the invitation to create one). */
    private fun legaProssimo(v: View, voce: Voce.Prossimo) {
        val c = v.context
        val titolo = v.findViewById<TextView>(R.id.testoTitolo)
        val quando = v.findViewById<TextView>(R.id.testoQuando)
        val traQuanto = v.findViewById<TextView>(R.id.testoTraQuanto)
        val ripeti = v.findViewById<TextView>(R.id.testoRipeti)
        val avvisi = v.findViewById<TextView>(R.id.testoAvvisi)
        val nota = v.findViewById<TextView>(R.id.testoNota)
        val scheda = v.findViewById<View>(R.id.schedaProssimo)
        v.findViewById<View>(R.id.coda).isVisible = voce.conCoda

        val riga = voce.riga
        if (riga == null) {
            titolo.setText(R.string.vuoto_titolo)
            quando.setText(R.string.vuoto_testo)
            nota.isVisible = false
            traQuanto.isVisible = false
            ripeti.isVisible = false
            avvisi.isVisible = false
            scheda.setOnLongClickListener(null)
            scheda.isLongClickable = false
            scheda.setOnClickListener(null)
            scheda.isClickable = false
            return
        }

        val p = riga.promemoria
        titolo.text = p.titolo
        quando.text = Formati.quandoLungo(riga.quando, conOra = !p.senzaOrario)
        nota.isVisible = p.nota.isNotEmpty()   // here the note is whole: it's the next thing to do
        nota.text = p.nota
        traQuanto.isVisible = true
        traQuanto.text = Formati.traQuanto(c, adesso, riga.quando, conOra = !p.senzaOrario)
        ripeti.isVisible = p.siRipete
        if (p.siRipete) ripeti.text = Formati.ripetizioneEPausa(p, adesso.toLocalDate())
        avvisi.isVisible = true
        avvisi.text = Formati.rigaAvvisi(c, p, riga.quando, adesso)
        avvisi.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (p.attivo) R.drawable.ic_campana_piccola else R.drawable.ic_campana_spenta_piccola, 0, 0, 0
        )
        scheda.setOnLongClickListener { allaPressioneLunga(riga); true }
        scheda.setOnClickListener { alTocco(false) }
    }

    private fun colore(c: Context, id: Int) = ContextCompat.getColor(c, id)
    private fun dp(c: Context, valore: Int) = (valore * c.resources.displayMetrics.density).toInt()
}
