package ar.edu.itba.aurora

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================================================================
// Las filas, tal como viajan hacia y desde Supabase
// ============================================================================
// Son el "molde" del JSON. Los nombres tienen que coincidir con las columnas;
// cuando no coinciden (chofer_id vs choferId) se aclara con @SerialName.

@Serializable
data class ViajeFila(
    val id: String,
    @SerialName("empresa_id") val empresaId: String? = null,
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
    @SerialName("empresa_id") val empresaId: String? = null,
    val tipo: String,
    val nivel: Int,
    @SerialName("ocurrido_en") val ocurridoEn: String? = null,
    val valor: Double? = null
)

@Serializable
private data class EmpresaFila(val nombre: String)

// ============================================================================
// Subidas y bajadas contra Supabase
// ============================================================================
object Datos {

    /**
     * Sube viajes. Usa "upsert": si el viaje ya estaba (porque el envio
     * anterior llego pero no alcanzamos a marcarlo como subido), lo pisa en
     * vez de crear un duplicado. Por eso el id lo genera la app.
     */
    suspend fun subirViajes(viajes: List<ViajeLocal>) {
        if (viajes.isEmpty()) return
        supabase.from("viajes").upsert(viajes.map { it.aFila() })
    }

    suspend fun subirEventos(eventos: List<EventoLocal>) {
        if (eventos.isEmpty()) return
        supabase.from("eventos").upsert(eventos.map { it.aFila() })
    }

    /** Trae los viajes del chofer que estan en la nube. */
    suspend fun bajarViajes(): List<ViajeLocal> {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        return supabase.from("viajes")
            .select(
                Columns.list(
                    "id", "empresa_id", "chofer_id", "inicio", "fin",
                    "estado", "origen", "destino"
                )
            ) {
                filter { eq("chofer_id", uid) }
                order("inicio", Order.DESCENDING)
                limit(100)
            }
            .decodeList<ViajeFila>()
            .map { it.aLocal() }
    }

    /** Trae los eventos de esos viajes (las reglas RLS ya filtran por chofer). */
    suspend fun bajarEventos(): List<EventoLocal> =
        supabase.from("eventos")
            .select(
                Columns.list("id", "viaje_id", "empresa_id", "tipo", "nivel", "ocurrido_en", "valor")
            ) {
                order("ocurrido_en", Order.DESCENDING)
                limit(1000)
            }
            .decodeList<EventoFila>()
            .map { it.aLocal() }

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
// Traducciones entre los tres formatos: nube, base local y pantalla
// ============================================================================

private val ZONA: ZoneId = ZoneId.systemDefault()
private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd MMM", Locale("es"))
private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FORMATO_SOLO_HORA = DateTimeFormatter.ofPattern("HH")

/** Texto de fecha de Supabase -> milisegundos. */
private fun aMilisegundos(texto: String?): Long? =
    texto?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

/** Milisegundos -> texto de fecha que entiende Supabase. */
private fun aTexto(ms: Long): String = Instant.ofEpochMilli(ms).toString()

// --- nube -> local ---

private fun ViajeFila.aLocal() = ViajeLocal(
    id = id,
    empresaId = empresaId ?: "",
    choferId = choferId ?: "",
    inicioMs = aMilisegundos(inicio) ?: 0L,
    finMs = aMilisegundos(fin),
    estado = estado,
    origen = origen ?: "-",
    destino = destino ?: "-",
    pendiente = false
)

private fun EventoFila.aLocal() = EventoLocal(
    id = id,
    viajeId = viajeId,
    empresaId = empresaId ?: "",
    tipo = tipo,
    nivel = nivel,
    ocurridoEnMs = aMilisegundos(ocurridoEn) ?: 0L,
    valor = valor,
    pendiente = false
)

// --- local -> nube ---

private fun ViajeLocal.aFila() = ViajeFila(
    id = id,
    empresaId = empresaId,
    choferId = choferId,
    inicio = aTexto(inicioMs),
    fin = finMs?.let { aTexto(it) },
    estado = estado,
    origen = origen,
    destino = destino
)

private fun EventoLocal.aFila() = EventoFila(
    id = id,
    viajeId = viajeId,
    empresaId = empresaId,
    tipo = tipo,
    nivel = nivel,
    ocurridoEn = aTexto(ocurridoEnMs),
    valor = valor
)

// --- local -> pantalla ---

fun armarViaje(viaje: ViajeLocal, eventos: List<EventoLocal>): Viaje {
    val enCurso = viaje.estado == "en_curso"
    val hasta = viaje.finMs ?: System.currentTimeMillis()
    val ordenados = eventos.sortedByDescending { it.ocurridoEnMs }
    val (porHora, etiquetas) = eventosPorHora(viaje.inicioMs, hasta, ordenados)

    return Viaje(
        id = viaje.id,
        origen = viaje.origen,
        destino = viaje.destino,
        fecha = fecha(viaje.inicioMs),
        duracion = duracionLegible(viaje.inicioMs, hasta),
        horario = horaDelDia(viaje.inicioMs) + " -> " +
                if (enCurso) "en curso" else horaDelDia(viaje.finMs ?: hasta),
        inicioMs = viaje.inicioMs,
        finMs = viaje.finMs,
        eventos = ordenados.map {
            Evento(
                hora = horaDelDia(it.ocurridoEnMs),
                tipo = nombreTipo(it.tipo),
                nivel = it.nivel,
                momentoMs = it.ocurridoEnMs
            )
        },
        enCurso = enCurso,
        // Si el viaje o alguno de sus eventos todavia no se subio, se avisa.
        sincronizado = !viaje.pendiente && ordenados.none { it.pendiente },
        eventosPorHora = porHora,
        etiquetasHora = etiquetas,
        resumen = resumen(ordenados)
    )
}

// ============================================================================
// Formatos de fecha, hora y duracion
// ============================================================================

private fun fecha(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZONA).format(FORMATO_FECHA)

/** Hora del reloj (HH:MM) de un momento cualquiera. */
fun horaDelDia(momentoMs: Long): String =
    Instant.ofEpochMilli(momentoMs).atZone(ZONA).format(FORMATO_HORA)

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

private fun nombreTipo(tipo: String): String = when (tipo) {
    "parpadeo_lento" -> "Parpadeo lento"
    "cabeceo" -> "Cabeceo"
    else -> "Otro"
}

/** Cuenta cuantos eventos hubo en cada hora del viaje, para el grafico. */
private fun eventosPorHora(
    inicioMs: Long,
    hastaMs: Long,
    eventos: List<EventoLocal>
): Pair<List<Int>, List<String>> {
    if (eventos.isEmpty()) return emptyList<Int>() to emptyList()

    val primeraHora = Instant.ofEpochMilli(inicioMs).atZone(ZONA)
        .withMinute(0).withSecond(0).withNano(0)
    val primeraHoraMs = primeraHora.toInstant().toEpochMilli()
    val unaHora = 3_600_000L
    val cantidad = (((hastaMs - primeraHoraMs) / unaHora).toInt() + 1).coerceIn(1, 24)

    val baldes = IntArray(cantidad)
    eventos.forEach { evento ->
        val indice = ((evento.ocurridoEnMs - primeraHoraMs) / unaHora).toInt()
        if (indice in 0 until cantidad) baldes[indice]++
    }

    val etiquetas = if (cantidad >= 3) {
        listOf(0, cantidad / 2, cantidad - 1)
            .map { primeraHora.plusHours(it.toLong()).format(FORMATO_SOLO_HORA) }
    } else {
        listOf(primeraHora.format(FORMATO_SOLO_HORA))
    }

    return baldes.toList() to etiquetas
}

private fun resumen(eventos: List<EventoLocal>): String {
    if (eventos.isEmpty()) return ""
    val nivel2 = eventos.count { it.nivel >= 2 }
    return if (nivel2 > 0) {
        "$nivel2 de ${eventos.size} eventos llegaron a nivel de alerta."
    } else {
        "${eventos.size} eventos, todos en nivel de precaucion."
    }
}