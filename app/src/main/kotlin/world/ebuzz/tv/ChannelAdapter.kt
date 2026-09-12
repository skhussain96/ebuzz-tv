package world.ebuzz.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import world.ebuzz.tv.databinding.ItemChannelBinding

class ChannelAdapter(private val onOpen: (Channel) -> Unit) :
    ListAdapter<Channel, ChannelAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }

    class VH(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = getItem(pos)
        h.b.num.text = c.number.toString()
        h.b.title.text = c.title
        h.b.poster.load(c.poster) { crossfade(true); allowRgb565(true) }
        h.b.root.setOnClickListener { onOpen(c) }
    }
}
