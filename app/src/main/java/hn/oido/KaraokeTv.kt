package hn.oido

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Karaoke en una segunda pantalla (tele por cable, dock o Miracast).
 * Muestra karaoke.html; la app le manda la canción, la letra y la posición.
 */
class KaraokeTv(contexto: Context, pantalla: Display, private val alListo: () -> Unit) :
    Presentation(contexto, pantalla) {

    private var web: WebView? = null
    private var listo = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val w = WebView(context)
        w.setBackgroundColor(0xFF0E1615.toInt())
        w.settings.javaScriptEnabled = true
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            // Solo archivos de la propia app; nada de internet en la tele
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (request.url.toString().startsWith("file:///android_asset/")) null
                else WebResourceResponse("text/plain", "utf-8", 403, "Bloqueado", emptyMap(), ByteArrayInputStream(ByteArray(0)))

            override fun onPageFinished(view: WebView, url: String) {
                listo = true
                alListo()
            }
        }
        setContentView(w)
        w.loadUrl("file:///android_asset/karaoke.html")
        web = w
    }

    fun enviar(json: String) {
        if (listo) web?.evaluateJavascript("window.__karaoke($json)", null)
    }

    override fun onStop() {
        runCatching { web?.destroy() }
        web = null
        super.onStop()
    }
}
