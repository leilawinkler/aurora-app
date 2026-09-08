package ar.edu.itba.aurora

data class Evento(
    val hora: String,
    val tipo: String,
    val nivel: Int
)

data class Viaje(
    val id: Int,
    val origen: String,
    val destino: String,
    val fecha: String,
    val duracion: String,
    val horario: String,
    val eventos: List<Evento>,
    val enCurso: Boolean = false,
    val sincronizado: Boolean = true,
    val eventosPorHora: List<Int> = emptyList(),
    val etiquetasHora: List<String> = emptyList(),
    val resumen: String = ""
) {
    val nivelMaximo: Int
        get() = eventos.maxOfOrNull { it.nivel } ?: 0
}

object DatosDeEjemplo {

    val viajeEnCurso = Viaje(
        id = 0,
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
            id = 1,
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
            id = 2,
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