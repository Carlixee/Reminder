package com.ac.reminder.notifiche

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.ac.reminder.Formati
import com.ac.reminder.MainActivity
import com.ac.reminder.R
import com.ac.reminder.SceltaLingua
import com.ac.reminder.impila
import com.ac.reminder.testoGrande
import com.ac.reminder.dati.Lingua
import com.ac.reminder.dati.Promemoria
import com.ac.reminder.dati.PromemoriaManager
import java.time.LocalDateTime

/**
 * The "Upcoming reminders" tile in quick settings.
 *
 * DON'T change the name or folder of this class: Android recognises the tile
 * by its full name (com.ac.reminder.notifiche.RiquadroPromemoria), and changing it
 * it would disappear from the panel of whoever added it (it would have to be added again by hand).
 *
 * Tap: dialog with the next 5; tapping one opens its edit sheet.
 * Long tap: Android handles it, it opens the app (see QS_TILE_PREFERENCES in the manifest).
 */
class RiquadroPromemoria : TileService() {

    // Below Android 13 the language chosen in the app only gets here this way
    override fun attachBaseContext(base: Context) = super.attachBaseContext(SceltaLingua.contesto(base))

    private companion object {
        /** How many rows are visible without scrolling: the dialog never gets taller than this. */
        const val VISIBILI = 5
        /** The number of the tile's "permission to open" (the notifications use 0). */
        const val RICHIESTA_RIQUADRO = 1
    }

    /** The dialog open right now: tapping the bell again brings it to the front, it doesn't pile up. */
    private var finestraAperta: Dialog? = null

    /**
     * Android calls it every time you open the panel: under the name it says WHEN the next
     * reminder is ("oggi 15:00", "gio 12/11 15:00"). Which one it is, you see by tapping.
     */
    override fun onStartListening() {
        super.onStartListening()
        val riquadro = qsTile ?: return
        val adesso = LocalDateTime.now()
        val prossimo = PromemoriaManager.inArrivo(this, adesso).firstOrNull()

        riquadro.label = getString(R.string.tile_titolo)
        riquadro.state = if (prossimo != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            riquadro.subtitle =
                if (prossimo != null) Formati.quandoBreve(adesso, prossimo.second, conOra = !prossimo.first.senzaOrario)
                else getString(R.string.tile_nessuno)
        }
        riquadro.updateTile()
    }

    override fun onClick() {
        super.onClick()
        mostraFinestra()
    }

    /**
     * Tiles can't open normal dialogs: there's showDialog for that,
     * which shows it above the panel. The theme is the app's, so the
     * dialog has the same colours, the same typeface and the same corners.
     */
    private fun mostraFinestra() {
        // Is a dialog already open? That one is brought to the front, without creating another.
        // It must NOT be closed to open a new one: closing it, Android withdraws the "permission"
        // to show dialogs above the panel and the new one would make the app close.
        finestraAperta?.let { aperta ->
            if (aperta.isShowing) { showDialog(aperta); return }
        }

        val contesto = ContextThemeWrapper(this, R.style.Theme_Reminder)
        val inflater = LayoutInflater.from(contesto)
        val vista = inflater.inflate(R.layout.finestra_riquadro, null)
        val elenco = vista.findViewById<LinearLayout>(R.id.elenco)

        val finestra = Dialog(contesto)
        finestraAperta = finestra
        finestra.setOnDismissListener { if (finestraAperta === finestra) finestraAperta = null }
        finestra.requestWindowFeature(Window.FEATURE_NO_TITLE)
        finestra.setContentView(vista)
        finestra.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // Behind the dialog: blurred instead of just darkened (from Android 12), BUT only if the
        // phone allows it to apps. The Samsung S23 says no ("Blur supported on
        // device: false"): there the normal dark veil stays, which is more decisive.
        if (Build.VERSION.SDK_INT >= 31 && getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled) {
            finestra.window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.apply { blurBehindRadius = (30 * resources.displayMetrics.density).toInt() }
                setDimAmount(0.3f)   // with the blur a light veil is enough
            }
        } else {
            // No blur: an almost black veil (0 = transparent, 1 = solid black),
            // so the background behind almost disappears and the dialog stays the only thing to look at
            finestra.window?.setDimAmount(0.85f)
        }
        finestra.setOnShowListener {
            // almost as wide as the screen, as tall as needed
            finestra.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val adesso = LocalDateTime.now()
        val prossimi = PromemoriaManager.inArrivo(this, adesso)   // all of them: beyond the first 5 you scroll
        val scorrimento = vista.findViewById<ScrollView>(R.id.scorrimentoElenco)
        vista.findViewById<View>(R.id.testoVuoto).isVisible = prossimi.isEmpty()
        scorrimento.isVisible = prossimi.isNotEmpty()

        // Large text: title and detail can go on two lines instead of being cut off
        // ("in 2 Thu…"), and "+ New" goes under "Open the app" instead of going past the edge
        val grande = contesto.testoGrande()
        if (grande) vista.findViewById<LinearLayout>(R.id.rigaBottoni).impila(4)

        prossimi.forEachIndexed { i, (p, quando) ->
            val riga = inflater.inflate(R.layout.riga_riquadro, elenco, false)
            val primo = i == 0     // the first one has the blue background, like the card at the top of the app
            val giorno = quando.toLocalDate()
            fun tinta(id: Int) = ContextCompat.getColor(contesto, id)

            riga.setBackgroundResource(if (primo) R.drawable.fondo_voce_prima else R.drawable.fondo_voce)
            riga.findViewById<View>(R.id.casellaData)
                .setBackgroundResource(if (primo) R.drawable.fondo_data_riquadro_prima else R.drawable.fondo_data_riquadro)
            riga.findViewById<TextView>(R.id.testoGiorno).apply {
                text = Formati.giornoCorto(giorno)
                setTextColor(tinta(if (primo) R.color.azzurro_chiaro else R.color.testo_secondario))
            }
            riga.findViewById<TextView>(R.id.testoNumero).apply {
                text = giorno.dayOfMonth.toString()
                setTextColor(tinta(if (primo) R.color.bianco else R.color.testo_principale))
            }
            riga.findViewById<TextView>(R.id.testoTitolo).apply {
                text = p.titolo
                setTextColor(tinta(if (primo) R.color.bianco else R.color.testo_principale))
                if (grande) maxLines = 2
            }
            // "16:00 · in 3 days", "22:00 · every week: Sat"; without a time: "today", "in 3 days"
            riga.findViewById<TextView>(R.id.testoDettaglio).apply {
                val pezzi = mutableListOf<String>()
                if (!p.senzaOrario) pezzi += Formati.ora(quando.toLocalTime())
                if (primo || p.senzaOrario) pezzi += Formati.traQuanto(contesto, adesso, quando, conOra = !p.senzaOrario)
                if (p.siRipete) pezzi += Formati.ripetizioneBreve(p)
                if (!p.attivo) pezzi += getString(R.string.avvisi_spenti).lowercase(Lingua.locale)
                text = pezzi.joinToString(" · ")
                setTextColor(tinta(if (primo) R.color.azzurro_chiaro else R.color.testo_secondario))
                if (grande) maxLines = 2
            }
            riga.setOnClickListener { finestra.dismiss(); apriApp(p) }
            elenco.addView(riga)
        }

        // More than 5: you see 5 whole rows and HALF a sixth. The cut row says
        // "there's more below, scroll"; the dialog doesn't grow beyond this size.
        // How tall the rows really are is measured (it changes if you enlarge the text).
        if (prossimi.size > VISIBILI) {
            val liberamente = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            // As wide as the dialog (90% of the screen minus the margins): needed to know
            // when a long title goes on two lines, with large text
            val larghezza = View.MeasureSpec.makeMeasureSpec(
                (resources.displayMetrics.widthPixels * 0.9).toInt() - (36 * resources.displayMetrics.density).toInt(),
                View.MeasureSpec.EXACTLY
            )
            var altezza = 0
            for (i in 0..VISIBILI) {
                val riga = elenco.getChildAt(i)
                riga.measure(larghezza, liberamente)
                altezza +=
                    if (i < VISIBILI) riga.measuredHeight + (riga.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                    else riga.measuredHeight / 2   // the sixth: only half
            }
            scorrimento.layoutParams.height = altezza
        }

        vista.findViewById<View>(R.id.bottoneApriApp).setOnClickListener { finestra.dismiss(); apriApp(null) }
        vista.findViewById<View>(R.id.bottoneNuovo).setOnClickListener { finestra.dismiss(); apriApp(null, nuovo = true) }
        finestra.setCanceledOnTouchOutside(true)   // no more "Close": you tap outside

        showDialog(finestra)
    }

    /**
     * Closes the panel and opens the app; with a reminder, straight onto its sheet.
     * From Android 14 a PendingIntent must be passed (a ready-made "permission to open"):
     * the old way with the plain Intent makes the app crash there.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun apriApp(p: Promemoria?, nuovo: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (nuovo) intent.putExtra(MainActivity.EXTRA_NUOVO, true)
        if (p != null) {
            intent.putExtra(MainActivity.EXTRA_TITOLO, p.titolo)
                .putExtra(MainActivity.EXTRA_PARTENZA, p.partenza.toString())
        }
        if (Build.VERSION.SDK_INT >= 34) {
            // Its own number: with the notifications' one (0) Android would treat the two as the SAME
            // "permission to open", and a notification already in the shade would open this sheet too
            val apri = PendingIntent.getActivity(
                this, RICHIESTA_RIQUADRO, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(apri)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
