package world.ebuzz.tv.core.link

import android.app.Activity
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.Toast
import org.json.JSONObject
import world.ebuzz.tv.core.playback.HandOff

// The one list both directions go through. It stays live while open: devices appear and disappear as they join the Wi-Fi.
object DevicePicker {
    private class Row(val peer: Peer, var now: JSONObject? = null, var asked: Boolean = false) {
        override fun toString() = peer.name + when { !asked -> ""; now == null -> "\nNothing playing"; else -> "\nPlaying · " + HandOff.title(now!!) }
    }

    // "Play on…": hand what is on this screen to another device, then stop here
    fun send(activity: Activity, item: JSONObject) = open(activity, "Play on", query = false) { row, close ->
        DeviceLink.request(row.peer, JSONObject().put("cmd", "play").put("item", item)) { reply ->
            if (reply?.optBoolean("ok") == true) { close(); HandOff.source?.stop() } else toast(activity, "${row.peer.name} did not respond")
        }
    }

    // "Continue here": pull whatever another device is playing, then stop it there
    fun pull(activity: Activity) = open(activity, "Continue from", query = true) { row, close ->
        val item = row.now ?: return@open toast(activity, "Nothing playing on ${row.peer.name}")
        if (!DeviceLink.play(item)) return@open toast(activity, "Can't play that here")
        DeviceLink.request(row.peer, JSONObject().put("cmd", "stop")) {}
        close()
    }

    private fun open(activity: Activity, title: String, query: Boolean, onPick: (Row, close: () -> Unit) -> Unit) {
        val rows = ArrayList<Row>()
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, rows)
        val dialog = AlertDialog.Builder(activity).setTitle(title).setAdapter(adapter, null).setNegativeButton("Close", null).create()
        dialog.listView.setOnItemClickListener { _, _, pos, _ -> onPick(rows[pos]) { dialog.dismiss() } }
        fun refresh() {
            val peers = DeviceLink.peers
            rows.removeAll { r -> peers.none { it.id == r.peer.id } }
            peers.filter { p -> rows.none { it.peer.id == p.id } }.forEach { p ->
                val row = Row(p).also(rows::add)
                if (query) DeviceLink.request(p, JSONObject().put("cmd", "query")) { reply -> row.asked = true; row.now = reply?.optJSONObject("now"); adapter.notifyDataSetChanged() }
            }
            dialog.setTitle(if (rows.isEmpty()) "$title · looking for devices…" else title)
            adapter.notifyDataSetChanged()
        }
        DeviceLink.onPeersChanged = ::refresh
        dialog.setOnDismissListener { DeviceLink.onPeersChanged = null }
        dialog.show(); refresh()
    }

    private fun toast(a: Activity, text: String) = Toast.makeText(a, text, Toast.LENGTH_SHORT).show()
}
