package world.ebuzz.tv.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ImageLoader {
    private val pool = Executors.newFixedThreadPool(6)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtMost(16 shl 20)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(view: ImageView, url: String?) {
        view.setTag(R.id.image_url, url)
        if (url.isNullOrBlank()) { view.setImageDrawable(null); return }
        cache.get(url)?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        val target = view.width.takeIf { it > 0 } ?: 360
        pool.execute {
            if (view.getTag(R.id.image_url) != url) return@execute      // scrolled past while queued
            val bmp = runCatching { fetch(url, target) }.getOrNull() ?: return@execute
            cache.put(url, bmp)
            main.post { if (view.getTag(R.id.image_url) == url) view.setImageBitmap(bmp) }
        }
    }

    private fun fetch(url: String, targetWidth: Int): Bitmap? {
        val bytes = (URL(url.replace(" ", "%20")).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000; readTimeout = 15_000
            try { inputStream.use { it.readBytes() } } finally { disconnect() }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
    }
}

fun ImageView.loadUrl(url: String?) = ImageLoader.load(this, url)
