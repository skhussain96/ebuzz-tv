package world.ebuzz.tv.presentation.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import world.ebuzz.tv.domain.model.Movie
import world.ebuzz.tv.databinding.ItemMovieBinding

class MovieAdapter(private val onOpen: (Movie) -> Unit) : ListAdapter<Movie, MovieAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(a: Movie, b: Movie) = a.id == b.id
        override fun areContentsTheSame(a: Movie, b: Movie) = a == b
    }

    class VH(val b: ItemMovieBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply { media.clipToOutline = true })

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = getItem(pos)
        h.b.title.text = m.title
        h.b.sub.text = listOf(m.year, m.rating.takeIf(String::isNotEmpty)?.let { "★ $it" }.orEmpty()).filter(String::isNotEmpty).joinToString("  ·  ")
        h.b.badge.text = m.quality; h.b.badge.visibility = if (m.quality.isEmpty()) View.GONE else View.VISIBLE
        h.b.poster.load(m.poster) { crossfade(true); allowRgb565(true) }
        h.b.root.setOnClickListener { onOpen(m) }
    }
}
