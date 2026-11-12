package com.ac.reminder.dati

import android.content.Context
import android.content.SharedPreferences

/**
 * The app's small settings file on the phone, opened from one place only: favorite alerts,
 * chosen language, the notes already seen, the alerts' bookmark. Each piece keeps its own keys.
 *
 * DON'T change the name: whoever already has the app would lose all of those.
 */
object Preferenze {
    private const val NOME_FILE = "preferenze"

    fun apri(c: Context): SharedPreferences = c.getSharedPreferences(NOME_FILE, Context.MODE_PRIVATE)
}
