package hn.oido

import hn.oido.potoken.PoTokenProviderImpl
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Todo lo que habla con YouTube: buscar, abrir listas/mixes y obtener el audio. */
object Extractor {
    private val yt = ServiceList.YouTube
    private val hilos = Executors.newFixedThreadPool(3)
    /** Dónde está el audio: archivo normal, o lista HLS si es un en vivo. */
    data class Audio(val url: String, val vivo: Boolean)

    private val cacheAudio = ConcurrentHashMap<String, Pair<Audio, Long>>()

    private fun esVivo(t: StreamType?) = t == StreamType.LIVE_STREAM || t == StreamType.AUDIO_LIVE_STREAM
    private const val VIDA_URL_MS = 3 * 60 * 60 * 1000L   // las URL de audio caducan en ~6 h

    fun iniciar() {
        val locale = Locale.getDefault()
        val pais = locale.country.ifEmpty { "US" }
        NewPipe.init(DownloaderImpl.getInstance(), Localization.fromLocale(locale), ContentCountry(pais))
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
    }

    private fun idDeVideo(url: String): String? =
        Regex("""(?:v=|youtu\.be/|/shorts/)([\w-]{11})""").find(url)?.groupValues?.get(1)

    private fun duracion(seg: Long): String {
        if (seg <= 0) return ""
        val h = seg / 3600; val m = (seg % 3600) / 60; val s = seg % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun aJson(item: InfoItem): JSONObject? = when (item) {
        is StreamInfoItem -> idDeVideo(item.url)?.let { id ->
            JSONObject()
                .put("tipo", "video").put("id", id).put("titulo", item.name ?: "(sin título)")
                .put("canal", item.uploaderName ?: "").put("duracion", duracion(item.duration))
                .put("fecha", item.uploadDate?.instant?.epochSecond ?: JSONObject.NULL) // para ordenar por fecha
                .put("vivo", esVivo(item.streamType))
        }
        is PlaylistInfoItem -> JSONObject()
            .put("tipo", if (item.playlistType == PlaylistInfo.PlaylistType.NORMAL) "lista" else "mix")
            .put("id", item.url).put("titulo", item.name ?: "(sin título)")
            .put("canal", item.uploaderName ?: "")
        else -> null
    }

    private fun buscarCon(q: String, filtro: String): List<JSONObject> {
        val qh = yt.searchQHFactory.fromQuery(q, listOf(filtro), "")
        return SearchInfo.getInfo(yt, qh).relatedItems.mapNotNull { aJson(it) }
    }

    /** Videos, mixes y listas mezclados como en YouTube, más una pasada solo de listas. */
    fun buscar(q: String): String {
        val todo = hilos.submit(Callable { buscarCon(q, "all") })
        val listas = hilos.submit(Callable { buscarCon(q, "playlists") })
        val a = runCatching { todo.get() }
        val b = runCatching { listas.get() }
        if (a.isFailure && b.isFailure) throw a.exceptionOrNull()!!
        val vistos = HashSet<String>()
        val res = JSONArray()
        (a.getOrDefault(emptyList()) + b.getOrDefault(emptyList())).forEach {
            if (vistos.add(it.getString("id"))) res.put(it)
        }
        return res.toString()
    }

    /** Abre una lista o mix y devuelve {titulo, items}. */
    fun abrirLista(url: String): String {
        val info = PlaylistInfo.getInfo(yt, url)
        val items = info.relatedItems.toMutableList()
        var pagina = info.nextPage
        while (pagina != null && items.size < 200) {
            val mas = runCatching { PlaylistInfo.getMoreItems(yt, url, pagina) }.getOrNull() ?: break
            if (mas.items.isEmpty()) break
            items.addAll(mas.items)
            pagina = if (mas.hasNextPage()) mas.nextPage else null
        }
        val res = JSONArray()
        val vistos = HashSet<String>()
        items.mapNotNull { aJson(it) }.forEach { if (vistos.add(it.getString("id"))) res.put(it) }
        return JSONObject().put("titulo", info.name ?: "").put("items", res).toString()
    }

    /** URL del mejor audio del video, o la lista HLS si es un en vivo (se guarda un rato). */
    fun audio(id: String): Audio {
        cacheAudio[id]?.let { (a, cuando) ->
            if (System.currentTimeMillis() - cuando < VIDA_URL_MS) return a
        }
        val info = StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
        val resultado = if (esVivo(info.streamType)) {
            // En vivo: audio y video vienen juntos en HLS; la calidad se elige en el reproductor
            Audio(info.hlsUrl?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Este en vivo no tiene transmisión disponible"), true)
        } else {
            val candidatos = info.audioStreams.filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }
            val originales = candidatos.filter {
                it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
            }.ifEmpty { candidatos }
            val mejor = originales.maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
                ?: throw IllegalStateException("Este video no tiene audio disponible")
            Audio(mejor.content, false)
        }
        cacheAudio[id] = resultado to System.currentTimeMillis()
        return resultado
    }

    fun olvidarAudio(id: String) { cacheAudio.remove(id) }
}
