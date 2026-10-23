package hn.oido

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display

/** Karaoke en una segunda pantalla (tele por cable, dock o Miracast). */
class KaraokeTv(contexto: Context, pantalla: Display, private val alListo: () -> Unit) :
    Presentation(contexto, pantalla) {

    private var vista: VistaKaraoke? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = VistaKaraoke(context, alListo)
        setContentView(v.web)
        vista = v
    }

    fun enviar(json: String) { vista?.enviar(json) }

    override fun onStop() {
        vista?.destruir()
        vista = null
        super.onStop()
    }
}
