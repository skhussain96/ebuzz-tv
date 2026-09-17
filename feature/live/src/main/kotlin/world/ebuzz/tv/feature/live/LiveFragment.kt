package world.ebuzz.tv.feature.live

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import world.ebuzz.tv.core.data.container
import world.ebuzz.tv.core.playback.PlayerIntents
import world.ebuzz.tv.core.ui.DigitEntry
import world.ebuzz.tv.core.ui.HomeSection
import world.ebuzz.tv.core.ui.KeyHandler
import world.ebuzz.tv.core.ui.factory
import world.ebuzz.tv.core.ui.keyboardOnlyOnClick
import world.ebuzz.tv.feature.live.databinding.FragmentLiveBinding

object LiveSection : HomeSection {
    override val id = "live"
    override val title = "Live TV"
    override fun newFragment(): Fragment = LiveFragment()
}

class LiveFragment : Fragment(), KeyHandler {
    private var _b: FragmentLiveBinding? = null
    private val b get() = _b!!
    private val vm: LiveViewModel by viewModels { factory { LiveViewModel(requireContext().container) } }
    private val adapter = ChannelAdapter { open(it.number) }
    private lateinit var digits: DigitEntry

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentLiveBinding.inflate(inflater, container, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.grid.layoutManager = GridLayoutManager(requireContext(), spanCount())
        b.grid.adapter = adapter
        b.state.onRetry = vm::load
        digits = DigitEntry(b.chNum) { n -> if (vm.hasChannel(n)) open(n) }
        b.search.keyboardOnlyOnClick()
        b.grid.requestFocus()                                   // never let the search box take first focus (it would open the TV keyboard)
        b.search.doAfterTextChanged { vm.setQuery(it?.toString().orEmpty()) }
        b.search.setOnEditorActionListener { _, _, _ -> vm.state.value.channels.firstOrNull()?.let { open(it.number) }; true }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect(::render) } }
    }

    private fun render(s: LiveUiState) {
        adapter.submitList(s.channels)
        when { s.error -> b.state.showError(); s.loading -> b.state.showLoading(); s.channels.isEmpty() && s.query.isNotBlank() -> b.state.showEmpty("No channel matches"); else -> b.state.hide() }
        s.focusNumber?.let { n -> vm.consumeFocus(); focusTile(s.channels.indexOfFirst { it.number == n }.coerceAtLeast(0)) }
    }

    /** Coming back to a failed screen (e.g. the network returned) tries again by itself. */
    override fun onResume() { super.onResume(); if (vm.state.value.error) vm.load() }

    private fun focusTile(pos: Int, attempt: Int = 0) {
        val grid = _b?.grid ?: return
        grid.scrollToPosition(pos)
        grid.post {
            val v = _b?.grid?.findViewHolderForAdapterPosition(pos)?.itemView
            if (v != null) v.requestFocus() else if (attempt < 5) focusTile(pos, attempt + 1)
        }
    }

    private fun open(number: Int) = startActivity(PlayerIntents.live(requireContext(), number))

    private fun spanCount() = (resources.configuration.screenWidthDp / 190).coerceIn(2, 8)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        _b?.let { (it.grid.layoutManager as GridLayoutManager).spanCount = spanCount() }
    }

    /** Typed digits jump to a channel number, unless the user is typing in the search box. */
    override fun onKey(keyCode: Int, event: KeyEvent): Boolean = _b != null && !b.search.hasFocus() && digits.onKey(keyCode)

    override fun onDestroyView() { _b = null; super.onDestroyView() }
}
