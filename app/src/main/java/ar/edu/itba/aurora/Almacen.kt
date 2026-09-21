package ar.edu.itba.aurora

import java.util.UUID

/**
 * El unico lugar del que las pantallas piden o guardan datos.
 *
 * Reglas:
 *  1. La app NUNCA espera a internet para trabajar. Iniciar, registrar
 *     eventos y finalizar escriben en Room (la base del telefono), con o sin
 *     señal. Despues el sincronizador sube lo pendiente.
 *  2. El telefono guarda solo lo que hace falta: lo que todavia no se subio,
 *     y el viaje en curso (para poder finalizarlo aunque se corte la señal).
 *     Lo ya subido y terminado se borra del telefono.
 *  3. El historial completo se trae de Supabase cada vez que hay señal y se
 *     guarda solo en memoria, no en el telefono. Sin señal se ve solo lo que
 *     hay en Room, y la pantalla avisa que para el resto hace falta conexion.
 */
object Almacen {

    // Historial traido de la nube en la ultima sincronizacion que funciono.
    // Vive en memoria: se pierde al cerrar la app y se vuelve a pedir.
    // null = todavia no se pudo traer (sin señal desde que se abrio la app).
    @Volatile
    private var historialNube: List<Pair<ViajeLocal, List<EventoLocal>>>? = null

    /** true si el historial de la nube ya esta cargado en esta sesion. */
    val historialCompleto: Boolean
        get() = historialNube != null

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    /**
     * Lo que ve el chofer: lo del telefono (pendiente y viaje en curso) mas
     * el historial de la nube, si esta cargado. Si un viaje esta en los dos
     * lados, gana el del telefono, que puede tener cambios sin subir.
     */
    suspend fun viajes(perfil: Perfil): List<Viaje> {
        val dao = Local.dao
        val eventosLocales = dao.todosLosEventos().groupBy { it.viajeId }
        val locales = dao.viajesDe(perfil.id)
        val idsLocales = locales.map { it.id }.toSet()

        val deTelefono = locales.map { v ->
            v.inicioMs to armarViaje(v, eventosLocales[v.id] ?: emptyList())
        }
        val deNube = (historialNube ?: emptyList())
            .filter { (v, _) -> v.choferId == perfil.id && v.id !in idsLocales }
            .map { (v, eventos) -> v.inicioMs to armarViaje(v, eventos) }

        return (deTelefono + deNube)
            .sortedByDescending { it.first }
            .map { it.second }
    }

    /** Cuantas cosas hay guardadas en el telefono sin subir todavia. */
    suspend fun pendientes(): Int = Local.dao.cuantosPendientes()

    // ------------------------------------------------------------------
    // Escritura (siempre local; no necesita señal)
    // ------------------------------------------------------------------

    suspend fun iniciarViaje(perfil: Perfil, origen: String, destino: String): Result<Unit> =
        runCatching {
            val empresa = perfil.empresaId
                ?: error("Tu usuario no tiene empresa asignada. Avisale a tu supervisor.")

            Local.dao.guardarViaje(
                ViajeLocal(
                    id = UUID.randomUUID().toString(),
                    empresaId = empresa,
                    choferId = perfil.id,
                    inicioMs = System.currentTimeMillis(),
                    finMs = null,
                    estado = "en_curso",
                    origen = origen.trim().ifBlank { "Origen" },
                    destino = destino.trim().ifBlank { "Destino" },
                    pendiente = true
                )
            )
        }

    suspend fun finalizarViaje(idViaje: String): Result<Unit> = runCatching {
        val ahora = System.currentTimeMillis()
        val cambiadas = Local.dao.cerrarViaje(idViaje, ahora)

        // Caso raro: el viaje en curso solo estaba en el historial de la nube
        // (por ejemplo, se abrio desde otro telefono). Se copia al telefono
        // ya finalizado, para que se suba la finalizacion.
        if (cambiadas == 0) {
            val deNube = historialNube?.firstOrNull { it.first.id == idViaje }?.first
                ?: error("No se encontró el viaje para finalizarlo.")
            Local.dao.guardarViaje(
                deNube.copy(finMs = ahora, estado = "finalizado", pendiente = true)
            )
        }
    }

    /**
     * Guarda un evento de somnolencia.
     *
     * Hoy lo llama el boton simulador; cuando este el ESP32, lo va a llamar
     * el receptor de Bluetooth con exactamente los mismos datos.
     */
    suspend fun registrarEvento(
        perfil: Perfil,
        idViaje: String,
        tipo: String,
        nivel: Int,
        valor: Double? = null,
        ocurridoEnMs: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val empresa = perfil.empresaId ?: error("Tu usuario no tiene empresa asignada.")
        Local.dao.guardarEvento(
            EventoLocal(
                id = UUID.randomUUID().toString(),
                viajeId = idViaje,
                empresaId = empresa,
                tipo = tipo,
                nivel = nivel,
                ocurridoEnMs = ocurridoEnMs,
                valor = valor,
                pendiente = true
            )
        )
    }

    // ------------------------------------------------------------------
    // Sincronizacion (la llama el Sincronizador, con sesion activa)
    // ------------------------------------------------------------------

    /**
     * 1) Sube lo pendiente.
     * 2) Marca como subido solo lo que no cambio mientras se subia.
     * 3) Trae el historial de la nube a memoria (sin tocar el telefono).
     * 4) Si hay un viaje en curso en la nube que el telefono no tiene, lo
     *    copia, para poder finalizarlo aunque despues se corte la señal.
     * 5) Borra del telefono lo que ya esta subido y terminado.
     *
     * Si algo falla (se corta la señal a mitad de camino), lo pendiente
     * sigue pendiente y se reintenta en la vuelta siguiente: no se pierde nada.
     */
    suspend fun sincronizar(): Result<Int> = runCatching {
        val dao = Local.dao

        // Primero los viajes: los eventos apuntan a ellos.
        val viajesPendientes = dao.viajesPendientes()
        if (viajesPendientes.isNotEmpty()) {
            Datos.subirViajes(viajesPendientes)
            viajesPendientes.forEach { v ->
                dao.marcarViajeSubidoSiNoCambio(v.id, v.estado, v.finMs ?: -1L)
            }
        }

        val eventosPendientes = dao.eventosPendientes()
        if (eventosPendientes.isNotEmpty()) {
            Datos.subirEventos(eventosPendientes)
            dao.marcarEventosSubidos(eventosPendientes.map { it.id })
        }

        // Historial de la nube, a memoria
        val viajesNube = Datos.bajarViajes()
        val eventosNube = Datos.bajarEventos().groupBy { it.viajeId }
        historialNube = viajesNube.map { it to (eventosNube[it.id] ?: emptyList()) }

        // Viaje en curso que el telefono no tiene
        viajesNube.filter { it.estado == "en_curso" }.forEach { v ->
            dao.guardarViajeSiNoExiste(v)
            dao.guardarEventosSiNoExisten(eventosNube[v.id] ?: emptyList())
        }

        limpiarTelefono()
        viajesPendientes.size + eventosPendientes.size
    }

    /** Borra del telefono lo ya subido y terminado. Lo pendiente no se toca. */
    private suspend fun limpiarTelefono() {
        Local.dao.borrarEventosDeViajesTerminados()
        Local.dao.borrarViajesTerminados()
    }

    /**
     * Al cerrar sesion: se olvida el historial de la nube (era de este chofer)
     * y se limpia lo ya subido. Lo pendiente queda y se sube la proxima vez
     * que este chofer entre con señal.
     */
    suspend fun alCerrarSesion() {
        historialNube = null
        limpiarTelefono()
    }
}
