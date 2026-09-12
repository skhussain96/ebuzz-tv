package world.ebuzz.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
        b = ActivityChannelsBinding.inflate(layoutInflater).also { setContentView(it.root) }
        b.grid.layoutManager = GridLayoutManager(this, 6)
        b.grid.adapter = adapter
        digits = DigitEntry(b.chNum) { n -> if (n in 1..adapter.itemCount) open(n) }
        load()
    }

    private fun load() {
        failed = false
        b.status.text = getString(R.string.loading); b.status.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { ChannelRepo.channels() }
                .onSuccess { list ->
                    adapter.submitList(list)
                    b.status.visibility = View.GONE
                    b.grid.post { b.grid.findViewHolderForAdapterPosition(lastWatched() - 1)?.itemView?.requestFocus() ?: b.grid.requestFocus() }
                }
                .onFailure { failed = true; b.status.text = getString(R.string.error) }
        }
    }

    private fun lastWatched() = getSharedPreferences("ebuzz", MODE_PRIVATE).getInt("last", 1)

    private fun open(number: Int) =
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("number", number))

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (digits.onKey(keyCode)) return true
        if (failed && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) { load(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
