package world.ebuzz.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import world.ebuzz.tv.databinding.ActivityChannelsBinding

class ChannelsActivity : AppCompatActivity() {
    private lateinit var b: ActivityChannelsBinding
    private val adapter = ChannelAdapter { open(it.number) }
    private lateinit var digits: DigitEntry
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        b = ActivityChannelsBinding.inflate(layoutInflater).also { setContentView(it.root) }
        b.grid.layoutManager = GridLayoutManager(this, spanCount())
        b.grid.adapter = adapter
        digits = DigitEntry(b.chNum) { n -> if (n in 1..all.size) open(n) }
        b.search.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }
        b.search.setOnEditorActionListener { _, _, _ -> adapter.currentList.firstOrNull()?.let { open(it.number) }; true }
        load()
    }

    private fun load() {
        failed = false
        b.status.text = getString(R.string.loading); b.status.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { ChannelRepo.channels() }
                .onSuccess { list ->
                    all = list
                    adapter.submitList(list)
                    b.status.visibility = View.GONE
                    focusTile((lastWatched() - 1).coerceIn(0, list.lastIndex))
                }
                .onFailure { failed = true; b.status.text = getString(R.string.error) }
        }
    }

    private var all: List<Channel> = emptyList()

    private fun applyFilter(q: String) {
        val t = q.trim()
        adapter.submitList(if (t.isEmpty()) all else all.filter { it.title.contains(t, true) || it.number.toString().startsWith(t) })
    }

    private fun focusTile(pos: Int, attempt: Int = 0) {
        b.grid.scrollToPosition(pos)
        b.grid.post {
            val v = b.grid.findViewHolderForAdapterPosition(pos)?.itemView
            if (v != null) v.requestFocus() else if (attempt < 5) focusTile(pos, attempt + 1)
        }
    }

    private fun spanCount() = (resources.configuration.screenWidthDp / 190).coerceIn(2, 8)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        (b.grid.layoutManager as GridLayoutManager).spanCount = spanCount()
    }

    private fun lastWatched() = getSharedPreferences("ebuzz", MODE_PRIVATE).getInt("last", 1)

    private fun open(number: Int) =
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("number", number))

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!b.search.hasFocus() && digits.onKey(keyCode)) return true
        if (failed && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) { load(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
