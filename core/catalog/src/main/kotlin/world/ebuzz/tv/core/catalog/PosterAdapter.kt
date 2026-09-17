package world.ebuzz.tv.core.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import world.ebuzz.tv.core.ui.loadUrl
import world.ebuzz.tv.core.catalog.databinding.ItemPosterBinding

class PosterAdapter(private val onOpen: (PosterTile) -> Unit) : ListAdapter<PosterTile, PosterAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<PosterTile>() {
        override fun areItemsTheSame(a: PosterTile, b: PosterTile) = a.id == b.id
        override fun areContentsTheSame(a: PosterTile, b: PosterTile) = a == b
    }

    class VH(val b: ItemPosterBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply { media.clipToOutline = true })

    override fun onBindViewHolder(h: VH, pos: Int) {
        val t = getItem(pos)
        h.b.title.text = t.title
        h.b.sub.text = t.subtitle; h.b.sub.visibility = if (t.subtitle.isEmpty()) View.GONE else View.VISIBLE
        h.b.badge.text = t.badge; h.b.badge.visibility = if (t.badge.isEmpty()) View.GONE else View.VISIBLE
        h.b.poster.loadUrl(t.poster)
        h.b.root.setOnClickListener { onOpen(t) }
    }
}
