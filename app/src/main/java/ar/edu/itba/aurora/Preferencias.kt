package ar.edu.itba.aurora

import android.content.Context
import android.content.SharedPreferences

/**
 * Gustos del usuario que quedan guardados en el telefono aunque cierre la app
 * o cierre sesion. Por ahora, solo si prefiere modo dia o modo noche.
 *
 * Es un archivo chiquito de Android (SharedPreferences), aparte de la base
 * local de Room: son datos sueltos, no hacen falta tablas.
 */
object Preferencias {

    private const val ARCHIVO = "aurora_preferencias"
    private const val TEMA_OSCURO = "tema_oscuro"

    private var prefs: SharedPreferences? = null

    fun iniciar(contexto: Context) {
        if (prefs == null) {
            prefs = contexto.applicationContext
                .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        }
    }

    /** Modo noche. Si nunca eligio nada, arranca en oscuro. */
    var temaOscuro: Boolean
        get() = prefs?.getBoolean(TEMA_OSCURO, true) ?: true
        set(valor) {
            prefs?.edit()?.putBoolean(TEMA_OSCURO, valor)?.apply()
        }
}
