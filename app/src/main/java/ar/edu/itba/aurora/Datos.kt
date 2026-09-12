package ar.edu.itba.aurora

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ============================================================================
// Las filas, tal como estan en la base de datos
// ============================================================================
// Estas clases son el "molde" para leer el JSON que devuelve Supabase.
// Los nombres tienen que coincidir con las columnas; cuando no coinciden
// (chofer_id vs choferId) se aclara con @SerialName.

@Serializable
data class ViajeFila(
    val id: String,
    @SerialName("chofer_id") val choferId: String? = null,
    val inicio: String? = null,
    val fin: String? = null,
    val estado: String = "en_curso",
    val origen: String? = null,
    val destino: String? = null
)

@Serializable
data class EventoFila(
    val id: String,
    @SerialName("viaje_id") val viajeId: String,
    val tipo: String,
    val nivel: Int,
    @SerialName("ocurrido_en") val ocurridoEn: String? = null,
    val valor: Double? = null
)

// Molde para dar de alta un viaje. El id lo genera la app (no la base) para
// que mas adelante se pueda reintentar el envio sin duplicar el viaje.
@Serializable
private data class ViajeNuevo(
    val id: String,
    @SerialName("empresa_id") val empresaId: String,
    @SerialName("chofer_id") val choferId: String,
    val inicio: String,
    val estado: String,
    val origen: String,
    val destino: String
)

// Molde para cerrar un viaje
@Serializable
private data class ViajeCierre(
    val fin: String,
    val estado: String
)

@Serializable
private data class EmpresaFila(val nombre: String)

// ============================================================================
// Lecturas y escrituras contra Supabase
// ============================================================================
object Datos {

    /** Todos los viajes del chofer logueado, con sus eventos, mas nuevo primero. */
    suspend fun viajesDelChofer(): List<Viaje> {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return emptyList()

        val viajes = supabase.from("viajes")
            .select(
                Columns.list("id", "chofer_id", "inicio", "fin", "estado", "origen", "destino")
            ) {
                filter { eq("chofer_id", uid) }
                order("inicio", Order.DESCENDING)
                limit(100)
            }
            .decodeList<ViajeFila>()

        // Las reglas de la base (RLS) ya hacen que solo lleguen los eventos
        // de los viajes de este chofer, asi que no hace falta filtrar de nuevo.
        val eventosPorViaje = supabase.from("eventos")
            .select(
                Columns.list("id", "viaje_id", "tipo", "nivel", "ocurrido_en", "valor")
            ) {
                order("ocurrido_en", Order.DESCENDING)
                limit(1000)
            }
            .decodeList<EventoFila>()
            .groupBy { it.viajeId }

        return viajes.map { fila -> armarViaje(fila, eventosPorViaje[fila.id] ?: emptyList()) }
    }

    /** Da de alta un viaje nuevo en estado "en curso". */
    suspend fun iniciarViaje(
        perfil: Perfil,
        origen: String,
        destino: String
    ): Result<Viaje> = runCatching {
        val empresa = perfil.empresaId
            ?: error("Tu usuario no tiene empresa asignada. Avisale a tu supervisor.")

        val nuevo = ViajeNuevo(
            id = UUID.randomUUID().toString(),
            empresaId = empresa,
            choferId = perfil.id,
            inicio = Instant.now().toString(),
            estado = "en_curso",
            origen = origen.trim().ifBlank { "Origen" },
            destino = destino.trim().ifBlank { "Destino" }
        )

        supabase.from("viajes").insert(nuevo)

        armarViaje(
            ViajeFila(
                id = nuevo.id,
                choferId = nuevo.choferId,
                inicio = nuevo.inicio,
                estado = nuevo.estado,
                origen = nuevo.origen,
                destino = nuevo.destino
            ),
            emptyList()
        )
    }

    /** Cierra el viaje: guarda la hora de fin y lo pasa a "finalizado". */
    suspend fun finalizarViaje(idViaje: String): Result<Unit> = runCatching {
        supabase.from("viajes").update(
            ViajeCierre(fin = Instant.now().toString(), estado = "finalizado")
        ) {
            filter { eq("id", idViaje) }
        }
        Unit
    }

    /** Nombre de la empresa del chofer. Devuelve null si no se puede leer. */
    suspend fun nombreEmpresa(perfil: Perfil): String? {
        val empresa = perfil.empresaId ?: return null
        return runCatching {
            supabase.from("empresas")
                .select(Columns.list("nombre")) {
                    filter { eq("id", empresa) }
                }
                .decodeSingleOrNull<EmpresaFila>()
                ?.nombre
        }.getOrNull()
    }
}

// ============================================================================
// De filas de la base a lo que muestran las pantallas
// ============================================================================

private val ZONA: ZoneId = ZoneId.systemDefault()
private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd MMM", Locale("es"))
private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FORMATO_SOLO_HORA = DateTimeFormatter.ofPattern("HH")

/** Convierte el texto de fecha que manda Supabase a un instante de tiempo. */
private fun aInstante(texto: String?): Instant? =
    texto?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

private fun fecha(momento: Instant?): String =
    momento?.atZone(ZONA)?.format(FORMATO_FECHA) ?: "--"

private fun hora(momento: Instant?): String =
    momento?.atZone(ZONA)?.format(FORMATO_HORA) ?: "--:--"

/** Duracion en texto legible: "4 h 12 min", "12 min" o "40 s". */
fun duracionLegible(desdeMs: Long?, hastaMs: Long): String {
    if (desdeMs == null) return "--"
    val segundos = ((hastaMs - desdeMs) / 1000).coerceAtLeast(0)
    val horas = segundos / 3600
    val minutos = (segundos % 3600) / 60
    return when {
        horas > 0 -> "$horas h $minutos min"
        minutos > 0 -> "$minutos min"
        else -> "$segundos s"
    }
}

/** Cronometro en formato HH:MM:SS, para el viaje en curso. */
fun cronometro(desdeMs: Long?, hastaMs: Long): String {
    if (desdeMs == null) return "--:--:--"
    val segundos = ((hastaMs - desdeMs) / 1000).coerceAtLeast(0)
    return String.format(
        Locale.US, "%02d:%02d:%02d",
        segundos / 3600, (segundos % 3600) / 60, segundos % 60
    )
}

/** Hora del reloj (HH:MM) de un momento cualquiera. */
fun horaDelDia(momentoMs: Long): String =
    Instant.ofEpochMilli(momentoMs).atZone(ZONA).format(FORMATO_HORA)

private fun nombreTipo(tipo: String): String = when (tipo) {
    "parpadeo_lento" -> "Parpadeo lento"
    "cabeceo" -> "Cabeceo"
    else -> "Otro"
}

private fun armarViaje(fila: ViajeFila, eventos: List<EventoFila>): Viaje {
    val inicio = aInstante(fila.inicio)
    val fin = aInstante(fila.fin)
    val enCurso = fila.estado == "en_curso"
    // Si el viaje sigue abierto, la duracion se cuenta hasta ahora.
    val hasta = fin ?: Instant.now()

    val ordenados = eventos.sortedByDescending { it.ocurridoEn ?: "" }
    val (porHora, etiquetas) = eventosPorHora(inicio, hasta, ordenados)

    return Viaje(
        id = fila.id,
        origen = fila.origen ?: "-",
        destino = fila.destino ?: "-",
        fecha = fecha(inicio),
        duracion = duracionLegible(inicio?.toEpochMilli(), hasta.toEpochMilli()),
        horario = hora(inicio) + " -> " + if (enCurso) "en curso" else hora(fin),
        inicioMs = inicio?.toEpochMilli(),
        eventos = ordenados.map {
            Evento(
                hora = hora(aInstante(it.ocurridoEn)),
                tipo = nombreTipo(it.tipo),
                nivel = it.nivel
            )
        },
        enCurso = enCurso,
        sincronizado = true,
        eventosPorHora = porHora,
        etiquetasHora = etiquetas,
        resumen = resumen(ordenados)
    )
}

/** Cuenta cuantos eventos hubo en cada hora del viaje, para el grafico. */
private fun eventosPorHora(
    inicio: Instant?,
    hasta: Instant,
    eventos: List<EventoFila>
): Pair<List<Int>, List<String>> {
    if (inicio == null || eventos.isEmpty()) return emptyList<Int>() to emptyList()

    val primeraHora = inicio.atZone(ZONA).withMinute(0).withSecond(0).withNano(0)
    val cantidad = (Duration.between(primeraHora, hasta.atZone(ZONA)).toHours().toInt() + 1)
        .coerceIn(1, 24)

    val baldes = IntArray(cantidad)
    eventos.forEach { evento ->
        val momento = aInstante(evento.ocurridoEn)?.atZone(ZONA)
        if (momento != null) {
            val indice = Duration.between(primeraHora, momento).toHours().toInt()
            if (indice in 0 until cantidad) baldes[indice]++
        }
    }

    val etiquetas = if (cantidad >= 3) {
        listOf(0, cantidad / 2, cantidad - 1)
            .map { primeraHora.plusHours(it.toLong()).format(FORMATO_SOLO_HORA) }
    } else {
        listOf(primeraHora.format(FORMATO_SOLO_HORA))
    }

    return baldes.toList() to etiquetas
}

private fun resumen(eventos: List<EventoFila>): String {
    if (eventos.isEmpty()) return ""
    val nivel2 = eventos.count { it.nivel >= 2 }
    return if (nivel2 > 0) {
        "$nivel2 de ${eventos.size} eventos llegaron a nivel de alerta."
    } else {
        "${eventos.size} eventos, todos en nivel de precaucion."
    }
}