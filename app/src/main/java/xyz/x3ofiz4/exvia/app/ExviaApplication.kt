package xyz.x3ofiz4.exvia.app

import android.app.Application

/** Process-level composition root for Exvia's MVVM dependencies. */
class ExviaApplication : Application() {
    val container: ExviaContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ExviaContainer(this)
    }
}
