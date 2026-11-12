package com.ac.reminder

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.ac.reminder.dati.Avvisi
import com.ac.reminder.dati.Lingua
import com.ac.reminder.dati.PreAvviso
import com.ac.reminder.dati.Preferiti
import com.ac.reminder.dati.Promemoria
import com.ac.reminder.dati.PromemoriaManager
import com.ac.reminder.dati.Ripetizione
import com.ac.reminder.dati.Unita
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * The "New / Edit reminder" sheet that slides up from the bottom.
 *
 * It keeps the choices in memory (date, time, repetition, alerts) and at every tap
 * redraws the sheet with aggiorna(): one single place that decides what is shown.
 * The file is touched only when Save (or Delete) is pressed.
 */
class EditorPromemoria(
    private val attivita: AppCompatActivity,
    private val esistente: RigaDati?,
    private val dopo: () -> Unit
) {
    private enum class Tipo { NO, GIORNI, SETTIMANE, MESI }
    /** The pause of one that repeats: none, only the next occurrence, or up to a day */
    private enum class Pausa { NO, SALTA, FINO }

    private val vista: View = attivita.layoutInflater.inflate(R.layout.foglio_promemoria, null)
    val foglio = BottomSheetDialog(attivita)

    // --- The choices, starting from the reminder (or from "tomorrow at 10:00") ---
    private val vecchio = esistente?.promemoria
    private var data: LocalDate = vecchio?.data ?: LocalDate.now().plusDays(1)
    private var ora: LocalTime = vecchio?.ora ?: LocalTime.of(10, 0)
    /** No time: it's for the whole day, and `ora` is when its alert rings */
    private var senzaOrario = vecchio?.senzaOrario ?: false
    /** The alerts in minutes and hours set aside when the time is taken away: they come back with it */
    private var avvisiConOrario = listOf<PreAvviso>()
    private var tipo = Tipo.NO
    private var ogni = 1
    private val giorniSettimana = mutableSetOf<DayOfWeek>()
    /** Months: false = "on day 12", true = "on the second Thursday" */
    private var mesiPerSettimana = false
    /** null = "follows the date": change the date and it changes too */
    private var giornoMese: Int? = null
    private var ordinale: Int? = null
    private var giornoSettimana: DayOfWeek? = null
    private val avvisi = (vecchio?.avvisi ?: PreAvviso.PREDEFINITI).toMutableList()
    /** Last day of the repetition (included); null = forever */
    private var fino: LocalDate? = vecchio?.fino
    private var pausaModo = Pausa.NO
    /** With "Pause until…": the last day of the pause (included) */
    private var pausaFino: LocalDate? = null

    private val giornoMeseScelto get() = giornoMese ?: data.dayOfMonth
    private val ordinaleScelto get() = ordinale ?: ((data.dayOfMonth - 1) / 7 + 1).let { if (it > 4) -1 else it }
    private val giornoSettimanaScelto get() = giornoSettimana ?: data.dayOfWeek

    // --- The pieces of the sheet ---
    private val campoTitolo = vista.findViewById<EditText>(R.id.campoTitolo)
    private val campoNota = vista.findViewById<EditText>(R.id.campoNota)
    private val etichettaData = vista.findViewById<TextView>(R.id.etichettaData)
    private val etichettaOra = vista.findViewById<TextView>(R.id.etichettaOra)
    private val interruttoreSenzaOrario = vista.findViewById<MaterialSwitch>(R.id.interruttoreSenzaOrario)
    private val testoData = vista.findViewById<TextView>(R.id.testoData)
    private val testoOra = vista.findViewById<TextView>(R.id.testoOra)
    private val segmentiRipeti = listOf(R.id.ripetiNo, R.id.ripetiGiorni, R.id.ripetiSettimane, R.id.ripetiMesi)
        .map { vista.findViewById<TextView>(it) }
    private val rigaOgni = vista.findViewById<View>(R.id.rigaOgni)
    private val testoOgni = vista.findViewById<TextView>(R.id.testoOgni)
    private val testoUnita = vista.findViewById<TextView>(R.id.testoUnita)
    private val gruppoGiorni = vista.findViewById<LinearLayout>(R.id.gruppoGiorni)
    private val segmentiGiorni = gruppoGiorni.children.map { it as TextView }.toList()   // 0 = Monday … 6 = Sunday
    private val opzioniMesi = vista.findViewById<View>(R.id.opzioniMesi)
    private val sceltaGiorno = vista.findViewById<View>(R.id.sceltaGiorno)
    private val sceltaSettimana = vista.findViewById<View>(R.id.sceltaSettimana)
    private val pillolaGiorno = vista.findViewById<TextView>(R.id.pillolaGiorno)
    private val pillolaOrdinale = vista.findViewById<TextView>(R.id.pillolaOrdinale)
    private val pillolaGiornoSettimana = vista.findViewById<TextView>(R.id.pillolaGiornoSettimana)
    private val gruppoFine = vista.findViewById<View>(R.id.gruppoFine)
    private val finePerSempre = vista.findViewById<TextView>(R.id.finePerSempre)
    private val fineFinoAl = vista.findViewById<TextView>(R.id.fineFinoAl)
    private val gruppoPausa = vista.findViewById<View>(R.id.gruppoPausa)
    private val segmentoPausaNo = vista.findViewById<TextView>(R.id.pausaNo)
    private val segmentoPausaSalta = vista.findViewById<TextView>(R.id.pausaSalta)
    private val segmentoPausaFino = vista.findViewById<TextView>(R.id.pausaFino)
    private val riquadroRiassunto = vista.findViewById<View>(R.id.riquadroRiassunto)
    private val testoRiassunto = vista.findViewById<TextView>(R.id.testoRiassunto)
    private val testoProssima = vista.findViewById<TextView>(R.id.testoProssima)
    private val gruppoAvvisi = vista.findViewById<ChipGroup>(R.id.gruppoAvvisi)
    private val testoNessunAvviso = vista.findViewById<TextView>(R.id.testoNessunAvviso)
    private val interruttore = vista.findViewById<MaterialSwitch>(R.id.interruttoreAvvisi)
    private val gruppoPreferiti = vista.findViewById<ChipGroup>(R.id.gruppoPreferiti)
    private val testoOppure = vista.findViewById<TextView>(R.id.testoOppure)
    private val pannello = vista.findViewById<View>(R.id.pannelloAvviso)
    private val segmentiUnita = vista.findViewById<LinearLayout>(R.id.avvisoUnita).children.map { it as TextView }.toList()
    private val tastiRapidi = vista.findViewById<LinearLayout>(R.id.avvisoRapidi).children.map { it as TextView }.toList()
    private val testoQuanto = vista.findViewById<TextView>(R.id.avvisoQuanto)
    private val anteprimaAvviso = vista.findViewById<TextView>(R.id.avvisoAnteprima)
    private val bottoneStella = vista.findViewById<MaterialButton>(R.id.avvisoStella)
    /** The sheet is a strip that scrolls (foglioScorre) with everything else inside (contenuto) */
    private val foglioScorre = vista as NestedScrollView
    private val contenuto = foglioScorre.getChildAt(0) as ViewGroup

    // --- Favorites and the custom choice of alerts ---
    /** The "+ 10 min" pills: they apply to every reminder and stay on the phone */
    private val preferiti = Preferiti.leggi(attivita).toMutableList()
    private var pannelloAperto = false
    private var nAvviso = if (senzaOrario) 1 else 15
    private var unitaAvviso = if (senzaOrario) Unita.GIORNI else Unita.MINUTI

    init {
        // From the saved reminder to the sheet's choices
        when (val r = vecchio?.ripetizione) {
            is Ripetizione.OgniGiorni -> { tipo = Tipo.GIORNI; ogni = r.ogni }
            is Ripetizione.OgniSettimane -> { tipo = Tipo.SETTIMANE; ogni = r.ogni; giorniSettimana += r.giorni }
            is Ripetizione.OgniMesi -> { tipo = Tipo.MESI; ogni = r.ogni; giornoMese = r.giorno }
            is Ripetizione.OgniMesiSettimana -> {
                tipo = Tipo.MESI; ogni = r.ogni; mesiPerSettimana = true
                ordinale = r.ordinale; giornoSettimana = r.giornoSettimana
            }
            else -> {}
        }
        // A pause still running: if it stops exactly at the next occurrence it's "skip the next one"
        vecchio?.pausa?.takeIf { vecchio.inPausa(LocalDate.now()) }?.let { ultimoGiorno ->
            val prossima = vecchio.copy(pausa = null).prossimaVolta(LocalDateTime.now())?.toLocalDate()
            if (ultimoGiorno == prossima) pausaModo = Pausa.SALTA
            else { pausaModo = Pausa.FINO; pausaFino = ultimoGiorno }
        }
        vista.findViewById<TextView>(R.id.titoloFoglio)
            .setText(if (esistente == null) R.string.personale_titolo else R.string.personale_modifica)
        campoTitolo.setText(vecchio?.titolo ?: "")
        campoTitolo.setOnEditorActionListener { _, _, _ -> nascondiTastiera(); true }
        campoNota.setText(vecchio?.nota ?: "")
        // First the switch is set, then the listener is attached: opening the sheet isn't "a change"
        interruttoreSenzaOrario.isChecked = senzaOrario
        interruttoreSenzaOrario.setOnCheckedChangeListener { _, acceso -> cambiaSenzaOrario(acceso) }
        vista.findViewById<View>(R.id.rigaSenzaOrario).setOnClickListener { interruttoreSenzaOrario.toggle() }
        interruttore.isChecked = vecchio?.attivo ?: true
        vista.findViewById<View>(R.id.rigaAccesi).setOnClickListener { interruttore.toggle() }

        // Large text: what no longer fits side by side goes one thing under the other
        if (attivita.testoGrande()) {
            vista.findViewById<LinearLayout>(R.id.rigaDataOra).impila(10)
            vista.findViewById<LinearLayout>(R.id.gruppoRipeti).impila(4)
            vista.findViewById<LinearLayout>(R.id.rigaOgni).impila(4)
            vista.findViewById<LinearLayout>(R.id.rigaIlSettimana).impila(6)
            vista.findViewById<LinearLayout>(R.id.avvisoUnita).impila(4)
            vista.findViewById<LinearLayout>(R.id.rigaBottoni).impila(4)
        }

        collegaComandi()
        aggiorna()
        // At every new layout of the sheet: if the panel is unrolling, the sheet follows it
        contenuto.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> seguiSrotolamento() }

        foglio.setContentView(vista)
        // It opens fully expanded right away, and pulling it down closes it (no half height)
        foglio.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        foglio.behavior.skipCollapsed = true

        // The sheet rises at most up to BELOW the clock bar, never behind it.
        // Why: when the sheet is "almost" as tall as the screen (less than the bar
        // is missing), the library sticks it to the top even if it's shorter, and a gap is left
        // at the bottom; then it suddenly detaches it. It was the "short blanket" while the panel closed.
        // With this ceiling the sheet never enters that zone: it grows and shrinks smoothly.
        // It's recalculated at every change of screen size (rotation): otherwise the starting one
        // would stay, too low in portrait or too high in landscape.
        foglio.setOnShowListener {
            val sfondo = foglio.findViewById<View>(com.google.android.material.R.id.coordinator)
                ?: return@setOnShowListener
            fun aggiornaTetto() {
                val barra = ViewCompat.getRootWindowInsets(sfondo)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
                val tetto = sfondo.height - barra
                if (tetto <= 0 || tetto == foglio.behavior.maxHeight) return
                foglio.behavior.maxHeight = tetto
                // The ceiling only counts at the next measurement: we ask for it right away
                sfondo.post { foglio.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.requestLayout() }
            }
            sfondo.doOnLayout { aggiornaTetto() }
            sfondo.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> aggiornaTetto() }
        }
    }

    fun mostra() = foglio.show()

    private fun collegaComandi() {
        vista.findViewById<View>(R.id.bottoneData).setOnClickListener { nascondiTastiera(); scegliData() }
        vista.findViewById<View>(R.id.bottoneOra).setOnClickListener { nascondiTastiera(); scegliOra() }

        segmentiRipeti.forEachIndexed { i, segmento ->
            segmento.setOnClickListener {
                tipo = Tipo.values()[i]
                // Switching to "Weeks" it starts from the day of the chosen date
                if (tipo == Tipo.SETTIMANE && giorniSettimana.isEmpty()) giorniSettimana += data.dayOfWeek
                aggiorna()
            }
        }
        segmentiGiorni.forEachIndexed { i, segmento ->
            segmento.setOnClickListener {
                val giorno = DayOfWeek.of(i + 1)
                // The last day that's on can't be switched off: a week without days makes no sense
                if (giorno in giorniSettimana) { if (giorniSettimana.size > 1) giorniSettimana -= giorno }
                else giorniSettimana += giorno
                aggiorna()
            }
        }
        ripetiTenendo(vista.findViewById(R.id.bottoneMeno)) { if (ogni > 1) { ogni--; aggiorna() } }
        ripetiTenendo(vista.findViewById(R.id.bottonePiu)) { if (ogni < 99) { ogni++; aggiorna() } }

        sceltaGiorno.setOnClickListener { mesiPerSettimana = false; aggiorna() }
        sceltaSettimana.setOnClickListener { mesiPerSettimana = true; aggiorna() }
        pillolaGiorno.setOnClickListener { mesiPerSettimana = false; aggiorna(); scegliGiornoDelMese() }
        pillolaOrdinale.setOnClickListener { mesiPerSettimana = true; aggiorna(); scegliOrdinale() }
        pillolaGiornoSettimana.setOnClickListener { mesiPerSettimana = true; aggiorna(); scegliGiornoSettimana() }

        finePerSempre.setOnClickListener { fino = null; aggiorna() }
        fineFinoAl.setOnClickListener { nascondiTastiera(); scegliFine() }
        segmentoPausaNo.setOnClickListener { pausaModo = Pausa.NO; aggiorna() }
        segmentoPausaSalta.setOnClickListener { pausaModo = Pausa.SALTA; aggiorna() }
        segmentoPausaFino.setOnClickListener { nascondiTastiera(); scegliPausa() }

        // Alerts: "Or…" opens and closes the custom choice
        testoOppure.setOnClickListener {
            nascondiTastiera()
            if (pannelloAperto) chiudiPannello() else apriPannello()
        }
        segmentiUnita.forEachIndexed { i, s ->
            s.setOnClickListener {
                unitaAvviso = Unita.values()[i]
                nAvviso = nAvviso.coerceAtMost(unitaAvviso.massimo)   // 45 minutes → 4 weeks at most
                aggiornaPannello()
            }
        }
        tastiRapidi.forEachIndexed { i, s ->
            s.setOnClickListener { nAvviso = unitaAvviso.rapidi[i]; aggiornaPannello() }
        }
        ripetiTenendo(vista.findViewById(R.id.avvisoMeno)) { if (nAvviso > 1) { nAvviso--; aggiornaPannello() } }
        ripetiTenendo(vista.findViewById(R.id.avvisoPiu)) { if (nAvviso < unitaAvviso.massimo) { nAvviso++; aggiornaPannello() } }
        bottoneStella.setOnClickListener { cambiaPreferito(PreAvviso(nAvviso, unitaAvviso)) }
        vista.findViewById<View>(R.id.avvisoAggiungi).setOnClickListener {
            aggiungiAvviso(PreAvviso(nAvviso, unitaAvviso), eChiudi = true)
        }

        vista.findViewById<View>(R.id.bottoneAnnulla).setOnClickListener { foglio.dismiss() }
        vista.findViewById<View>(R.id.bottoneSalva).setOnClickListener { salva() }
        vista.findViewById<View>(R.id.bottoneElimina).apply {
            isVisible = esistente != null      // only what already exists gets deleted
            setOnClickListener { confermaElimina() }
        }
    }

    /** Turns the choices into the repetition to save. */
    private fun ripetizioneScelta(): Ripetizione = when (tipo) {
        Tipo.NO -> Ripetizione.Nessuna
        Tipo.GIORNI -> Ripetizione.OgniGiorni(ogni)
        Tipo.SETTIMANE -> Ripetizione.OgniSettimane(ogni, giorniSettimana.ifEmpty { setOf(data.dayOfWeek) }.toSet())
        Tipo.MESI ->
            if (mesiPerSettimana) Ripetizione.OgniMesiSettimana(ogni, ordinaleScelto, giornoSettimanaScelto)
            else Ripetizione.OgniMesi(ogni, giornoMese)
    }

    /** "Until" only applies to the ones that repeat. */
    private fun finoScelto(): LocalDate? = if (tipo == Tipo.NO) null else fino

    /** The current choices without the pause: what "the next occurrence" would be. */
    private fun senzaPausa() =
        Promemoria(data, ora, "", ripetizione = ripetizioneScelta(), fino = finoScelto(), senzaOrario = senzaOrario)

    /**
     * The last day of the pause, to save. "Skip the next one" is worked out at this moment (the day
     * of the next occurrence), so it follows the date and the repetition if you change them here.
     */
    private fun pausaScelta(): LocalDate? = when {
        tipo == Tipo.NO -> null
        pausaModo == Pausa.SALTA -> senzaPausa().prossimaVolta(LocalDateTime.now())?.toLocalDate()
        pausaModo == Pausa.FINO -> pausaFino
        else -> null
    }

    /** A "test" reminder with the current choices (for the summary and previews). */
    private fun prova() = senzaPausa().copy(pausa = pausaScelta())

    /**
     * The "No time" switch. Without a time the alerts can only be whole days before ("1 day", "2 weeks"):
     * the ones in minutes and hours are set aside, and come back if the time comes back.
     */
    private fun cambiaSenzaOrario(acceso: Boolean) {
        nascondiTastiera()
        senzaOrario = acceso
        if (acceso) {
            avvisiConOrario = avvisi.filterNot { it.giorniInteri }
            avvisi.removeAll(avvisiConOrario)
            if (unitaAvviso.minuti < Unita.GIORNI.minuti) { unitaAvviso = Unita.GIORNI; nAvviso = 1 }
        } else {
            val tutti = PreAvviso.ordina(avvisi + avvisiConOrario).take(PreAvviso.MASSIMO)
            avvisi.clear(); avvisi += tutti
            avvisiConOrario = emptyList()
        }
        anima { aggiorna() }
    }

    /** The next time it would fall with the current choices. */
    private fun prossimaVolta(): LocalDateTime? = prova().prossimaVolta(LocalDateTime.now())

    /** Redraws the sheet according to the choices: what's shown, what's on, what's written. */
    private fun aggiorna() {
        val c = attivita
        testoData.text = Formati.dataCasella(data)
        testoOra.text = Formati.ora(ora)
        etichettaOra.setText(if (senzaOrario) R.string.etichetta_avviso_alle else R.string.etichetta_ora)
        etichettaData.setText(if (tipo == Tipo.NO) R.string.etichetta_quando else R.string.etichetta_a_partire_da)

        segmentiRipeti.forEachIndexed { i, s -> s.isSelected = i == tipo.ordinal }
        rigaOgni.isVisible = tipo != Tipo.NO
        testoOgni.text = ogni.toString()
        if (tipo != Tipo.NO) {
            testoUnita.text = c.resources.getQuantityString(
                when (tipo) {
                    Tipo.GIORNI -> R.plurals.unita_giorni
                    Tipo.SETTIMANE -> R.plurals.unita_settimane
                    else -> R.plurals.unita_mesi
                }, ogni
            )
        }
        gruppoGiorni.isVisible = tipo == Tipo.SETTIMANE
        segmentiGiorni.forEachIndexed { i, s -> s.isSelected = DayOfWeek.of(i + 1) in giorniSettimana }

        // Months: the selected option also "lights up" the dot and the pills inside it
        opzioniMesi.isVisible = tipo == Tipo.MESI
        sceltaGiorno.isSelected = !mesiPerSettimana
        sceltaSettimana.isSelected = mesiPerSettimana
        pillolaGiorno.text = giornoMeseScelto.toString()
        pillolaOrdinale.text = nomeOrdinale(ordinaleScelto, giornoSettimanaScelto)
        pillolaGiornoSettimana.text = nomeGiorno(giornoSettimanaScelto)

        gruppoFine.isVisible = tipo != Tipo.NO
        finePerSempre.isSelected = fino == null
        fineFinoAl.isSelected = fino != null
        fineFinoAl.text = fino?.let { Formati.finoAl(it) } ?: c.getString(R.string.fine_fino_al)

        gruppoPausa.isVisible = tipo != Tipo.NO
        segmentoPausaNo.isSelected = pausaModo == Pausa.NO
        segmentoPausaSalta.isSelected = pausaModo == Pausa.SALTA
        segmentoPausaFino.isSelected = pausaModo == Pausa.FINO
        segmentoPausaFino.text = pausaFino?.takeIf { pausaModo == Pausa.FINO }?.let { Formati.finoAl(it) }
            ?: c.getString(R.string.pausa_fino)

        val r = ripetizioneScelta()
        riquadroRiassunto.isVisible = tipo != Tipo.NO
        var riassunto = prova().descrizioneRipetizione()
        if (r is Ripetizione.OgniMesi && giornoMeseScelto > 28) riassunto += "\n" + c.getString(R.string.nota_mesi_corti)
        testoRiassunto.text = riassunto
        val prossima = prossimaVolta()
        testoProssima.isVisible = prossima != null
        if (prossima != null) {
            testoProssima.text = c.getString(
                R.string.prossima_volta,
                if (senzaOrario) Formati.giornoLungo(prossima.toLocalDate()) else Formati.giornoEOra(c, prossima)
            )
        }

        mostraAvvisi()
    }

    /**
     * The alerts, from the top: the chosen pills (the ✕ removes one), the favorites
     * "+ 10 min" (one tap and the alert is there), "Or…" and the custom choice.
     * Once the maximum number of alerts is reached, nothing is left below.
     */
    private fun mostraAvvisi() {
        gruppoAvvisi.removeAllViews()
        val inflater = attivita.layoutInflater
        for (a in avvisi) {
            val chip = inflater.inflate(R.layout.chip_avviso, gruppoAvvisi, false) as Chip
            chip.text = a.etichetta()
            chip.setOnCloseIconClickListener { anima { avvisi.remove(a); aggiorna() } }
            gruppoAvvisi.addView(chip)
        }
        // In orange: "No alerts…", or "the alerts have already passed: it will ring at 16:00".
        // Without a time the alert of the day is always there: it's only said when today's has already gone
        val adesso = LocalDateTime.now()
        val allOra = Avvisi.daSuonareAllOra(prova().copy(avvisi = avvisi.toList()), adesso)
        val oggiPassato = senzaOrario && prossimaVolta()?.isBefore(adesso) == true
        testoNessunAvviso.isVisible = if (senzaOrario) oggiPassato else avvisi.isEmpty() || allOra != null
        testoNessunAvviso.text = when {
            oggiPassato -> attivita.getString(R.string.avviso_oggi_passato, Formati.ora(ora))
            allOra != null -> attivita.getString(R.string.avvisi_passati, Formati.ora(allOra.toLocalTime()))
            else -> attivita.getString(R.string.avvisi_nessuno)
        }

        val cePosto = avvisi.size < PreAvviso.MASSIMO
        gruppoPreferiti.removeAllViews()
        if (cePosto) {
            for (p in preferiti) {
                if (avvisi.any { it.minuti == p.minuti }) continue   // already among the alerts: no point offering it again
                if (senzaOrario && !p.giorniInteri) continue         // "10 min before" of what, without a time?
                val chip = inflater.inflate(R.layout.chip_aggiungi, gruppoPreferiti, false) as Chip
                chip.text = p.breve()
                chip.setOnClickListener { nascondiTastiera(); aggiungiAvviso(p) }
                chip.setOnLongClickListener { confermaTogliPreferito(p); true }
                gruppoPreferiti.addView(chip)
            }
        }
        gruppoPreferiti.isVisible = gruppoPreferiti.childCount > 0
        // "Or…" if there are favorites above, otherwise an invitation that stands on its own
        testoOppure.isVisible = cePosto
        testoOppure.setText(if (gruppoPreferiti.childCount > 0) R.string.preferiti_oppure else R.string.preferiti_scegli)
        if (!cePosto && pannelloAperto) chiudiPannello()   // at 5 alerts it's no longer needed
        aggiornaPannello()
    }

    /** The custom choice: unit, quick buttons, number, preview and star. */
    private fun aggiornaPannello() {
        if (!pannelloAperto) return
        val c = attivita
        segmentiUnita.forEachIndexed { i, s ->
            val u = Unita.values()[i]
            s.isVisible = !senzaOrario || u.minuti >= Unita.GIORNI.minuti   // without a time: days and weeks only
            s.text = u.nome(nAvviso)
            s.isSelected = u == unitaAvviso
        }
        // The buttons of the chosen unit; the one equal to the number is on (none if it's a 20)
        tastiRapidi.forEachIndexed { i, s ->
            val valore = unitaAvviso.rapidi.getOrNull(i)
            s.isVisible = valore != null
            if (valore != null) {
                s.text = valore.toString()
                s.isSelected = valore == nAvviso
            }
        }
        val scelto = PreAvviso(nAvviso, unitaAvviso)
        testoQuanto.text = scelto.etichetta()
        // "It will ring: Monday 9 November at 15:45"
        val volta = prossimaVolta()
        anteprimaAvviso.isVisible = volta != null
        if (volta != null) {
            val quando = volta.minusMinutes(scelto.minuti)
            anteprimaAvviso.text = c.getString(
                R.string.avviso_anteprima,
                Formati.giornoEOra(c, quando)
            )
        }
        val giaPreferito = scelto in preferiti
        bottoneStella.setText(if (giaPreferito) R.string.preferiti_gia else R.string.preferiti_aggiungi)
        bottoneStella.setIconResource(if (giaPreferito) R.drawable.ic_stella_piena else R.drawable.ic_stella)
    }

    /** Adds an alert (from a favorite or from the custom choice), if it isn't there already. */
    private fun aggiungiAvviso(nuovo: PreAvviso, eChiudi: Boolean = false) {
        if (avvisi.any { it.minuti == nuovo.minuti }) {
            Toast.makeText(attivita, R.string.avviso_doppio, Toast.LENGTH_SHORT).show()
            return
        }
        val aggiungi = {
            avvisi += nuovo
            val ordinati = PreAvviso.ordina(avvisi)
            avvisi.clear(); avvisi += ordinati
        }
        // From the custom choice: the alert appears and the panel rolls up
        if (eChiudi) {
            aggiungi(); aggiorna()
            if (pannelloAperto) chiudiPannello()   // (at the fifth alert aggiorna has already closed it)
        } else {
            anima { aggiungi(); aggiorna() }
        }
    }

    /** The star: puts the choice among the favorites, or removes it if it's already there. */
    private fun cambiaPreferito(p: PreAvviso) {
        if (p !in preferiti && preferiti.size >= Preferiti.MASSIMO) {
            Toast.makeText(attivita, attivita.getString(R.string.preferiti_massimo, Preferiti.MASSIMO), Toast.LENGTH_LONG).show()
            return
        }
        anima {
            if (p in preferiti) preferiti -= p else preferiti += p
            val ordinati = Preferiti.ordina(preferiti)
            preferiti.clear(); preferiti += ordinati
            Preferiti.scrivi(attivita, preferiti)
            aggiorna()
        }
    }

    /** Pressing and holding a favorite: "Remove “10 min” from favorites?" */
    private fun confermaTogliPreferito(p: PreAvviso) {
        MaterialAlertDialogBuilder(attivita)
            .setTitle(attivita.getString(R.string.preferiti_togli_titolo, p.breve()))
            .setPositiveButton(R.string.preferiti_togli) { _, _ ->
                anima { preferiti -= p; Preferiti.scrivi(attivita, preferiti); aggiorna() }
            }
            .setNegativeButton(R.string.annulla, null)
            .show().eliminaInRosso().titoloIntero()
    }

    /**
     * The changes made in here slide smoothly (things appear and move)
     * instead of jumping. Not the pills: they're rebuilt from scratch and would flicker.
     */
    private fun anima(cambi: () -> Unit) {
        TransitionManager.beginDelayedTransition(contenuto, AutoTransition().apply {
            duration = 250
            excludeChildren(gruppoAvvisi, true)
            excludeChildren(gruppoPreferiti, true)
        })
        cambi()
    }

    // --- The panel that unrolls ---

    private var srotolamento: ValueAnimator? = null
    /** While it unrolls: where scrolling and height started from, and which way it goes */
    private var inMovimento = false
    private var inApertura = false
    private var scorrimentoPartenza = 0
    private var altezzaPartenza = 0

    private fun apriPannello() {
        pannelloAperto = true
        aggiornaPannello()          // first the texts are written, then its height is measured
        srotolaPannello(apri = true)
    }

    private fun chiudiPannello() {
        pannelloAperto = false
        srotolaPannello(apri = false)
    }

    /**
     * The panel grows (or shrinks) a little at every frame, easing. The sheet follows it
     * by itself, getting longer or shorter along with it; and it scrolls by the same amount
     * (seguiSrotolamento does that), so what's below, Alerts on and Save,
     * stays still where it was. Closing retraces the same path backwards:
     * you're back exactly as you were before opening.
     */
    private fun srotolaPannello(apri: Boolean) {
        // Tapped again halfway: it restarts from where it got to, without jumps
        val altezzaOra = if (!pannello.isVisible) 0
            else pannello.layoutParams.height.takeIf { it >= 0 } ?: pannello.height
        srotolamento?.let { vecchio -> srotolamento = null; vecchio.cancel() }

        // How tall the fully open panel is
        val larghezza = View.MeasureSpec.makeMeasureSpec(
            contenuto.width - contenuto.paddingLeft - contenuto.paddingRight, View.MeasureSpec.EXACTLY
        )
        pannello.measure(larghezza, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val piena = pannello.measuredHeight

        scorrimentoPartenza = foglioScorre.scrollY
        altezzaPartenza = altezzaOra
        inApertura = apri
        inMovimento = true
        pannello.layoutParams.height = altezzaOra
        pannello.isVisible = true

        val animazione = ValueAnimator.ofInt(altezzaOra, if (apri) piena else 0)
        animazione.duration = 320
        animazione.interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)   // starts firmly, arrives gently
        animazione.addUpdateListener {
            val h = it.animatedValue as Int
            pannello.layoutParams.height = h
            pannello.alpha = if (piena > 0) h.toFloat() / piena else 1f
            pannello.requestLayout()
        }
        animazione.doOnEnd {
            if (srotolamento !== animazione) return@doOnEnd   // interrupted by a new tap
            inMovimento = false
            if (!apri) pannello.isVisible = false
            pannello.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            pannello.alpha = 1f
            pannello.requestLayout()
        }
        srotolamento = animazione
        animazione.start()
    }

    /**
     * Called at every new layout of the sheet: while the panel unrolls,
     * the sheet scrolls exactly by how much the panel has grown (or shrunk).
     * It's done HERE, with the sizes already taken, and not before: otherwise the sheet doesn't
     * yet know how far it can scroll and stops halfway.
     */
    private fun seguiSrotolamento() {
        if (!inMovimento) return
        val cresciuto = (if (pannello.isVisible) pannello.height else 0) - altezzaPartenza
        var y = scorrimentoPartenza + cresciuto
        // opening, "Or…" must never go out of the top of the screen
        if (inApertura) y = minOf(y, testoOppure.top - (12 * attivita.resources.displayMetrics.density).toInt())
        foglioScorre.scrollTo(0, y.coerceAtLeast(0))   // scrollTo never goes beyond the bottom
    }

    // --- The small choice dialogs ---

    private fun scegliData() {
        val scelta = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (tipo == Tipo.NO) R.string.etichetta_quando else R.string.etichetta_a_partire_da)
            // the picker reasons in UTC: we convert the "pure" day
            .setSelection(data.toEpochDay() * 86_400_000L)
            .build()
        scelta.addOnPositiveButtonClickListener { millis ->
            val nuova = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            // Weeks, like months: if the only day that's on is the date's own, it follows the date.
            // Days you picked yourself stay as they are.
            if (giorniSettimana == setOf(data.dayOfWeek)) {
                giorniSettimana.clear()
                giorniSettimana += nuova.dayOfWeek
            }
            data = nuova
            // An end that falls before the start makes no sense: back to "forever"
            if (fino?.isBefore(data) == true) fino = null
            aggiorna()
        }
        scelta.show(attivita.supportFragmentManager, "data")
    }

    /** "Until…": a calendar where the days before the start can't be touched. */
    private fun scegliFine() {
        val inizioMillis = data.toEpochDay() * 86_400_000L
        val scelta = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.fine_titolo)
            .setSelection((fino ?: data.plusMonths(1)).toEpochDay() * 86_400_000L)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setStart(inizioMillis)
                    .setValidator(DateValidatorPointForward.from(inizioMillis))
                    .build()
            )
            .build()
        scelta.addOnPositiveButtonClickListener { millis ->
            fino = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            aggiorna()
        }
        scelta.show(attivita.supportFragmentManager, "fino")
    }

    /** "Pause until…": the last day without the reminder, from today on. */
    private fun scegliPausa() {
        val oggiMillis = LocalDate.now().toEpochDay() * 86_400_000L
        val scelta = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pausa_titolo)
            .setSelection((pausaFino ?: LocalDate.now().plusWeeks(1)).toEpochDay() * 86_400_000L)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setStart(oggiMillis)
                    .setValidator(DateValidatorPointForward.from(oggiMillis))
                    .build()
            )
            .build()
        scelta.addOnPositiveButtonClickListener { millis ->
            pausaFino = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            pausaModo = Pausa.FINO
            aggiorna()
        }
        scelta.show(attivita.supportFragmentManager, "pausa")
    }

    private fun scegliOra() {
        val scelta = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)   // round clock, not keypad
            .setHour(ora.hour)
            .setMinute(ora.minute)
            .setTitleText(if (senzaOrario) R.string.etichetta_avviso_alle else R.string.etichetta_ora)
            .build()
        scelta.addOnPositiveButtonClickListener {
            ora = LocalTime.of(scelta.hour, scelta.minute)
            aggiorna()
        }
        scelta.show(attivita.supportFragmentManager, "ora")
    }

    /** "On day [N]": a grid from 1 to 31, like a calendar without a month. */
    private fun scegliGiornoDelMese() {
        val c = attivita
        val griglia = GridLayout(c).apply {
            columnCount = 7
            setPadding(dp(18), dp(8), dp(18), 0)
        }
        lateinit var finestra: AlertDialog
        for (n in 1..31) {
            griglia.addView(TextView(c).apply {
                text = n.toString()
                gravity = Gravity.CENTER
                typeface = ResourcesCompat.getFont(c, R.font.jakarta_extrabold)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColorStateList(c, R.color.testo_pillola))
                setBackgroundResource(R.drawable.fondo_pillola)
                isSelected = n == giornoMeseScelto
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply { width = 0; height = dp(40); setMargins(dp(3), dp(3), dp(3), dp(3)) }
                setOnClickListener {
                    giornoMese = n
                    mesiPerSettimana = false
                    aggiorna()
                    finestra.dismiss()
                }
            })
        }
        finestra = MaterialAlertDialogBuilder(c)
            .setTitle(R.string.giorno_titolo)
            .setView(griglia)
            .setNegativeButton(R.string.annulla, null)
            .show().titoloIntero()
    }

    /** "On the [second] …": first, second, third, fourth, last */
    private fun scegliOrdinale() {
        val menu = PopupMenu(attivita, pillolaOrdinale)
        listOf(1, 2, 3, 4, -1).forEach { o -> menu.menu.add(0, o + 10, 0, nomeOrdinale(o, giornoSettimanaScelto)) }
        menu.setOnMenuItemClickListener { voce -> ordinale = voce.itemId - 10; aggiorna(); true }
        menu.show()
    }

    /** "… [Thursday]": from Monday to Sunday */
    private fun scegliGiornoSettimana() {
        val menu = PopupMenu(attivita, pillolaGiornoSettimana)
        DayOfWeek.values().forEach { g -> menu.menu.add(0, g.value, 0, nomeGiorno(g)) }
        menu.setOnMenuItemClickListener { voce -> giornoSettimana = DayOfWeek.of(voce.itemId); aggiorna(); true }
        menu.show()
    }

    // --- Saving and deleting ---

    private fun salva() {
        val nome = campoTitolo.text.toString().trim()
        if (nome.isEmpty()) {
            campoTitolo.error = attivita.getString(R.string.personale_serve_nome)
            campoTitolo.requestFocus()
            return
        }
        val nuovo = Promemoria(
            data = data,
            ora = ora,
            titolo = nome,
            attivo = interruttore.isChecked,
            ripetizione = ripetizioneScelta(),
            avvisi = avvisi.toList(),
            fino = finoScelto(),
            nota = campoNota.text.toString().trim(),
            senzaOrario = senzaOrario,
            pausa = pausaScelta()
        ).let { it.copy(allOra = Avvisi.daSuonareAllOra(it, LocalDateTime.now())) }
        val salvato = if (esistente == null) PromemoriaManager.aggiungi(attivita, nuovo)
        else PromemoriaManager.modifica(attivita, esistente.indice, nuovo)
        if (!salvato) {   // storage full: the sheet stays open, nothing is lost of what was typed
            Toast.makeText(attivita, R.string.salvataggio_fallito, Toast.LENGTH_LONG).show()
            return
        }

        if (nuovo.prossimaVolta(LocalDateTime.now()) == null) {
            Toast.makeText(attivita, R.string.salvato_passato, Toast.LENGTH_LONG).show()
        }
        nascondiTastiera()
        foglio.dismiss()
        dopo()
    }

    /** Asks for confirmation before throwing away a reminder (if it repeats: every occurrence). */
    private fun confermaElimina() {
        val riga = esistente ?: return
        val p = riga.promemoria
        val messaggio =
            if (p.siRipete) "\"${p.titolo}\" · ${p.descrizioneRipetizione()}\n\n" +
                attivita.getString(R.string.elimina_ripetuto)
            else "\"${p.titolo}\" · ${Formati.quandoLungo(riga.quando, conOra = !p.senzaOrario)}"
        MaterialAlertDialogBuilder(attivita)
            .setTitle(R.string.elimina_titolo)
            .setMessage(messaggio)
            .setPositiveButton(R.string.elimina) { _, _ ->
                if (!PromemoriaManager.rimuovi(attivita, riga.indice)) {
                    Toast.makeText(attivita, R.string.salvataggio_fallito, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                foglio.dismiss()
                dopo()
            }
            .setNegativeButton(R.string.annulla, null)
            .show()
            .eliminaInRosso().titoloIntero()
    }

    // --- Small helpers ---

    private fun nascondiTastiera() {
        val tastiera = attivita.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        tastiera.hideSoftInputFromWindow(campoTitolo.windowToken, 0)
        campoTitolo.clearFocus()
        campoNota.clearFocus()
    }

    private fun nomeGiorno(g: DayOfWeek) = Lingua.nome(g.getDisplayName(TextStyle.FULL, Lingua.locale))

    /** Italian "secondo" giovedì but "seconda" domenica; English has no masculine and feminine */
    private fun nomeOrdinale(o: Int, g: DayOfWeek) = Lingua.ordinale(o, femminile = g == DayOfWeek.SUNDAY)

    private fun dp(valore: Int) = (valore * attivita.resources.displayMetrics.density).toInt()
}

/**
 * A tap on − or + takes one step; holding the number down it runs by itself
 * (from 1 to 59 minutes without 58 taps).
 */
@SuppressLint("ClickableViewAccessibility")
fun ripetiTenendo(bottone: View, passo: () -> Unit) {
    val mano = Handler(Looper.getMainLooper())
    val giro = object : Runnable {
        override fun run() { passo(); mano.postDelayed(this, 70) }
    }
    bottone.setOnClickListener { passo() }
    bottone.setOnLongClickListener { mano.post(giro); true }
    bottone.setOnTouchListener { _, evento ->
        if (evento.action == MotionEvent.ACTION_UP || evento.action == MotionEvent.ACTION_CANCEL) mano.removeCallbacks(giro)
        false
    }
}
