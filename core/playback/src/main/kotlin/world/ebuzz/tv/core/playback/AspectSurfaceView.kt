package world.ebuzz.tv.core.playback

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView

/** A video surface that letterboxes itself to the stream's aspect ratio inside whatever space it is given. */
class AspectSurfaceView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : SurfaceView(c, a) {
    var aspect = 16f / 9f
        set(v) { if (v > 0 && v != field) { field = v; requestLayout() } }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec); val h = MeasureSpec.getSize(heightSpec)
        if (w == 0 || h == 0) { super.onMeasure(widthSpec, heightSpec); return }
        if (w.toFloat() / h > aspect) setMeasuredDimension((h * aspect).toInt(), h) else setMeasuredDimension(w, (w / aspect).toInt())
    }
}
