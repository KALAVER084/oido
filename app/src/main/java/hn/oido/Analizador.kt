package hn.oido

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Recibe una copia del audio que sale del reproductor (sin pedir permisos: es el audio de la propia app)
 * y lo convierte en 16 bandas, de graves a agudos, para que el karaoke mueva sus líneas con la música.
 * Solo trabaja mientras hay alguien escuchando (oyente != null).
 */
@OptIn(UnstableApi::class)
object Analizador : TeeAudioProcessor.AudioBufferSink {

    /** Quien recibe las bandas (el karaoke). Si es null, no se calcula nada. */
    @Volatile var oyente: ((FloatArray) -> Unit)? = null

    private const val N = 1024                  // muestras por análisis
    private const val BANDAS = 16
    private const val RETRASO_MS = 0L           // la espera la maneja el karaoke (ajustable desde la app)

    private val principal = Handler(Looper.getMainLooper())
    private var frecuencia = 44100
    private var canales = 2
    private var pcm16 = false

    private val anillo = FloatArray(N)          // últimas N muestras (mono)
    private var pos = 0
    private var desdeUltimo = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val ventana = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private var maximo = -30f                   // control automático de volumen (en dB)
    private var bordes = IntArray(BANDAS + 1)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        frecuencia = sampleRateHz
        canales = max(1, channelCount)
        pcm16 = encoding == C.ENCODING_PCM_16BIT
        pos = 0; desdeUltimo = 0
        // Bandas repartidas en escala logarítmica, de 40 Hz a 16 kHz (como las oye el oído)
        val alto = min(16000.0, sampleRateHz / 2.0)
        bordes = IntArray(BANDAS + 1) { k ->
            val f = 40.0 * (alto / 40.0).pow(k.toDouble() / BANDAS)
            max(1, min(N / 2 - 1, (f * N / sampleRateHz).toInt()))
        }
        for (k in 1..BANDAS) if (bordes[k] <= bordes[k - 1]) bordes[k] = bordes[k - 1] + 1
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (oyente == null || !pcm16) return
        val datos = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val porCuadro = frecuencia / 30         // unas 30 veces por segundo
        while (datos.remaining() >= canales) {
            var suma = 0f
            for (c in 0 until canales) suma += datos.get() / 32768f
            anillo[pos] = suma / canales
            pos = (pos + 1) % N
            if (++desdeUltimo >= porCuadro) { desdeUltimo = 0; analizar() }
        }
    }

    private fun analizar() {
        for (i in 0 until N) { re[i] = anillo[(pos + i) % N] * ventana[i]; im[i] = 0f }
        fft()
        val bandas = FloatArray(BANDAS)
        var mayor = -120f
        for (k in 0 until BANDAS) {
            var e = 0f
            for (j in bordes[k] until bordes[k + 1]) e += re[j] * re[j] + im[j] * im[j]
            val db = 10f * log10(e / (bordes[k + 1] - bordes[k]) + 1e-10f)
            bandas[k] = db
            mayor = max(mayor, db)
        }
        maximo = max(mayor, maximo - 0.15f)    // se adapta al volumen de cada canción
        for (k in 0 until BANDAS) bandas[k] = ((bandas[k] - (maximo - 42f)) / 42f).coerceIn(0f, 1f)
        val listo = SystemClock.uptimeMillis() + RETRASO_MS
        principal.postAtTime({ oyente?.invoke(bandas) }, listo)
    }

    // FFT de base 2, en el lugar
    private fun fft() {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var largo = 2
        while (largo <= N) {
            val ang = -2 * PI / largo
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < N) {
                var cr = 1f; var ci = 0f
                for (k in 0 until largo / 2) {
                    val a = i + k; val b = a + largo / 2
                    val tr = re[b] * cr - im[b] * ci; val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    val n = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = n
                }
                i += largo
            }
            largo = largo shl 1
        }
    }

    /** Las bandas en el formato que entiende el karaoke. */
    fun json(b: FloatArray): String =
        b.joinToString(",", "{\"tipo\":\"audio\",\"b\":[", "]}") { String.format(java.util.Locale.US, "%.2f", it) }
}
