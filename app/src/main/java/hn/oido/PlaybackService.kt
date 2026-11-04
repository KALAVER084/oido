package hn.oido

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Reproduce el audio en un servicio en primer plano: sigue sonando con la pantalla apagada
 * o la app cerrada, y muestra controles en la notificación y en la pantalla de bloqueo.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val numeroPeticion = AtomicLong(0)
    private val reintentados = HashSet<String>()

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        // Cada canción entra como "oido://audio/ID"; justo al cargarla se pide su URL real.
        val resolvedor = ResolvingDataSource.Factory(http) { spec -> resolver(spec) }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvedor))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // pausa si entra una llamada u otra app reproduce
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .setWakeMode(C.WAKE_MODE_NETWORK)   // mantiene CPU y wifi con la pantalla apagada
            .build()

        // En vivos: siempre la calidad más baja (144p) para gastar pocos datos
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setForceLowestBitrate(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // En vivo pausado mucho rato: se quedó atrás, vuelve al momento actual
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }
                val id = player.currentMediaItem?.mediaId ?: return
                if (reintentados.add(id)) {
                    // Puede que la URL haya caducado: se pide otra y se reintenta una vez.
                    Extractor.olvidarAudio(id)
                    player.prepare()
                    player.play()
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                reintentados.retainAll(setOfNotNull(mediaItem?.mediaId))
            }
        })

        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(abrirApp)
            .setCallback(object : MediaSession.Callback {
                // Los elementos llegan sin URI desde la interfaz; se reconstruye a partir del id.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map {
                        val b = it.buildUpon().setUri("oido://audio/${it.mediaId}")
                        // Los en vivo se reproducen como HLS
                        if (it.mediaMetadata.extras?.getBoolean("vivo") == true) b.setMimeType(MimeTypes.APPLICATION_M3U8)
                        b.build()
                    }.toMutableList()
                )
            })
            .build()
    }

    private fun resolver(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != "oido") return spec
        val id = spec.uri.lastPathSegment ?: return spec
        val audio = try {
            Extractor.audio(id)
        } catch (e: Exception) {
            throw IOException("No se pudo obtener el audio de $id", e)
        }
        if (audio.vivo) {
            // La lista HLS se pide normal (GET); sus pedazos ya traen direcciones completas
            return spec.buildUpon()
                .setUri(Uri.parse(audio.url))
                .setHttpRequestHeaders(mapOf("User-Agent" to DownloaderImpl.USER_AGENT))
                .build()
        }
        var url = audio.url
        // Igual que NewPipe: número de petición, POST y user-agent según el cliente de YouTube.
        if (url.contains("/videoplayback") && !url.contains("&rn=")) {
            url += "&rn=" + numeroPeticion.incrementAndGet()
        }
        val cabeceras = HashMap<String, String>()
        cabeceras["User-Agent"] = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url))
            YoutubeParsingHelper.getVisionOsUserAgent(null) else DownloaderImpl.USER_AGENT
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            cabeceras["Origin"] = "https://www.youtube.com"
            cabeceras["Referer"] = "https://www.youtube.com"
        }
        return spec.buildUpon()
            .setUri(Uri.parse(url))
            .setHttpRequestHeaders(cabeceras)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(byteArrayOf(0x78, 0))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si cierras la app desde recientes y no está sonando nada, se apaga del todo.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
