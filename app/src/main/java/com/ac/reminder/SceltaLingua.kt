package com.ac.reminder

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ac.reminder.dati.Lingua
import java.util.Locale

/**
 * The languages drop-down, in the "i" dialog.
 *
 * The real work is done by Android: we tell it "this app speaks English" and it
 * reloads the screens taking the texts from res/values (English) instead of
 * from res/values-it (Italian). From 13 onwards the choice also shows up in
 * Settings → Apps → Reminder → Language, and it applies to the whole app.
 *
 * Below Android 13 instead it only applies to the screens: notifications and tile
 * find it again thanks to contesto(), which reads the copy saved here.
 */
object SceltaLingua {

    private const val FILE = "preferenze"
    private const val CHIAVE = "lingua"

    /** The drop-down's items. Until a choice is made, Android's rule applies:
     *  Italian if the phone is in Italian, otherwise English ("" saved = no choice). */
    val CODICI = listOf("it", "en")

    private fun salvata(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(CHIAVE, "") ?: ""

    /** Changes language: Android reloads the screen by itself, already translated. */
    fun scegli(c: Context, codice: String) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(CHIAVE, codice).apply()
        aggiornaTesti(c.applicationContext)
        AppCompatDelegate.setApplicationLocales(
            if (codice.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(codice)
        )
    }

    /** Below Android 13: a "context" that speaks the chosen language, for notifications and tile. */
    fun contesto(c: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return c
        val codice = salvata(c)
        if (codice.isEmpty()) return c
        val conf = Configuration(c.resources.configuration).apply { setLocale(Locale.forLanguageTag(codice)) }
        return c.createConfigurationContext(conf)
    }

    /**
     * Tells the code (dates, repetitions, notification text) which language to write in:
     * the same as the screens. We ask the text files themselves (codice_lingua
     * is "it" in values-it, "en" elsewhere): so whoever has the phone in German
     * sees everything in English, screens and dates together, never mixed.
     */
    fun aggiornaTesti(c: Context) {
        val codice = contesto(c).getString(R.string.codice_lingua)
        Lingua.locale = if (codice == "it") Locale.ITALIAN else Locale.ENGLISH
    }
}

/** The whole app: it starts first, even when it's woken up by a notification or the tile. */
class ReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SceltaLingua.aggiornaTesti(this)
    }

    override fun onConfigurationChanged(nuova: Configuration) {
        super.onConfigurationChanged(nuova)
        SceltaLingua.aggiornaTesti(this)   // language changed from Android's Settings
    }
}
