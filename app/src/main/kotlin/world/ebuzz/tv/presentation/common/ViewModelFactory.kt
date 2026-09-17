package world.ebuzz.tv.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** `viewModels { factory { HomeViewModel(...) } }` without a DI framework. */
inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
