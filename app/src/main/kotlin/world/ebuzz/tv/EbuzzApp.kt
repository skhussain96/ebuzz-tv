package world.ebuzz.tv

import android.app.Application
import android.content.Context
import world.ebuzz.tv.di.AppContainer

class EbuzzApp : Application() {
    val container by lazy { AppContainer(this) }
}

val Context.container: AppContainer get() = (applicationContext as EbuzzApp).container
