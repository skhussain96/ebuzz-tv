package world.ebuzz.tv.core.link

import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.Toast
import org.json.JSONObject
import world.ebuzz.tv.core.playback.HandOff

// The one list both directions go through. It stays live while open: devices appear and disappear as they join the Wi-Fi.
object DevicePicker {
    private class Row(val peer: Peer, var now: JSONObject? = null, var resume: JSONObject? = null, var asked: Boolean = false) {
        val item get() = now ?: resume
        override fun toString() = peer.name + when {
            !asked -> ""
            now != null -> "\nPlaying · " + HandOff.title(now!!) + at(now!!)
            resume != null -> "\nStopped · " + HandOff.title(resume!!) + at(resume!!)
            else -> "\nNothing to continue"
        }
        private fun at(o: JSONObject) = o.optLong("pos").takeIf { it > 0 }?.let { ms -> " · %d:%02d:%02d".format(ms / 3600000, ms / 60000 % 60, ms / 1000 % 60) }.orEmpty()
    }

    // "Play on…": hand what is on this screen to another device, then stop here
    fun send(activity: Activity, item: JSONObject) = open(activity, "Play on", query = false, accepts = { item.optString("kind") in it.kinds }) { row, close ->
        DeviceLink.request(row.peer, JSONObject().put("cmd", "play").put("item", item)) { reply ->
            if (reply?.optBoolean("ok") == true) { close(); HandOff.source?.stop() } else toast(activity, "${row.peer.name} did not respond")
        }
    }

    // "Continue here": pull whatever another device is playing, then stop it there
    fun pull(activity: Activity) = open(activity, "Continue from", query = true) { row, close ->
        val item = row.item ?: return@open toast(activity, "Nothing to continue on ${row.peer.name}")
        if (!DeviceLink.play(item)) return@open toast(activity, "Can't play that here")
        if (row.now != null) DeviceLink.request(row.peer, JSONObject().put("cmd", "stop")) {}
        close()
    }

    private fun open(activity: Activity, title: String, query: Boolean, accepts: (Peer) -> Boolean = { true }, onPick: (Row, close: () -> Unit) -> Unit) {
        // a live-TV-only edition lists a device only while it is actually playing a channel; the full edition lists every device
        val liveOnly = query && DeviceLink.kinds == setOf("live")
        val all = ArrayList<Row>()
        val rows = ArrayList<Row>()
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, rows)
        val dialog = AlertDialog.Builder(activity).setTitle(title).setAdapter(adapter, null).setNegativeButton("Close", null).create()
        dialog.listView.setOnItemClickListener { _, _, pos, _ -> onPick(rows[pos]) { dialog.dismiss() } }
        lateinit var refresh: () -> Unit
        refresh = {
            val peers = DeviceLink.peers.filter(accepts)
            all.removeAll { r -> peers.none { it.id == r.peer.id } }
            peers.filter { p -> all.none { it.peer.id == p.id } }.forEach { p ->
                val row = Row(p).also(all::add)
                if (query) DeviceLink.request(p, JSONObject().put("cmd", "query")) { reply ->
                    row.asked = true
                    row.now = reply?.optJSONObject("now")?.takeIf(DeviceLink::handles); row.resume = reply?.optJSONObject("resume")?.takeIf(DeviceLink::handles)
                    refresh()
                }
            }
            rows.clear(); rows.addAll(if (liveOnly) all.filter { it.now != null } else all)
            dialog.setTitle(when { rows.isNotEmpty() -> title; liveOnly && all.any { it.asked } -> "$title · no device is playing TV"; else -> "$title · looking for devices…" })
            adapter.notifyDataSetChanged()
        }
        DeviceLink.onPeersChanged = refresh
        dialog.setOnDismissListener { DeviceLink.onPeersChanged = null }
        dialog.show(); refresh()
    }

    private fun toast(a: Activity, text: String) = Toast.makeText(a, text, Toast.LENGTH_SHORT).show()
}
