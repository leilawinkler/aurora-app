package ar.edu.itba.aurora

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La duracion basal del parpadeo de cada chofer (calibracion de duracion).
 *
 * Es de la PERSONA (cada vincha es de un chofer). Se mide una sola vez (y se
 * puede repetir desde Configuracion) y se usa en cada viaje para el nivel 1.
 *
 * Mismas reglas que los viajes (ver Almacen.kt):
 *  1. Se guarda primero en el telefono (Preferencias), con o sin señal.
 *  2. El Sincronizador la sube a Supabase cuando hay señal (columna
 *     dur_basal_ms de "usuarios", por la funcion guardar_basal).
 *  3. Al entrar, se trae de la nube. La nube NUNCA pisa una calibracion
 *     hecha en el telefono que todavia no se subio.
 *
 * Sin basal guardado (ni en el telefono ni en la nube), la app pide
 * calibrar y no deja empezar viajes. Los choferes que ya existian
 * simplemente no tienen el valor: calibran al entrar y listo.
 */
object Calibracion {

    // Sube de a uno cada vez que cambia el basal guardado: la pantalla lo
    // mira para volver a leerlo.
    private val _cambios = MutableStateFlow(0)
    val cambios: StateFlow<Int> = _cambios.asStateFlow()

    /** El basal de este chofer guardado en el telefono, o null si nunca calibro. */
    fun basal(idChofer: String?): BasalGuardado? =
        idChofer?.let { Preferencias.basalDe(it) }

    /** Recien calibrado: queda en el telefono y se intenta subir enseguida. */
    fun guardarNueva(idChofer: String, ms: Int, parpadeos: Int) {
        Preferencias.guardarBasal(
            idChofer,
            BasalGuardado(
                ms = ms,
                parpadeos = parpadeos,
                calibradoEnMs = System.currentTimeMillis(),
                pendiente = true
            )
        )
        _cambios.value = _cambios.value + 1
        Sincronizador.huboNovedades()
    }

    /**
     * Trae el basal de la nube (si hay señal y sesion). Si en el telefono hay
     * una calibracion sin subir, no hace nada: gana la del telefono.
     */
    suspend fun traerDeLaNube(idChofer: String) {
        val local = Preferencias.basalDe(idChofer)
        if (local?.pendiente == true) return
        val nube = runCatching { Datos.bajarBasal(idChofer) }
            .onFailure { android.util.Log.w("AuroraCal", "No se pudo leer el basal de la nube", it) }
            .getOrNull() ?: return
        val ms = nube.ms ?: return            // nunca calibro: queda lo que haya en el telefono
        val enMs = nube.calibradoEnMs ?: local?.calibradoEnMs ?: 0L
        if (local != null && local.ms == ms && local.calibradoEnMs == enMs) return
        Preferencias.guardarBasal(
            idChofer,
            BasalGuardado(ms = ms, parpadeos = local?.parpadeos ?: 0, calibradoEnMs = enMs, pendiente = false)
        )
        _cambios.value = _cambios.value + 1
    }

    /**
     * Sube la calibracion pendiente del chofer que esta adentro, si hay.
     * La llama el Sincronizador (con sesion activa). Si falla, queda
     * pendiente y se reintenta en la vuelta siguiente.
     */
    suspend fun subirPendiente() {
        val id = Preferencias.perfilGuardado?.id ?: return
        val b = Preferencias.basalDe(id) ?: return
        if (!b.pendiente) return
        Datos.subirBasal(b.ms, b.parpadeos, b.calibradoEnMs)
        // Se marca subida solo si no se recalibro mientras se subia.
        if (Preferencias.basalDe(id) == b) {
            Preferencias.guardarBasal(id, b.copy(pendiente = false))
            _cambios.value = _cambios.value + 1
        }
    }
}
