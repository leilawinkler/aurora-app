package ar.edu.itba.aurora

data class Evento(
    val hora: String,
    val tipo: String,
    val nivel: Int,
    // Momento exacto del evento, para ubicarlo en el grafico del viaje.
    val momentoMs: Long = 0L,
    // Parpadeo lento: duracion en ms. Cabeceo: grados hacia adelante.
    val valor: Double? = null
) {
    /** El valor con su unidad, para mostrar: "620 ms" o "18°". */
    val valorTexto: String?
        get() = valor?.let {
            if (tipo == "Cabeceo") "${it.toInt()}°" else "${it.toInt()} ms"
        }
}

data class Viaje(
    val id: String,
    val origen: String,
    val destino: String,
    val fecha: String,
    val duracion: String,
    val horario: String,
    val eventos: List<Evento>,
    // Momento de arranque del viaje, para el cronometro en vivo.
    val inicioMs: Long? = null,
    // Momento de cierre; null mientras el viaje sigue abierto.
    val finMs: Long? = null,
    val enCurso: Boolean = false,
    val sincronizado: Boolean = true,
    val eventosPorHora: List<Int> = emptyList(),
    val etiquetasHora: List<String> = emptyList(),
    val resumen: String = ""
) {
    val nivelMaximo: Int
        get() = eventos.maxOfOrNull { it.nivel } ?: 0
}

// Solo se usa en las vistas previas de Android Studio.
object DatosDeEjemplo {

    val viajeEnCurso = Viaje(
        id = "ejemplo-0",
        origen = "Rosario",
        destino = "Tucuman",
        fecha = "08 sep",
        duracion = "04:12",
        horario = "12:20 -> en curso",
        enCurso = true,
        eventos = listOf(
            Evento("15:40", "Parpadeo lento", 1),
            Evento("14:55", "Parpadeo lento", 2),
            Evento("13:30", "Parpadeo lento", 1)
        )
    )

    val viajes = listOf(
        Viaje(
            id = "ejemplo-1",
            origen = "Cordoba",
            destino = "Rosario",
            fecha = "05 sep",
            duracion = "6:20",
            horario = "08:10 -> 14:30",
            sincronizado = true,
            eventosPorHora = listOf(1, 0, 2, 1, 4, 3, 1),
            etiquetasHora = listOf("08", "11", "14"),
            resumen = "La mayoria de los eventos se concentro sobre el final del viaje.",
            eventos = listOf(
                Evento("13:52", "Parpadeo lento", 2),
                Evento("13:20", "Parpadeo lento", 1),
                Evento("12:44", "Parpadeo lento", 1)
            )
        ),
        Viaje(
            id = "ejemplo-2",
            origen = "Mendoza",
            destino = "Cordoba",
            fecha = "02 sep",
            duracion = "9:05",
            horario = "21:30 -> 06:35",
            sincronizado = false,
            eventosPorHora = listOf(1, 2, 1, 4, 7, 10, 8, 3, 1),
            etiquetasHora = listOf("21", "02", "06"),
            resumen = "El 70 % de los eventos ocurrio entre las 02 y las 04.",
            eventos = listOf(
                Evento("03:14", "Cabeceo", 3),
                Evento("03:02", "Parpadeo lento", 2),
                Evento("02:47", "Parpadeo lento", 1),
                Evento("02:15", "Parpadeo lento", 2),
                Evento("01:38", "Parpadeo lento", 1)
            )
        )
    )
}

/**
 * En que nivel esta el conductor ahora (se muestra en la tarjeta del viaje en curso).
 * Se guarda cuando paso cada cosa; el nivel que se muestra es el MAS RECIENTE de
 * los que siguen vigentes:
 *  - nivel 1: desde la alerta "A" hasta que la vincha avisa que termino ("AF")
 *  - nivel 2 (microsueño) y nivel 3 (cabeceo): durante 20 s despues del evento
 *    (la misma ventana que usa el firmware para decidir si un microsueño esta aislado)
 * Si no hay ninguno vigente: normal (0).
 */
data class EstadoNivel(
    val nivel1DesdeMs: Long? = null,     // null = el nivel 1 no esta activo
    val ultimoNivel2Ms: Long? = null,
    val ultimoNivel3Ms: Long? = null
) {
    fun nivelEn(ahoraMs: Long): Int {
        val vigentes = buildList {
            nivel1DesdeMs?.let { add(1 to it) }
            ultimoNivel2Ms?.takeIf { ahoraMs - it <= VIGENCIA_MS }?.let { add(2 to it) }
            ultimoNivel3Ms?.takeIf { ahoraMs - it <= VIGENCIA_MS }?.let { add(3 to it) }
        }
        return vigentes.maxByOrNull { it.second }?.first ?: 0
    }

    companion object {
        const val VIGENCIA_MS = 20_000L
    }
}

/** "Normal", "Nivel 1 · somnolencia", ... */
fun nombreDeNivel(nivel: Int): String = when (nivel) {
    1 -> "Nivel 1 · somnolencia"
    2 -> "Nivel 2 · microsueño"
    3 -> "Nivel 3 · cabeceo"
    else -> "Normal"
}
