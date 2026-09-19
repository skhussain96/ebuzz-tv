package world.ebuzz.tv.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.abs

// A vertical column whose UP/DOWN focus moves row by row. Android's own search measures from a text field's caret
// and scores by centre distance, so from the search box or a small chip it jumps over a full-width row (the Resume
// pill) to a tile underneath. Here the next visible row always wins; inside that row the selected or nearest item does.
class FocusColumn @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LinearLayout(c, a) {
    init { orientation = VERTICAL }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (focused == null || (direction != View.FOCUS_UP && direction != View.FOCUS_DOWN)) return super.focusSearch(focused, direction)
        val from = (0 until childCount).firstOrNull { isInside(focused, getChildAt(it)) } ?: return super.focusSearch(focused, direction)
        val step = if (direction == View.FOCUS_DOWN) 1 else -1
        var i = from + step
        while (i in 0 until childCount) {
            target(getChildAt(i), focused, direction)?.let { return it }
            i += step
        }
        return super.focusSearch(focused, direction)
    }

    private fun enter(focused: View, direction: Int): View? {
        val order = if (direction == View.FOCUS_DOWN) 0 until childCount else (0 until childCount).reversed()
        for (i in order) target(getChildAt(i), focused, direction)?.let { return it }
        return null
    }

    private fun columnIn(v: View): FocusColumn? {
        if (!v.isShown) return null
        if (v is FocusColumn) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) columnIn(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun isInside(v: View, row: View): Boolean { var p: View? = v; while (p != null && p !== this) { if (p === row) return true; p = p.parent as? View }; return false }

    private fun target(row: View, focused: View, direction: Int): View? {
        if (row.visibility != View.VISIBLE) return null
        if (row !is ViewGroup) return row.takeIf { it.isFocusable && it.isEnabled }
        // a row that holds another column (the screen inside the home shell) is entered at that column's near end
        columnIn(row)?.let { return it.enter(focused, direction) }
        val options = row.getFocusables(direction).filter { it !== row && it.isShown }
        if (options.isEmpty()) return row.takeIf { it.isFocusable }
        options.firstOrNull { it.isSelected }?.let { return it }
        val at = IntArray(2); focused.getLocationOnScreen(at); val x = at[0] + focused.width / 2
        // nearest edge row first (top row going down, bottom row going up), then the closest column
        return options.minByOrNull { v -> v.getLocationOnScreen(at); (if (direction == View.FOCUS_DOWN) at[1] else -at[1]) * 10_000L + abs(at[0] + v.width / 2 - x) }
    }
}
