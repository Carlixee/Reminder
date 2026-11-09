package com.ac.reminder

import android.content.Context
import android.util.TypedValue
import android.widget.LinearLayout
import androidx.core.view.children
import kotlin.math.roundToInt

/**
 * "Large text": the phone's font is so large that some things side by side
 * don't fit any more, and words would break in half ("Parrucchi-ere", "Settima…").
 * So those things are put one under the other (see [impila]).
 *
 * The measure: how much text fits in the screen's width. The SHORT side is used,
 * so turning the phone the answer doesn't change and the screen stays consistent.
 * Normal text on a normal phone: about 360. Below 320 = large text.
 */
fun Context.testoGrande(): Boolean {
    val m = resources.displayMetrics
    // How big a character is compared with normal (1 = normal, ~1.8 = at most)
    val ingrandimento = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, m) /
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, m)
    return resources.configuration.smallestScreenWidthDp / ingrandimento < 320
}

/**
 * From row to column: the pieces side by side go one under the other, [spazioDp] apart.
 * What stretched to fill the row now takes the full width;
 * what was as wide as its text (a pill, the time) stays that way, aligned left.
 */
fun LinearLayout.impila(spazioDp: Int) {
    orientation = LinearLayout.VERTICAL
    val spazio = (spazioDp * resources.displayMetrics.density).roundToInt()
    children.forEachIndexed { i, figlio ->
        val lp = figlio.layoutParams as LinearLayout.LayoutParams
        if (lp.width == 0) lp.width = LinearLayout.LayoutParams.MATCH_PARENT
        lp.weight = 0f
        lp.marginStart = 0
        lp.topMargin = if (i == 0) 0 else spazio
        figlio.layoutParams = lp
    }
}
