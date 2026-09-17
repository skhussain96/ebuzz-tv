package world.ebuzz.tv.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import world.ebuzz.tv.core.ui.databinding.ViewStateBinding

/**
 * The one loading / empty / error surface of the app. The error state has a real, focusable Retry button that
 * takes focus by itself, so a TV remote can press OK on it and a finger can tap it.
 */
class StateView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val b = ViewStateBinding.inflate(LayoutInflater.from(context), this)
    var onRetry: (() -> Unit)? = null

    init { b.retry.setOnClickListener { onRetry?.invoke() }; visibility = View.GONE }

    val isShowingError get() = visibility == View.VISIBLE && b.retry.visibility == View.VISIBLE

    fun hide() { visibility = View.GONE }

    fun showLoading() = show(spinner = true)

    fun showEmpty(message: String = "Nothing found") = show(title = message, icon = android.R.drawable.ic_menu_search)

    fun showError(
        title: String = context.getString(R.string.error_title),
        message: String = context.getString(R.string.error_message),
    ) {
        val wasError = isShowingError
        show(title = title, message = message, icon = android.R.drawable.stat_notify_error, retry = true)
        if (!wasError) b.retry.post { b.retry.requestFocus() }
    }

    private fun show(spinner: Boolean = false, title: String? = null, message: String? = null, icon: Int? = null, retry: Boolean = false) {
        visibility = View.VISIBLE
        b.spinner.visibility = if (spinner) View.VISIBLE else View.GONE
        b.icon.visibility = if (icon != null) View.VISIBLE else View.GONE; icon?.let(b.icon::setImageResource)
        b.title.visibility = if (title != null) View.VISIBLE else View.GONE; b.title.text = title
        b.message.visibility = if (message != null) View.VISIBLE else View.GONE; b.message.text = message
        b.retry.visibility = if (retry) View.VISIBLE else View.GONE
    }
}
