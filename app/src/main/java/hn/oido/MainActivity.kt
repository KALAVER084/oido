package hn.oido

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var marco: FrameLayout
    private var controller: MediaController? = null
    private var futuro: ListenableFuture<MediaController>? = null
    @Volatile private var paginaLista = false
    private val main = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newFixedThreadPool(3)
    private val archivoListas by lazy { File(filesDir, "listas.json") }
    private var ultimaRevision = 0L

    // ---------- Karaoke ----------
    private val gestorPantallas by lazy { getSystemService(DisplayManager::class.java) }
    private var tv: KaraokeTv? = null
    private var karaokeLocal: VistaKaraoke? = null   // karaoke encima de la app (teléfono girado)
    private var letraAbierta = false

    private val oyentePantallas = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = revisarPantallaExterna()
        override fun onDisplayRemoved(id: Int) = revisarPantallaExterna()
        override fun onDisplayChanged(id: Int) = revisarPantallaExterna()
    }

    /** Si hay una tele conectada (cable, dock, Miracast), muestra el karaoke ahí. */
    private fun revisarPantallaExterna() {
        val externa = gestorPantallas?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.firstOrNull()
        val actual = tv
        if (externa != null && actual != null && actual.display.displayId == externa.displayId) return
        actual?.let { runCatching { it.dismiss() } }
        tv = null
        if (externa != null) {
            val nueva = KaraokeTv(this, externa) { js("window.__pantallaExterna && window.__pantallaExterna(true)") }
            tv = runCatching { nueva.show(); nueva }.getOrNull()
        }
        if (tv == null) js("window.__pantallaExterna && window.__pantallaExterna(false)")
        actualizarPantallaEncendida()
    }

    /** Pantalla siempre encendida mientras se ve la letra completa o hay karaoke en la tele. */
    private fun actualizarPantallaEncendida() {
        if (letraAbierta || tv != null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private val http by lazy { OkHttpClient() }

    // ---------- Archivos de respaldo de listas ----------
    private var pendienteGuardar: Pair<String, String>? = null   // (petición, texto)
    private var pendienteAbrir: String? = null                   // petición
    private var importacionPendiente: String? = null             // llegó con "Abrir con Oído"

    /** Ventana de Android "Guardar en…" (Descargas, Drive, etc.). */
    private val guardarDocumento = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val (req, texto) = pendienteGuardar ?: return@registerForActivityResult
        pendienteGuardar = null
        if (uri == null) { responderJs(req, true, "false"); return@registerForActivityResult }
        trabajo.execute {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray()) }
                responderJs(req, true, "true")
            } catch (e: Exception) {
                responderJs(req, false, JSONObject.quote(e.message ?: "error"))
            }
        }
    }

    /** Ventana para elegir el archivo a importar. */
    private val abrirDocumento = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val req = pendienteAbrir ?: return@registerForActivityResult
        pendienteAbrir = null
        if (uri == null) { responderJs(req, true, "null"); return@registerForActivityResult }
        trabajo.execute {
            val texto = runCatching { leerTexto(uri) }.getOrNull()
            if (texto == null) responderJs(req, false, JSONObject.quote("No se pudo leer"))
            else responderJs(req, true, JSONObject.quote(texto))
        }
    }

    private fun leerTexto(uri: Uri): String? =
        contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun responderJs(req: String, ok: Boolean, datos: String) =
        js("window.__respuesta(${JSONObject.quote(req)}, $ok, $datos)")

    /** "Abrir con Oído" o "Compartir con Oído" desde WhatsApp, el correo o Archivos. */
    private fun recibirArchivo(recibido: Intent?) {
        val i = recibido ?: return
        val uri: Uri = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> uriAdjunto(i)
            else -> null
        } ?: return
        trabajo.execute {
            val texto = runCatching { leerTexto(uri) }.getOrNull() ?: return@execute
            main.post {
                if (paginaLista) web.evaluateJavascript("window.__importar(${JSONObject.quote(texto)})", null)
                else importacionPendiente = texto
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun uriAdjunto(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recibirArchivo(intent)
    }

    private val reloj = object : Runnable {
        override fun run() {
            enviarEstado()
            main.postDelayed(this, 250) // 4 veces por segundo: la letra va al ritmo
        }
    }

    private val oyente = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = enviarEstado()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // La app siempre es oscura: íconos claros en las barras del sistema
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        marco = FrameLayout(this)
        marco.setBackgroundColor(0xFF0E1615.toInt()) // fondo oscuro mientras carga
        web = WebView(this)
        web.setBackgroundColor(0xFF0E1615.toInt())
        marco.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(marco)
        // Que la interfaz no quede debajo de la barra de estado, la de navegación ni el teclado.
        ViewCompat.setOnApplyWindowInsetsListener(marco) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.webChromeClient = WebChromeClient() // permite confirm() para borrar listas
        web.webViewClient = object : WebViewClient() {
            // La interfaz es local: no se navega a ningún sitio...
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            // ...y cualquier petición que no sea de la propia app se bloquea (nada de anuncios ni rastreo).
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (request.url.toString().startsWith("file:///android_asset/")) null
                else WebResourceResponse("text/plain", "utf-8", 403, "Bloqueado", emptyMap(), ByteArrayInputStream(ByteArray(0)))

            override fun onPageFinished(view: WebView, url: String) {
                paginaLista = true
                importacionPendiente?.let {
                    web.evaluateJavascript("window.__importar(${JSONObject.quote(it)})", null)
                    importacionPendiente = null
                }
                enviarCola()
                enviarEstado()
                revisarActualizacion()
                if (tv != null) js("window.__pantallaExterna && window.__pantallaExterna(true)")
            }
        }
        web.addJavascriptInterface(Puente(), "OidoNativo")
        web.loadUrl("file:///android_asset/index.html")
        recibirArchivo(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.__atras ? window.__atras() : false") { r ->
                    if (r != "true") moveTaskToBack(true) // sale sin cortar la música
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        revisarActualizacion()
        gestorPantallas?.registerDisplayListener(oyentePantallas, main)
        revisarPantallaExterna()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        futuro = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(oyente)
            enviarCola()
            enviarEstado()
            main.removeCallbacks(reloj)
            main.post(reloj)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        gestorPantallas?.unregisterDisplayListener(oyentePantallas)
        tv?.let { runCatching { it.dismiss() } }
        tv = null
        main.removeCallbacks(reloj)
        controller?.removeListener(oyente)
        futuro?.let { MediaController.releaseFuture(it) }
        controller = null
        futuro = null
        super.onStop()
    }

    override fun onDestroy() {
        karaokeLocal?.destruir()
        karaokeLocal = null
        paginaLista = false
        web.destroy()
        trabajo.shutdown()
        super.onDestroy()
    }

    // ---------- ¿Hay versión nueva? ----------

    /**
     * Mira el último APK publicado en el repositorio público de descargas.
     * Se revisa al abrir la app y, si sigue abierta, cada 6 horas como mucho.
     */
    private fun revisarActualizacion() {
        if (!paginaLista || System.currentTimeMillis() - ultimaRevision < 6 * 60 * 60 * 1000L) return
        ultimaRevision = System.currentTimeMillis()
        val actual = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        if (actual == null || actual == "local") return
        trabajo.execute {
            runCatching {
                val pedido = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/$REPO_DESCARGAS/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                http.newCall(pedido).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val j = JSONObject(r.body?.string() ?: return@use)
                    val ultima = j.optString("tag_name").removePrefix("v")
                    val archivos = j.optJSONArray("assets") ?: JSONArray()
                    val apk = (0 until archivos.length()).map { archivos.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk") }
                        ?.optString("browser_download_url") ?: return@use
                    // Las versiones son fechas (2026.10.23-1530): comparar el texto basta
                    if (ultima > actual) {
                        val aviso = JSONObject().put("version", ultima).put("url", apk)
                        js("window.__actualizacion && window.__actualizacion($aviso)")
                    }
                }
            }
        }
    }

    // ---------- Hacia la interfaz ----------

    private fun js(codigo: String) {
        main.post { if (paginaLista) web.evaluateJavascript(codigo, null) }
    }

    private fun enviarEstado() {
        val c = controller ?: return
        val estado = JSONObject()
            .put("hay", c.mediaItemCount > 0)
            .put("index", c.currentMediaItemIndex)
            .put("total", c.mediaItemCount)
            .put("titulo", c.mediaMetadata.title?.toString() ?: "")
            .put("sonando", c.isPlaying)
            .put("quiere", c.playWhenReady)
            .put("cargando", c.playbackState == Player.STATE_BUFFERING)
            .put("pos", c.currentPosition)
            .put("dur", if (c.duration == C.TIME_UNSET) 0 else c.duration)
            .put("error", c.playerError != null)
            .put("vivo", c.isCurrentMediaItemLive)
        js("window.__estado && window.__estado($estado)")
    }

    /** Si la app se reabre mientras suena algo, la interfaz recupera la cola. */
    private fun enviarCola() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val items = JSONArray()
        for (i in 0 until c.mediaItemCount) items.put(aJson(c.getMediaItemAt(i)))
        val cola = JSONObject()
            .put("fuente", Cola.fuente ?: "")
            .put("titulo", Cola.titulo ?: "")
            .put("i", c.currentMediaItemIndex)
            .put("items", items)
        js("window.__restaurar && window.__restaurar($cola)")
    }

    private fun aJson(m: MediaItem) = JSONObject()
        .put("tipo", "video")
        .put("id", m.mediaId)
        .put("titulo", m.mediaMetadata.title?.toString() ?: "")
        .put("canal", m.mediaMetadata.artist?.toString() ?: "")
        .put("duracion", m.mediaMetadata.extras?.getString("duracion") ?: "")
        .put("vivo", m.mediaMetadata.extras?.getBoolean("vivo") ?: false)

    private fun aMediaItem(o: JSONObject): MediaItem {
        val extras = Bundle().apply {
            putString("duracion", o.optString("duracion"))
            putBoolean("vivo", o.optBoolean("vivo"))
        }
        return MediaItem.Builder()
            .setMediaId(o.getString("id"))
            .setUri("oido://audio/" + o.getString("id"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(o.optString("titulo"))
                    .setArtist(o.optString("canal"))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun aMediaItems(json: String): List<MediaItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { aMediaItem(arr.getJSONObject(it)) }
    }

    // ---------- Desde la interfaz ----------

    inner class Puente {
        private fun responder(req: String, ok: Boolean, datos: String) = responderJs(req, ok, datos)

        private fun enSegundoPlano(req: String, tarea: () -> String) = trabajo.execute {
            try {
                responder(req, true, tarea())
            } catch (e: Throwable) {
                responder(req, false, JSONObject.quote(e.message ?: e.toString()))
            }
        }

        private fun conControl(accion: (MediaController) -> Unit) = main.post { controller?.let(accion) }

        @JavascriptInterface fun buscar(req: String, q: String) = enSegundoPlano(req) { Extractor.buscar(q) }
        @JavascriptInterface fun abrirLista(req: String, url: String) = enSegundoPlano(req) { Extractor.abrirLista(url) }

        @JavascriptInterface fun leerListas(): String =
            runCatching { archivoListas.readText() }.getOrDefault("[]")

        @JavascriptInterface fun guardarListas(json: String) {
            val tmp = File(filesDir, "listas.json.tmp")
            tmp.writeText(json)
            tmp.renameTo(archivoListas)
        }

        @JavascriptInterface fun reproducir(itemsJson: String, indice: Int, fuente: String, titulo: String) {
            val items = aMediaItems(itemsJson)
            Cola.fuente = fuente
            Cola.titulo = titulo
            conControl {
                it.setMediaItems(items, indice, 0)
                it.prepare()
                it.play()
            }
        }

        /** Abre la descarga del APK nuevo en el navegador (solo enlaces de GitHub). */
        @JavascriptInterface fun abrirEnlace(url: String) {
            if (!url.startsWith("https://github.com/")) return
            main.post { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
        }

        /** Letras: pide a LRCLIB (solo ese sitio). Devuelve el texto, o null si no existe. */
        @JavascriptInterface fun pedir(req: String, url: String) = enSegundoPlano(req) {
            require(url.startsWith("https://lrclib.net/api/")) { "Dirección no permitida" }
            val pedido = okhttp3.Request.Builder().url(url)
                .header("User-Agent", "Oido (https://github.com/KALAVER084/oido)").build()
            http.newCall(pedido).execute().use { r ->
                when {
                    r.code == 404 -> "null"
                    !r.isSuccessful -> throw IllegalStateException("LRCLIB respondió ${r.code}")
                    else -> JSONObject.quote(r.body?.string() ?: "")
                }
            }
        }

        /** Guardar copia de las listas: abre "Guardar en…". */
        @JavascriptInterface fun guardarArchivo(req: String, texto: String, nombre: String) {
            main.post {
                pendienteGuardar = req to texto
                guardarDocumento.launch(nombre)
            }
        }

        /** Importar: abre el selector de archivos. */
        @JavascriptInterface fun abrirArchivo(req: String) {
            main.post {
                pendienteAbrir = req
                abrirDocumento.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }
        }

        /** Enviar las listas por WhatsApp, correo, etc. */
        @JavascriptInterface fun compartirArchivo(texto: String, nombre: String) {
            main.post {
                runCatching {
                    val carpeta = File(cacheDir, "compartir").apply { mkdirs() }
                    val archivo = File(carpeta, nombre).apply { writeText(texto) }
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.archivos", archivo)
                    val enviar = Intent(Intent.ACTION_SEND)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "Mis listas de Oído")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(enviar, "Enviar mis listas"))
                }
            }
        }

        /** Karaoke: canción, letra o posición, para la tele y para el karaoke del teléfono. */
        @JavascriptInterface fun karaoke(json: String) {
            main.post {
                tv?.enviar(json)
                karaokeLocal?.enviar(json)
            }
        }

        /** Teléfono girado con la letra abierta: muestra u oculta el karaoke encima de la app. */
        @JavascriptInterface fun karaokeLocal(activo: Boolean) {
            main.post {
                if (activo && karaokeLocal == null) {
                    val v = VistaKaraoke(this@MainActivity) { js("window.__karaokeListo && window.__karaokeListo()") }
                    marco.addView(v.web, FrameLayout.LayoutParams(-1, -1))
                    karaokeLocal = v
                } else if (!activo) {
                    karaokeLocal?.let { marco.removeView(it.web); it.destruir() }
                    karaokeLocal = null
                }
            }
        }

        /**
         * Letra a pantalla completa abierta/cerrada. Abierta: el teléfono gira con el sensor
         * (aunque la rotación esté bloqueada) para el karaoke en horizontal, y no se apaga la pantalla.
         */
        @JavascriptInterface fun letraCompleta(abierta: Boolean) {
            main.post {
                letraAbierta = abierta
                requestedOrientation = if (abierta) ActivityInfo.SCREEN_ORIENTATION_SENSOR
                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                actualizarPantallaEncendida()
            }
        }

        /** Compartir Oído: abre el menú de compartir de Android con el mensaje y el enlace. */
        @JavascriptInterface fun compartirTexto(texto: String) {
            main.post {
                runCatching {
                    val enviar = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                    startActivity(Intent.createChooser(enviar, "Compartir Oído"))
                }
            }
        }

        /** Copia un enlace al portapapeles (botón de compartir). */
        @JavascriptInterface fun copiar(texto: String) {
            main.post {
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Oído", texto))
            }
        }

        /** El tema cambió: pinta las franjas de arriba y abajo con el mismo fondo. */
        @JavascriptInterface fun colorFondo(color: String) {
            main.post { runCatching { marco.setBackgroundColor(android.graphics.Color.parseColor(color)) } }
        }

        @JavascriptInterface fun alternar() = conControl { if (it.playWhenReady) it.pause() else it.play() }
        @JavascriptInterface fun siguiente() = conControl { it.seekToNextMediaItem() }
        @JavascriptInterface fun anterior() = conControl { it.seekToPrevious() }
        @JavascriptInterface fun irA(ms: Double) = conControl { it.seekTo(ms.toLong()) }
        @JavascriptInterface fun agregar(itemsJson: String) {
            val items = aMediaItems(itemsJson)
            conControl { it.addMediaItems(items) }
        }
        @JavascriptInterface fun quitar(i: Int) = conControl { if (i in 0 until it.mediaItemCount) it.removeMediaItem(i) }
        @JavascriptInterface fun mover(de: Int, a: Int) = conControl {
            if (de in 0 until it.mediaItemCount && a in 0 until it.mediaItemCount) it.moveMediaItem(de, a)
        }
    }
}

/** Repositorio (público) donde GitHub publica cada APK nuevo. */
const val REPO_DESCARGAS = "KALAVER084/oido"

/** De dónde viene la cola que suena (para mostrarlo al reabrir la app). */
object Cola {
    @Volatile var fuente: String? = null
    @Volatile var titulo: String? = null
}
