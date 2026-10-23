package hn.oido

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * La pantalla del karaoke (karaoke.html). La misma se usa en la tele (segunda pantalla)
 * y encima de la app cuando giras el teléfono con la letra abierta.
 */
@SuppressLint("SetJavaScriptEnabled")
class VistaKaraoke(contexto: Context, private val alListo: () -> Unit) {
    val web = WebView(contexto)
    private var listo = false

    init {
        web.setBackgroundColor(0xFF0E1615.toInt())
        web.settings.javaScriptEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            // Solo archivos de la propia app; nada de internet
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (request.url.toString().startsWith("file:///android_asset/")) null
                else WebResourceResponse("text/plain", "utf-8", 403, "Bloqueado", emptyMap(), ByteArrayInputStream(ByteArray(0)))

            override fun onPageFinished(view: WebView, url: String) {
                listo = true
                alListo()
            }
        }
        web.loadUrl("file:///android_asset/karaoke.html")
    }

    fun enviar(json: String) {
        if (listo) web.evaluateJavascript("window.__karaoke($json)", null)
    }

    fun destruir() = runCatching { web.destroy() }
}
