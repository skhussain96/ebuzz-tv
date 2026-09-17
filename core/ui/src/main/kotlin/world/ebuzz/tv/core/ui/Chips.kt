package world.ebuzz.tv.core.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.TextView

/** A pill used for sort and category rows. [key] goes into `tag` so selection can be re-applied on each state. */
fun Context.chip(label: String, key: Any, onClick: () -> Unit): TextView =
    TextView(ContextThemeWrapper(this, R.style.Chip), null, 0).apply {
        text = label; tag = key; maxLines = 1
        val m = (4 * resources.displayMetrics.density).toInt()
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = m; marginEnd = m }
        setOnClickListener { onClick() }
    }

fun LinearLayout.selectChip(key: Any?) { for (i in 0 until childCount) getChildAt(i).let { it.isSelected = it.tag == key } }
