package com.ac.reminder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.ac.reminder.dati.Lingua
import com.ac.reminder.dati.PromemoriaManager
import com.ac.reminder.notifiche.DiagnosticaNotifiche
import com.ac.reminder.notifiche.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        /** The tile asks to open one specific reminder: it's recognised by title and start. */
        const val EXTRA_TITOLO = "apri_titolo"
        const val EXTRA_PARTENZA = "apri_partenza"
        const val EXTRA_NUOVO = "apri_nuovo"
        /** The donations page: the browser opens it, not the app */
        /** Saved once the first-launch note about paused apps has been seen. */
        private const val NOTA_SOSPENSIONE = "nota_sospensione_vista"

        const val LINK_DONAZIONI = "https://paypal.me/Carlixee"
    }

    private lateinit var adattatore: PromemoriaAdapter
    private lateinit var lista: RecyclerView
    private lateinit var gestoreLista: LinearLayoutManager
    private lateinit var campoRicerca: EditText
    private lateinit var bottoneCerca: ImageButton
    private lateinit var statoNotifiche: TextView
    private lateinit var numeroPassati: TextView
    private lateinit var bottoneNuovo: ExtendedFloatingActionButton

    private var ricercaAperta = false
    /** To notice the moment the keyboard closes */
    private var tastieraEraAperta = false
    /** All the rows, already worked out (the search only filters what to SHOW). */
    private var righe: List<RigaDati> = emptyList()
    /** On first opening the list sits on the next one: past reminders stay above, out of view. */
    private var daPosizionare = true
    /**
     * Have the reminders been read from the file yet? Until then, no positioning.
     * When Android rebuilds the screen (text size or language changed with the
     * app open) it puts the text back into the search box, and that redraws the list
     * BEFORE they've been read: the "go to the next one" was wasted on an empty list,
     * and you stayed at the top, on the very first past reminder.
     */
    private var righeLette = false
    /** The edit sheet that's open, if any: never two at once. */
    private var editor: EditorPromemoria? = null

    // System dialog asking "Allow this app to send notifications?"
    private val chiediPermessoNotifiche =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concesso ->
            if (!concesso) {
                Toast.makeText(this, R.string.permesso_negato, Toast.LENGTH_LONG).show()
            }
            notaSospensione()   // one dialog at a time: the note comes after the permission request
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The screen reaches the very top, under the transparent status bar, with light
        // icons. From Android 15 the phone does it by itself; before, the bar would be blue.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // First of all: dates and repetitions in the same language as the screens
        SceltaLingua.aggiornaTesti(this)
        setContentView(R.layout.activity_main)

        val testata = findViewById<View>(R.id.testata)
        val tastiExtra = findViewById<View>(R.id.tastiExtra)
        lista = findViewById(R.id.lista)
        campoRicerca = findViewById(R.id.campoRicerca)
        bottoneCerca = findViewById(R.id.bottoneCerca)
        statoNotifiche = findViewById(R.id.statoNotifiche)
        numeroPassati = findViewById(R.id.numeroPassati)
        bottoneNuovo = findViewById(R.id.bottoneNuovo)

        // The app is drawn "edge to edge": we ask Android how tall the
        // system bars are (clock above, gestures below) and leave room for them
        val dp = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.radice)) { _, insets ->
            val barre = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The keyboard is a "bar" too: the list ends above it, not under it
            val tastiera = insets.getInsets(WindowInsetsCompat.Type.ime())
            val tastieraAperta = insets.isVisible(WindowInsetsCompat.Type.ime())
            testata.updatePadding(top = (20 * dp).toInt() + barre.top)
            lista.updatePadding(
                bottom = ((if (tastieraAperta) 16 else 110) * dp).toInt() + maxOf(barre.bottom, tastiera.bottom)
            )
            // With the keyboard open you're searching, not creating: "+ New" steps aside
            if (tastieraAperta) bottoneNuovo.hide() else bottoneNuovo.show()
            tastiExtra.isVisible = !tastieraAperta
            // Keyboard just closed without typing anything: the search closes by itself
            if (tastieraEraAperta && !tastieraAperta) chiudiRicercaSeVuota()
            tastieraEraAperta = tastieraAperta
            bottoneNuovo.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = (20 * dp).toInt() + barre.bottom
            }
            tastiExtra.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = (20 * dp).toInt() + barre.bottom
            }
            insets
        }

        NotificationHelper.creaCanale(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            chiediPermessoNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notaSospensione()
        }

        // Every time you open the app the alarms are queued again from scratch:
        // it costs nothing and repairs any alarm lost along the way
        PromemoriaManager.riprogrammaTutti(this)

        adattatore = PromemoriaAdapter(
            allaPressioneLunga = { riga -> nascondiTastiera(); apriEditor(riga) },
            allaPressioneLungaPassato = { riga -> nascondiTastiera(); confermaEliminaPassato(riga) },
            eliminaPassati = { confermaEliminaPassati() },
            alTocco = { passato -> suggerisci(passato) }
        )
        gestoreLista = LinearLayoutManager(this)
        lista.layoutManager = gestoreLista
        lista.adapter = adattatore
        // The list doesn't remember by itself where it had got to: when Android rebuilds the
        // screen it starts again from the next one, as at every launch (see righeLette), with no
        // old saved position that could get in the way.
        lista.isSaveEnabled = false

        lista.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(vista: RecyclerView, stato: Int) {
                if (stato == RecyclerView.SCROLL_STATE_DRAGGING) {
                    nascondiTastiera()
                    chiudiRicercaSeVuota()
                }
            }
            // Scrolling down, "+ New" shrinks to a circle; scrolling back up it widens again
            override fun onScrolled(vista: RecyclerView, dx: Int, dy: Int) {
                if (dy > 6 && bottoneNuovo.isExtended) bottoneNuovo.shrink()
                else if (dy < -6 && !bottoneNuovo.isExtended) bottoneNuovo.extend()
            }
        })

        statoNotifiche.setOnClickListener { mostraDettagliSalute() }
        bottoneCerca.setOnClickListener { apriChiudiRicerca() }
        findViewById<View>(R.id.bottonePassati).setOnClickListener { vaiAiPassati() }
        bottoneNuovo.setOnClickListener { nascondiTastiera(); apriEditor(null) }
        findViewById<View>(R.id.bottoneCaffe).setOnClickListener { apriDonazioni() }
        findViewById<View>(R.id.bottoneInfo).setOnClickListener { mostraInformazioni() }

        // The search: every letter typed filters the list again
        campoRicerca.doAfterTextChanged { mostra() }
        campoRicerca.setOnEditorActionListener { _, _, _ -> nascondiTastiera(); true }

        // "Back" closes the search first, and only then leaves the app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ricercaAperta) {
                    chiudiRicerca()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        if (savedInstanceState == null) apriDaRiquadro(intent)
    }

    /** The app was already open and the tile asks for something again. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        apriDaRiquadro(intent)
    }

    /** Tapping an item in the tile's window opens that reminder's sheet. */
    private fun apriDaRiquadro(intent: Intent) {
        // "+ New" in the tile's window: empty sheet, like the app's button
        if (intent.getBooleanExtra(EXTRA_NUOVO, false)) {
            intent.removeExtra(EXTRA_NUOVO)
            apriEditor(null)
            return
        }
        val titolo = intent.getStringExtra(EXTRA_TITOLO) ?: return
        val partenza = intent.getStringExtra(EXTRA_PARTENZA)
        intent.removeExtra(EXTRA_TITOLO)    // used once: turning the screen doesn't reopen it
        intent.removeExtra(EXTRA_PARTENZA)
        aggiornaLista()
        val riga = righe.firstOrNull {
            !it.passato && it.promemoria.titolo == titolo && it.promemoria.partenza.toString() == partenza
        } ?: return
        apriEditor(riga)
    }

    private fun apriEditor(riga: RigaDati?) {
        editor?.foglio?.dismiss()
        editor = EditorPromemoria(this, riga) { aggiornaLista() }.also { it.mostra() }
    }

    /** Reads the file again and recalculates everything: rows, counters, status at the top. */
    private fun aggiornaLista() {
        val adesso = LocalDateTime.now()
        righe = PromemoriaManager.carica(this).mapIndexed { indice, p ->
            val prossima = p.prossimaVolta(adesso)
            // A finished repetition shows among the past ones with its LAST time
            RigaDati(indice, p, quando = prossima ?: p.ultimaVolta(), passato = prossima == null)
        }
        val passati = righe.count { it.passato }
        numeroPassati.isVisible = passati > 0
        numeroPassati.text = if (passati > 99) "99+" else passati.toString()
        aggiornaSalute()
        righeLette = true
        mostra()
    }

    /**
     * Lines up the list's items.
     * Without search: PAST, the past ones from the oldest, TODAY, the blue card, the upcoming ones.
     * With search: only what matches, always in time order.
     */
    private fun mostra() {
        val adesso = LocalDateTime.now()
        val cerca = normalizza(campoRicerca.text?.toString() ?: "")
        val passati = righe.filter { it.passato }.sortedBy { it.quando }
        val futuri = righe.filter { !it.passato }.sortedBy { it.quando }
        val voci = mutableListOf<Voce>()

        // The change of year: "2027" before the first row of a year different from the one above.
        // It starts from the current year, so this year's rows at the top don't get one.
        // conLinea: the timeline runs through it (not at the top, nor before the blue card)
        val oggi = LocalDate.now()
        var anno = oggi.year
        fun separaAnno(q: LocalDateTime, passato: Boolean, conLinea: Boolean) {
            if (q.year == anno) return
            anno = q.year
            voci += Voce.Anno(anno, passato, conLinea)
        }
        // The TODAY bookmark. If there are past ones from another year above, it also shows the year
        // ("TODAY · SUNDAY 8 NOVEMBER 2026") instead of a separator stuck on top of it
        fun segnalibroOggi(): Voce.Oggi {
            val giorno = Formati.giornoLungo(oggi) + if (anno != oggi.year) " ${oggi.year}" else ""
            anno = oggi.year
            return Voce.Oggi(getString(R.string.oggi_marcatore, giorno.uppercase(Lingua.locale)))
        }

        if (cerca.isEmpty()) {
            if (passati.isNotEmpty()) {
                voci += Voce.TestataPassati
                passati.forEachIndexed { i, r ->
                    separaAnno(r.quando, passato = true, conLinea = i > 0)
                    voci += Voce.Passato(r, primo = i == 0)
                }
                voci += segnalibroOggi()
            }
            futuri.firstOrNull()?.let { separaAnno(it.quando, passato = false, conLinea = false) }
            voci += Voce.Prossimo(futuri.firstOrNull(), conCoda = futuri.size > 1)
            val dopo = futuri.drop(1)
            dopo.forEachIndexed { i, r ->
                separaAnno(r.quando, passato = false, conLinea = true)
                voci += Voce.Futuro(r, primo = false, ultimo = i == dopo.lastIndex)
            }
        } else {
            val trovaPassati = passati.filter { normalizza(testoRicerca(it)).contains(cerca) }
            val trovaFuturi = futuri.filter { normalizza(testoRicerca(it)).contains(cerca) }
            if (trovaPassati.isEmpty() && trovaFuturi.isEmpty()) voci += Voce.Vuoto(getString(R.string.nessun_risultato))
            if (trovaPassati.isNotEmpty()) {
                trovaPassati.forEachIndexed { i, r ->
                    separaAnno(r.quando, passato = true, conLinea = i > 0)
                    voci += Voce.Passato(r, primo = i == 0)
                }
                voci += segnalibroOggi()
            }
            trovaFuturi.forEachIndexed { i, r ->
                separaAnno(r.quando, passato = false, conLinea = i > 0)
                voci += Voce.Futuro(r, primo = i == 0, ultimo = i == trovaFuturi.lastIndex)
            }
        }
        adattatore.aggiorna(voci, adesso)

        if (daPosizionare && righeLette && cerca.isEmpty()) {
            daPosizionare = false
            gestoreLista.scrollToPositionWithOffset(adattatore.posizioneDi { it is Voce.Prossimo }, 0)
        }
    }

    /**
     * The button with the clock: takes you up to the past ones.
     * If you're already up there, it brings you back down to the next one.
     */
    private fun vaiAiPassati() {
        if (righe.none { it.passato }) {
            Toast.makeText(this, R.string.nessun_passato, Toast.LENGTH_SHORT).show()
            return
        }
        if (ricercaAperta) chiudiRicerca()
        val giaSu = gestoreLista.findFirstVisibleItemPosition() == 0
        scorriA(if (giaSu) adattatore.posizioneDi { it is Voce.Prossimo } else 0)
        bottoneNuovo.extend()
    }

    /** Scrolls smoothly until that item is at the top. */
    private fun scorriA(posizione: Int) {
        if (posizione < 0) return
        val scorritore = object : LinearSmoothScroller(this) {
            override fun getVerticalSnapPreference() = SNAP_TO_START
        }
        scorritore.targetPosition = posizione
        gestoreLista.startSmoothScroll(scorritore)
    }

    /** Press and hold a past one: "Delete this reminder?" */
    private fun confermaEliminaPassato(riga: RigaDati) {
        val p = riga.promemoria
        val messaggio = "\"${p.titolo}\" · ${Formati.quandoLungo(riga.quando)}" +
            if (p.siRipete) "\n${p.descrizioneRipetizione()}" else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.elimina_passato_titolo)
            .setMessage(messaggio)
            .setPositiveButton(R.string.elimina) { _, _ ->
                PromemoriaManager.rimuovi(this, riga.indice)
                aggiornaLista()
            }
            .setNegativeButton(R.string.annulla, null)
            .show()
            .eliminaInRosso().titoloIntero()
    }

    /** Deletes all the expired reminders at once, after confirmation. */
    private fun confermaEliminaPassati() {
        val scaduti = righe.count { it.passato }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.passati_elimina_titolo)
            .setMessage(getString(R.string.passati_elimina_messaggio, scaduti))
            .setPositiveButton(R.string.elimina) { _, _ ->
                PromemoriaManager.rimuoviPassati(this)
                aggiornaLista()
            }
            .setNegativeButton(R.string.annulla, null)
            .show()
            .eliminaInRosso().titoloIntero()
    }

    // --- Search ---

    /** The magnifier: opens the box with the keyboard ready; tapped again (now it's an ✕), it closes it. */
    private fun apriChiudiRicerca() {
        if (ricercaAperta) {
            chiudiRicerca()
        } else {
            ricercaAperta = true
            campoRicerca.isVisible = true
            bottoneCerca.setImageResource(R.drawable.ic_chiudi)
            campoRicerca.requestFocus()
            val tastiera = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            tastiera.showSoftInput(campoRicerca, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun chiudiRicerca() {
        val cercavaQualcosa = !campoRicerca.text.isNullOrBlank()

        // Before going back to the full list: which reminder is at the top of the screen,
        // and at what height? (titles and bookmark don't count: we look for the first real reminder)
        var inCima: RigaDati? = null
        var altezza = 0
        val primo = gestoreLista.findFirstVisibleItemPosition()
        val ultimo = gestoreLista.findLastVisibleItemPosition()
        if (primo >= 0) {
            for (pos in primo..ultimo) {
                val riga = adattatore.rigaIn(pos) ?: continue
                inCima = riga
                altezza = (gestoreLista.findViewByPosition(pos)?.top ?: lista.paddingTop) - lista.paddingTop
                break
            }
        }

        ricercaAperta = false
        campoRicerca.setText("")   // no "ghost" filters once the box is closed
        campoRicerca.isVisible = false
        bottoneCerca.setImageResource(R.drawable.ic_cerca)
        nascondiTastiera()

        // After a real search the list changes: you stay on the reminder you were looking at
        // (or on the next one, if the search hadn't found anything).
        // If you hadn't typed anything, the list is the same and you stay where you are.
        if (cercavaQualcosa) {
            val pos = inCima?.let { adattatore.posizioneDiRiga(it.indice) } ?: -1
            if (pos >= 0) gestoreLista.scrollToPositionWithOffset(pos, altezza)
            else gestoreLista.scrollToPositionWithOffset(adattatore.posizioneDi { it is Voce.Prossimo }, 0)
        }
    }

    /** Search open but empty, and meanwhile you do something else (scroll, close the keyboard): it closes by itself. */
    private fun chiudiRicercaSeVuota() {
        if (ricercaAperta && campoRicerca.text.isNullOrBlank()) chiudiRicerca()
    }

    /**
     * Closes the keyboard and removes the cursor from the search box:
     * if the field stays "active", Android keeps the keyboard open forever.
     */
    private fun nascondiTastiera() {
        val tastiera = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        tastiera.hideSoftInputFromWindow(campoRicerca.windowToken, 0)
        campoRicerca.clearFocus()
    }

    /** All the "searchable" text: day, year, day name, title, repetition. */
    private fun testoRicerca(riga: RigaDati): String {
        val giorno = riga.quando.toLocalDate()
        val p = riga.promemoria
        return "${Formati.giornoCorto(giorno)} ${Formati.dataBreve(giorno)} ${Formati.giornoLungo(giorno)} ${giorno.year} " +
            "${p.titolo} ${p.descrizioneRipetizione()}"
    }

    /** Lowercase and without accents: so "mercoledi" finds "mercoledì". */
    private fun normalizza(s: String): String =
        s.lowercase()
            .replace('à', 'a').replace('è', 'e').replace('é', 'e')
            .replace('ì', 'i').replace('ò', 'o').replace('ù', 'u')

    // --- Notification health ---

    /** "Notifications on · 5 upcoming" in green, or "Notifications at risk" in orange. */
    private fun aggiornaSalute() {
        val esito = DiagnosticaNotifiche.controlla(this)
        val inArrivo = righe.count { !it.passato }
        val conta =
            if (inArrivo > 0) resources.getQuantityString(R.plurals.stato_in_arrivo, inArrivo, inArrivo)
            else getString(R.string.stato_niente)
        statoNotifiche.text = when {
            esito.tuttoOk -> "${getString(R.string.salute_ok)} · $conta"
            // the only problem is "Do Not Disturb": it's named, it's the easiest one to understand
            esito.problemi.size == 1 && esito.nonDisturbareDaSistemare -> getString(R.string.salute_ko_non_disturbare)
            else -> getString(R.string.salute_ko)
        }
        val tinta = ContextCompat.getColor(this, if (esito.tuttoOk) R.color.verde else R.color.arancio)
        statoNotifiche.setTextColor(tinta)
        TextViewCompat.setCompoundDrawableTintList(statoNotifiche, ColorStateList.valueOf(tinta))
    }

    /**
     * First launch only: if Reminder isn't opened for a few months, Android can pause it
     * ("hibernation") and that wipes its alarms. The app can't switch this off by itself:
     * the note says so once and opens the page where the user can do it.
     * Not shown where it can't happen (before Android 11) or if it's already switched off.
     */
    private fun notaSospensione() {
        val preferenze = getSharedPreferences("preferenze", Context.MODE_PRIVATE)
        if (preferenze.getBoolean(NOTA_SOSPENSIONE, false)) return
        if (Build.VERSION.SDK_INT < 30 || packageManager.isAutoRevokeWhitelisted) return
        fun vista() = preferenze.edit().putBoolean(NOTA_SOSPENSIONE, true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nota_sospensione_titolo)
            .setMessage(R.string.nota_sospensione_testo)
            .setNegativeButton(R.string.ho_capito) { _, _ -> vista() }
            .setPositiveButton(R.string.apri_impostazioni) { _, _ ->
                vista()
                startActivity(IntentCompat.createManageUnusedAppRestrictionsIntent(this, packageName))
            }
            .setOnCancelListener { vista() }   // back or a tap outside: seen all the same
            .show().titoloIntero()
    }

    /** The hint shown right now: another tap replaces it instead of queueing behind it. */
    private var suggerimento: Toast? = null

    /**
     * A normal tap on a reminder: to edit it (or delete it, if it's past) you press and
     * hold, but someone using the app for the first time can't guess it. They're told at the bottom.
     */
    private fun suggerisci(passato: Boolean) {
        suggerimento?.cancel()
        val testo = if (passato) R.string.suggerimento_elimina else R.string.suggerimento_modifica
        suggerimento = Toast.makeText(this, testo, Toast.LENGTH_SHORT).also { it.show() }
    }

    /** Tap on the green line: explains what's wrong and offers how to fix it. */
    /** The coffee: opens the donations page in the browser. It's the browser that goes
     *  online, not the app: that's why the app doesn't need the internet permission. */
    private fun apriDonazioni() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LINK_DONAZIONI)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, LINK_DONAZIONI, Toast.LENGTH_LONG).show()  // no browser: at least the address
        }
    }

    /** The "i": the language drop-down, then version, privacy and thanks. */
    private fun mostraInformazioni() {
        // We read the version from the installed app: it's the "1.0" written in build.gradle.kts
        val info = if (Build.VERSION.SDK_INT >= 33)
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
        // The view is born with the dialog's theme: so our buttons are identical to Android's
        val costruttore = MaterialAlertDialogBuilder(this)
        val vista = LayoutInflater.from(costruttore.context).inflate(R.layout.finestra_info, null)
        vista.findViewById<TextView>(R.id.testoInfo).text = getString(R.string.info_messaggio, info.versionName)
        val finestra = costruttore
            .setTitle(R.string.app_name)
            .setView(vista)
            .show()
        // The buttons are ours, not the dialog's: Android would put them in a row or stacked
        // depending on their length. This way they're always stacked, coffee on top, in every language.
        vista.findViewById<View>(R.id.tastoCaffe).setOnClickListener { finestra.dismiss(); apriDonazioni() }
        vista.findViewById<View>(R.id.tastoChiudi).setOnClickListener { finestra.dismiss() }
        vista.findViewById<View>(R.id.linkLicenze).setOnClickListener { mostraLicenze() }

        // The drop-down: each language written in its own language, as all apps do.
        // It shows the one the app is speaking now, chosen by hand or taken from the phone.
        val attuale = getString(R.string.codice_lingua)
        val nomi = arrayOf(getString(R.string.lingua_it), getString(R.string.lingua_en))
        val campo = vista.findViewById<MaterialAutoCompleteTextView>(R.id.campoLingua)
        campo.setSimpleItems(nomi)
        campo.setText(nomi[SceltaLingua.CODICI.indexOf(attuale).coerceAtLeast(0)], false)
        campo.setOnItemClickListener { _, _, posizione, _ ->
            val scelta = SceltaLingua.CODICI[posizione]
            if (scelta != attuale) {
                finestra.dismiss()
                SceltaLingua.scegli(this, scelta)   // the screen reloads, already translated
            }
        }
    }

    /** The full texts of the two licences, in English: only the originals are valid. Opens on top of the "i". */
    private fun mostraLicenze() {
        fun leggi(id: Int) = resources.openRawResource(id).bufferedReader().use { it.readText() }
        val testo = buildSpannedString {
            bold { append("Plus Jakarta Sans\n") }
            append("Copyright 2020 The Plus Jakarta Sans Project Authors\n\n")
            append(leggi(R.raw.licenza_ofl))
            bold { append("\n\nAndroidX, Material Components, Material icons, Kotlin\n\n") }
            append(leggi(R.raw.licenza_apache))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.licenze_titolo)
            .setMessage(testo)   // a dialog's message already scrolls by itself
            .setPositiveButton(R.string.chiudi, null)
            .show()
    }

    private fun mostraDettagliSalute() {
        val esito = DiagnosticaNotifiche.controlla(this)
        val finestra = MaterialAlertDialogBuilder(this).setTitle(R.string.salute_titolo)

        if (esito.tuttoOk) {
            finestra.setMessage(R.string.salute_ok_messaggio)
                .setPositiveButton(R.string.chiudi, null)
        } else {
            finestra.setMessage(getString(R.string.salute_ko_messaggio, esito.problemi.joinToString("\n")))
            if (esito.notificheDaSistemare) {
                finestra.setPositiveButton(R.string.salute_fix_notifiche) { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                }
            } else if (esito.nonDisturbareDaSistemare) {
                // The "Do Not Disturb" page, where you choose the apps that are allowed to ring
                finestra.setPositiveButton(R.string.salute_fix_non_disturbare) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
                }
            } else if (esito.sveglieDaSistemare && Build.VERSION.SDK_INT >= 31) {
                // The main button is free: the exact alarms use it (Android 12 only)
                finestra.setPositiveButton(R.string.salute_fix_sveglie) { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                }
            }
            if (esito.batteriaDaSistemare) {
                finestra.setNeutralButton(R.string.salute_fix_batteria) { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            finestra.setNegativeButton(R.string.chiudi, null)
        }
        finestra.show().titoloIntero()
    }

    /**
     * The clock's "tick": Android sends it at every change of minute (and when the
     * date, time or time zone change). While the app is on screen the list is recalculated: the blue
     * card goes from "in 7 minutes" to "in 6 minutes", the reminder that just passed moves down among
     * the past ones, and at midnight "TODAY" changes day.
     */
    private val orologio = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = aggiornaLista()
    }

    /** On coming back to the app (also from the system settings) everything is checked again. */
    override fun onResume() {
        super.onResume()
        aggiornaLista()
        val tic = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        // NOT_EXPORTED: only Android's signals arrive, no other app can knock here
        ContextCompat.registerReceiver(this, orologio, tic, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** App no longer on screen: stops listening to the clock (no pointless work). */
    override fun onPause() {
        super.onPause()
        unregisterReceiver(orologio)
    }
}
