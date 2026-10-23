package hn.oido

import android.app.Application
import android.webkit.CookieManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Extractor.iniciar()
    }

    companion object {
        lateinit var instance: App
            private set

        fun supportsWebView(): Boolean = try {
            CookieManager.getInstance()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
