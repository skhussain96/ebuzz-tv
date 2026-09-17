package world.ebuzz.tv.presentation.home

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import world.ebuzz.tv.R
import world.ebuzz.tv.container
import world.ebuzz.tv.databinding.ActivityChannelsBinding
import world.ebuzz.tv.domain.model.MovieSort
import world.ebuzz.tv.domain.model.ResumePoint
import world.ebuzz.tv.presentation.common.DigitEntry
import world.ebuzz.tv.presentation.common.applyOrientation
import world.ebuzz.tv.presentation.common.factory
import world.ebuzz.tv.presentation.common.isTv
import world.ebuzz.tv.presentation.player.PlayerActivity

/** Renders [HomeUiState]; all decisions live in [HomeViewModel]. */
class HomeActivity : AppCompatActivity() {
    private lateinit var b: ActivityChannelsBinding
    private val vm: HomeViewModel by viewModels { factory { HomeViewModel(container, moviesAvailable = !isTv) } }
    private val channelAdapter = ChannelAdapter { openChannel(it.number) }
    private val movieAdapter = MovieAdapter { openMovie(it.id, it.title, it.streamUrl) }
    private lateinit var digits: DigitEntry
    private var shownTab: Boolean? = null
    private var builtChipsFor: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        b = ActivityChannelsBinding.inflate(layoutInflater).also { setContentView(it.root) }
        b.grid.layoutManager = GridLayoutManager(this, 2)
        digits = DigitEntry(b.chNum) { n -> if (vm.hasChannel(n)) openChannel(n) }

        b.tabs.visibility = if (isTv) View.GONE else View.VISIBLE      // Movies is a phone / tablet feature
        b.tabLive.setOnClickListener { vm.selectTab(false) }
        b.tabMovies.setOnClickListener { vm.selectTab(true) }
        b.status.setOnClickListener { if (vm.state.value.error) vm.retry() }
        b.search.doAfterTextChanged { vm.setQuery(it?.toString().orEmpty()) }
        b.search.setOnEditorActionListener { _, _, _ ->
            vm.state.value.takeIf { !it.moviesTab }?.channels?.firstOrNull()?.let { openChannel(it.number) }; true
        }
        b.grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || !vm.state.value.moviesTab) return
                val lm = rv.layoutManager as GridLayoutManager
                if (lm.findLastVisibleItemPosition() >= movieAdapter.itemCount - lm.spanCount * 3) vm.loadMoreMovies()
            }
        })
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect(::render) } }
    }

    override fun onResume() { super.onResume(); vm.refreshResume() }

    private fun render(s: HomeUiState) {
        if (shownTab != s.moviesTab) {
            shownTab = s.moviesTab
            b.tabLive.isSelected = !s.moviesTab; b.tabMovies.isSelected = s.moviesTab
            b.filters.visibility = if (s.moviesTab) View.VISIBLE else View.GONE
            b.search.hint = if (s.moviesTab) "Search movies" else "Search channels or number"
            if (b.search.text.toString() != s.query) b.search.setText(s.query)
            b.grid.adapter = if (s.moviesTab) movieAdapter else channelAdapter
            (b.grid.layoutManager as GridLayoutManager).spanCount = spanCount()
        }
        if (s.moviesTab) movieAdapter.submitList(s.movies) else channelAdapter.submitList(s.channels)
        renderSorts(s); renderChips(s)

        b.status.visibility = if (s.loading || s.error || s.empty) View.VISIBLE else View.GONE
        b.status.text = when { s.error -> getString(R.string.error); s.empty -> "Nothing found"; else -> getString(R.string.loading) }

        s.focusChannelNumber?.let { n -> vm.consumeFocus(); if (!s.moviesTab) focusTile(s.channels.indexOfFirst { it.number == n }.coerceAtLeast(0)) }
    }

    private fun renderSorts(s: HomeUiState) {
        if (b.sorts.childCount == 0) MovieSort.entries.forEach { sort ->
            b.sorts.addView(chip(sort.label, null) { vm.selectSort(sort) }.apply { tag = sort })
        }
        for (i in 0 until b.sorts.childCount) b.sorts.getChildAt(i).let { it.isSelected = it.tag == s.sort }
    }

    private fun renderChips(s: HomeUiState) {
        val key = s.categories.size * 31 + (s.resume?.movieId ?: 0)
        if (key != builtChipsFor) {
            builtChipsFor = key
            b.chips.removeAllViews()
            s.resume?.let { r -> b.chips.addView(chip("▶  Resume · ${r.title}", null) { openResume(r) }.apply { tag = RESUME; isSelected = true }) }
            b.chips.addView(chip("All", null) { vm.selectCategory(null) })
            s.categories.forEach { c -> b.chips.addView(chip(c.name, c.id) { vm.selectCategory(c.id) }) }
        }
        for (i in 0 until b.chips.childCount) b.chips.getChildAt(i).let { v -> if (v.tag != RESUME) v.isSelected = v.tag == (s.categoryId ?: ALL) } 
    }

    private fun chip(label: String, id: Int?, onClick: () -> Unit) =
        TextView(ContextThemeWrapper(this, R.style.Chip), null, 0).apply {
            text = label; tag = id ?: ALL; maxLines = 1
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = 4.dp; marginEnd = 4.dp }
            setOnClickListener { onClick() }
        }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun focusTile(pos: Int, attempt: Int = 0) {
        b.grid.scrollToPosition(pos)
        b.grid.post {
            val v = b.grid.findViewHolderForAdapterPosition(pos)?.itemView
            if (v != null) v.requestFocus() else if (attempt < 5) focusTile(pos, attempt + 1)
        }
    }

    private fun spanCount() = (resources.configuration.screenWidthDp / if (vm.state.value.moviesTab) 124 else 190).coerceIn(2, 10)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        (b.grid.layoutManager as GridLayoutManager).spanCount = spanCount()
    }

    private fun openChannel(number: Int) = startActivity(PlayerActivity.live(this, number))
    private fun openMovie(id: Int, title: String, url: String) = startActivity(PlayerActivity.movie(this, id, title, url))
    private fun openResume(r: ResumePoint) = openMovie(r.movieId, r.title, r.streamUrl)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = vm.state.value
        if (!s.moviesTab && !b.search.hasFocus() && digits.onKey(keyCode)) return true
        if (s.error && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) { vm.retry(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private companion object { const val ALL = "all"; const val RESUME = "resume" }
}
