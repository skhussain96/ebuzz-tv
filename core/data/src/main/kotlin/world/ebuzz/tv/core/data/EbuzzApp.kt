package world.ebuzz.tv.core.data

import android.app.Application
import android.content.Context

class EbuzzApp : Application() {
    val container by lazy { AppContainer(this) }
}

val Context.container: AppContainer get() = (applicationContext as EbuzzApp).container
