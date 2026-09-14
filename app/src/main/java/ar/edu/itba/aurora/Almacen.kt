package ar.edu.itba.aurora

import java.util.UUID

/**
 * El unico lugar del que las pantallas piden o guardan datos.
 *
 * Regla de oro: la app NUNCA habla directo con Supabase. Escribe y lee de la
 * base local (Room) y despues, si hay internet, el sincronizador sube lo
 * pendiente. Asi el chofer puede trabajar todo el viaje sin senal.
 */
object Almacen {

    // ------------------------------------------------------------------
    // Lectura (siempre de la base local)
    // ------------------------------------------------------------------

    suspend fun viajes(perfil: Perfil): List<Viaje> {
        val dao = Local.dao
        val eventosPorViaje = dao.todosLosEventos().groupBy { it.viajeId }
        return dao.viajesDe(perfil.id).map { viaje ->
            armarViaje(viaje, eventosPorViaje[viaje.id] ?: emptyList())
        }
    }

    /** Cuantas cosas hay guardadas en el telefono sin subir todavia. */
    suspend fun pendientes(): Int = Local.dao.cuantosPendientes()

    // ------------------------------------------------------------------
    // Escritura (local primero, nube despues)
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
        Local.dao.cerrarViaje(idViaje, System.currentTimeMillis())
    }

    /**
     * Guarda un evento de somnolencia.
     *
     * Hoy lo llama el boton simulador; cuando este el ESP32, lo va a llamar
     * el receptor de Bluetooth con exactamente los mismos datos. El resto de
     * la cadena (guardado local, sincronizacion, web del admin) ya no cambia.
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
    // Sincronizacion
    // ------------------------------------------------------------------

    /**
     * Sube lo pendiente y despues baja lo que haya en la nube.
     * Devuelve cuantas cosas subio. Si no hay internet devuelve el error y
     * no se pierde nada: lo pendiente sigue pendiente.
     */
    suspend fun sincronizar(): Result<Int> = runCatching {
        val dao = Local.dao

        val viajesPendientes = dao.viajesPendientes()
        if (viajesPendientes.isNotEmpty()) {
            Datos.subirViajes(viajesPendientes)
            dao.marcarViajesSubidos(viajesPendientes.map { it.id })
        }

        val eventosPendientes = dao.eventosPendientes()
        if (eventosPendientes.isNotEmpty()) {
            Datos.subirEventos(eventosPendientes)
            dao.marcarEventosSubidos(eventosPendientes.map { it.id })
        }

        // Recien ahora se baja: si la subida fallo, esta linea no se ejecuta
        // y lo local queda intacto para el proximo intento.
        val viajesNube = Datos.bajarViajes()
        if (viajesNube.isNotEmpty()) dao.guardarViajes(viajesNube)

        val eventosNube = Datos.bajarEventos()
        if (eventosNube.isNotEmpty()) dao.guardarEventos(eventosNube)

        viajesPendientes.size + eventosPendientes.size
    }

    /** Al cerrar sesion se borra lo ya subido; lo pendiente se conserva. */
    suspend fun limpiarLoSubido() {
        Local.dao.borrarEventosSubidos()
        Local.dao.borrarViajesSubidos()
    }
}