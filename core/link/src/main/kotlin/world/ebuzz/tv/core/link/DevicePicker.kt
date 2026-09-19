package world.ebuzz.tv.core.link

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import world.ebuzz.tv.core.playback.HandOff
import world.ebuzz.tv.core.ui.R as UiR

// The one sheet both directions go through. It stays live while open: devices appear and disappear as they join the Wi-Fi.
// Everything is reachable by D-pad; a row reports its own progress and failure, so nothing depends on a toast.
object DevicePicker {
    private enum class Phase { IDLE, BUSY, FAILED, DONE }

    private class Row(val peer: Peer) {
        var now: JSONObject? = null; var resume: JSONObject? = null; var asked = false
        var phase = Phase.IDLE; var note: String? = null
        val item get() = now ?: resume
    }

    private fun at(o: JSONObject) = o.optLong("pos").takeIf { it > 0 }?.let { ms -> if (ms >= 3600000) "%d:%02d:%02d".format(ms / 3600000, ms / 60000 % 60, ms / 1000 % 60) else "%d:%02d".format(ms / 60000, ms / 1000 % 60) }

    // "Play on a TV": hand what is on this screen to a television, then stop here
    fun send(activity: Activity, item: JSONObject) = open(
        activity, title = "Play on a TV", subtitle = listOfNotNull(HandOff.title(item), at(item)?.let { "from $it" }).joinToString("  ·  "),
        searching = "Looking for TVs on this Wi-Fi…", hint = "Open TV or TV+ on the television. Phone and TV must be on the same Wi-Fi.",
        query = false, accepts = { it.tv && item.optString("kind") in it.kinds },
    ) { row, update, close ->
        row.phase = Phase.BUSY; row.note = "Connecting…"; update()
        DeviceLink.request(row.peer, JSONObject().put("cmd", "play").put("item", item)) { reply ->
            if (reply?.optBoolean("ok") == true) { row.phase = Phase.DONE; row.note = "Playing here now"; update(); close(650) { HandOff.source?.stop() } }
            else { row.phase = Phase.FAILED; row.note = if (reply == null) "Didn't respond. Is the app open on the TV? Select to retry" else "This TV can't play that"; update() }
        }
    }

    // On a TV: pull what a phone is playing (or the film it stopped), then stop it there
    fun pull(activity: Activity) = open(
        activity, title = "Continue from a phone", subtitle = "Pick up what a phone is playing, right where it is",
        searching = "Looking for phones on this Wi-Fi…", hint = "Open TV or TV+ on the phone and play something. Phone and TV must be on the same Wi-Fi.",
        query = true, accepts = { !it.tv },
    ) { row, update, close ->
        val item = row.item
        if (item == null) { row.phase = Phase.FAILED; row.note = "Nothing playing there yet"; update(); return@open }
        if (!DeviceLink.play(item)) { row.phase = Phase.FAILED; row.note = "Can't play that on this device"; update(); return@open }
        if (row.now != null) DeviceLink.request(row.peer, JSONObject().put("cmd", "stop")) {}
        close(0) {}
    }

    private fun open(
        activity: Activity, title: String, subtitle: String, searching: String, hint: String, query: Boolean,
        accepts: (Peer) -> Boolean, onPick: (Row, update: () -> Unit, close: (delayMs: Long, then: () -> Unit) -> Unit) -> Unit,
    ) {
        val c = activity; val dp = c.resources.displayMetrics.density; fun Int.dp() = (this * dp).toInt()
        val ink = c.getColor(UiR.color.ink); val dim = c.getColor(UiR.color.inkDim); val honey = c.getColor(UiR.color.honey); val bad = c.getColor(UiR.color.bad)
        val main = Handler(Looper.getMainLooper())
        // a live-TV-only edition lists a phone only while it is actually playing a channel
        val liveOnly = query && DeviceLink.kinds == setOf("live")
        val all = ArrayList<Row>()

        fun text(size: Float, color: Int, bold: Boolean = false) = TextView(c).apply { textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD }
        fun icon(res: Int, tint: Int, size: Int) = ImageView(c).apply { setImageResource(res); imageTintList = ColorStateList.valueOf(tint); layoutParams = LinearLayout.LayoutParams(size.dp(), size.dp()) }

        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val empty = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(8.dp(), 18.dp(), 8.dp(), 10.dp())
            addView(ProgressBar(c).apply { indeterminateTintList = ColorStateList.valueOf(honey); layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()) })
        }
        val emptyTitle = text(16f, ink, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, 14.dp(), 0, 6.dp()) }.also(empty::addView)
        text(13.5f, dim).apply { gravity = Gravity.CENTER; this.text = hint; setLineSpacing(0f, 1.15f) }.also(empty::addView)

        val cancel = TextView(c, null, 0, UiR.style.Chip).apply { this.text = "Cancel"; gravity = Gravity.CENTER; minWidth = 120.dp() }
        val sheet = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(UiR.drawable.sheet_bg); setPadding(20.dp(), 20.dp(), 20.dp(), 16.dp())
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(icon(UiR.drawable.ic_cast, honey, 26))
                addView(text(20f, ink, bold = true).apply { this.text = title; setPadding(12.dp(), 0, 0, 0) })
            })
            addView(text(14f, dim).apply { this.text = subtitle; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, 6.dp(), 0, 12.dp()) })
            addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp()); addView(LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; addView(empty); addView(list) }) },
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(LinearLayout(c).apply { gravity = Gravity.END; setPadding(0, 12.dp(), 0, 0); addView(cancel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)) })
        }
        val dialog = Dialog(c).apply {
            setContentView(sheet)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setDimAmount(0.72f)
                val m = c.resources.displayMetrics
                setLayout(minOf((440 * dp).toInt(), m.widthPixels - 32.dp()), minOf((420 * dp).toInt(), m.heightPixels - 48.dp()))
            }
        }
        cancel.setOnClickListener { dialog.dismiss() }
        val close: (Long, () -> Unit) -> Unit = { delay, then -> main.postDelayed({ if (dialog.isShowing) dialog.dismiss(); then() }, delay) }

        lateinit var render: () -> Unit
        fun rowView(r: Row): View = LinearLayout(c).apply {
            tag = r.peer.id; orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(UiR.drawable.tile_bg); isFocusable = true; isClickable = true; setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = 10.dp() }
            addView(icon(if (r.peer.tv) UiR.drawable.ic_tv else UiR.drawable.ic_phone, if (r.phase == Phase.FAILED) bad else honey, 30))
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; setPadding(14.dp(), 0, 10.dp(), 0)
                addView(text(16.5f, ink, bold = true).apply { this.text = r.peer.name; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                val status = r.note ?: when {
                    !query -> if (r.peer.kinds == setOf("live")) "TV  ·  live channels only" else "TV+  ·  channels, films and music"
                    !r.asked -> "Checking…"
                    r.now != null -> "Playing  ·  " + listOfNotNull(HandOff.title(r.now!!), at(r.now!!)).joinToString("  ·  ")
                    r.resume != null -> "Stopped  ·  " + listOfNotNull(HandOff.title(r.resume!!), at(r.resume!!)).joinToString("  ·  ")
                    else -> "Nothing to continue"
                }
                addView(text(13.5f, if (r.phase == Phase.FAILED) bad else dim).apply { this.text = status; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, 3.dp(), 0, 0) })
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            when (r.phase) {
                Phase.BUSY -> addView(ProgressBar(c).apply { indeterminateTintList = ColorStateList.valueOf(honey); layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp()) })
                Phase.DONE -> addView(icon(UiR.drawable.ic_check, honey, 24))
                else -> addView(icon(UiR.drawable.ic_play, dim, 22))
            }
            setOnClickListener { if (r.phase != Phase.BUSY && r.phase != Phase.DONE) onPick(r, render, close) }
        }

        var waitedLong = false
        render = render@{
            if (!dialog.isShowing) return@render
            val rows = if (liveOnly) all.filter { it.now != null } else all
            val focusedId = list.focusedChild?.tag
            list.removeAllViews(); rows.forEach { list.addView(rowView(it)) }
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            emptyTitle.text = when { liveOnly && all.any { it.asked } -> "No phone is playing a channel right now"; waitedLong -> "Still looking…"; else -> searching }
            // keep the D-pad where it was; a first device takes focus so OK works at once
            ((0 until list.childCount).map(list::getChildAt).firstOrNull { it.tag == focusedId } ?: list.getChildAt(0).takeIf { focusedId != null || !cancel.isFocused || rows.size == 1 })?.requestFocus()
        }
        val refresh: () -> Unit = {
            val peers = DeviceLink.peers.filter(accepts)
            all.removeAll { r -> peers.none { it.id == r.peer.id } }
            peers.filter { p -> all.none { it.peer.id == p.id } }.forEach { p ->
                val row = Row(p).also(all::add)
                if (query) DeviceLink.request(p, JSONObject().put("cmd", "query")) { reply ->
                    row.asked = true
                    row.now = reply?.optJSONObject("now")?.takeIf(DeviceLink::handles); row.resume = reply?.optJSONObject("resume")?.takeIf(DeviceLink::handles)
                    render()
                }
            }
            render()
        }
        DeviceLink.onPeersChanged = refresh
        dialog.setOnDismissListener { DeviceLink.onPeersChanged = null; main.removeCallbacksAndMessages(null) }
        dialog.show(); refresh()
        main.postDelayed({ waitedLong = true; render() }, 8000)
    }
}
