package world.ebuzz.tv.core.catalog

import android.content.Context
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
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import world.ebuzz.tv.core.catalog.databinding.FragmentCatalogBinding
import world.ebuzz.tv.core.data.container
import world.ebuzz.tv.core.ui.KeyHandler
import world.ebuzz.tv.core.ui.chip
import world.ebuzz.tv.core.ui.factory
import world.ebuzz.tv.core.ui.selectChip

/** The poster-grid screen. A feature subclasses it and supplies its [CatalogSource]; nothing else. */
abstract class CatalogFragment : Fragment(), KeyHandler {
    protected abstract fun source(context: Context): CatalogSource

    private var _b: FragmentCatalogBinding? = null
    private val b get() = _b!!
    private val src by lazy { source(requireContext().applicationContext) }
    private val vm: CatalogViewModel by viewModels { factory { CatalogViewModel(src, requireContext().container.catalogState) } }
    private val adapter = PosterAdapter { src.open(requireContext(), it.id) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentCatalogBinding.inflate(inflater, container, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.search.hint = src.searchHint
        b.grid.layoutManager = GridLayoutManager(requireContext(), spanCount())
        b.grid.adapter = adapter
        b.state.onRetry = vm::retry
        b.search.doAfterTextChanged { vm.setQuery(it?.toString().orEmpty()) }
        b.grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as GridLayoutManager
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.itemCount - lm.spanCount * 3) vm.loadMore()
            }
        })
        src.sorts().forEach { s -> b.sorts.addView(requireContext().chip(s.label, s) { vm.selectSort(s) }) }
        b.chips.addView(requireContext().chip("All", ALL) { vm.selectCategory(null) })
        src.categories().forEach { c -> b.chips.addView(requireContext().chip(c.name, c.id) { vm.selectCategory(c.id) }) }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect(::render) } }
    }

    private fun render(s: CatalogUiState) {
        adapter.submitList(s.tiles)
        b.sorts.selectChip(s.sort); b.chips.selectChip(s.categoryId ?: ALL)
        b.shortcut.visibility = if (s.shortcut != null) View.VISIBLE else View.GONE
        s.shortcut?.let { sc -> b.shortcut.text = sc.label; b.shortcut.setOnClickListener { sc.open(requireContext()) } }
        when { s.error -> b.state.showError(); s.empty -> b.state.showEmpty(); s.loading -> b.state.showLoading(); else -> b.state.hide() }
    }

    override fun onResume() { super.onResume(); vm.refreshShortcut(); if (vm.state.value.error) vm.retry() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        _b?.let { (it.grid.layoutManager as GridLayoutManager).spanCount = spanCount() }
    }

    private fun spanCount() = (resources.configuration.screenWidthDp / 124).coerceIn(2, 10)

    override fun onKey(keyCode: Int, event: KeyEvent) = false

    override fun onDestroyView() { _b = null; super.onDestroyView() }

    private companion object { const val ALL = "all" }
}
