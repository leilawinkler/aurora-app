package ar.edu.itba.aurora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Detalle de un viaje. Arriba las tres cifras del viaje, despues el grafico
 * "eventos a lo largo del viaje" (el mismo de la solapa "Eventos por viaje"
 * del historial de la web) y abajo la lista de eventos.
 */
@Composable
fun PantallaDetalle(viaje: Viaje, onVolver: () -> Unit) {
    val oscuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val n1 = viaje.eventos.count { it.nivel == 1 }
    val n2 = viaje.eventos.count { it.nivel >= 2 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .rastroAurora(oscuro)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // --- Encabezado ---
        IconButton(onClick = onVolver, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${viaje.origen} ➜ ${viaje.destino}",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 18.dp)
        ) {
            Text(
                "${viaje.fecha} · ${viaje.horario}",
                style = DatoMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (viaje.enCurso) {
                Pastilla("en curso", MaterialTheme.colorScheme.secondary, Modifier.padding(start = 10.dp))
            }
        }

        // --- Tres cifras, como las tarjetas de la web ---
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TarjetaCifra("Duración", viaje.duracion, RosaAurora, Modifier.weight(1.3f))
            TarjetaCifra("Nivel 1", n1.toString(), Nivel1, Modifier.weight(1f))
            TarjetaCifra("Nivel 2", n2.toString(), Nivel2, Modifier.weight(1f))
        }

        // --- Grafico ---
        Tarjeta(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                "Eventos a lo largo del viaje",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ContenedorGraficoViaje(viaje)
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Leyenda(Nivel1, "Nivel 1", "precaución")
                Leyenda(Nivel2, "Nivel 2", "alerta", Modifier.padding(start = 14.dp))
            }
            Text(
                "${viaje.eventos.size} eventos · ${viaje.duracion}" + if (viaje.enCurso) " · en curso" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // --- Lista de eventos ---
        Tarjeta(modifier = Modifier.padding(top = 14.dp, bottom = 24.dp)) {
            Text(
                "Eventos",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            if (viaje.eventos.isEmpty()) {
                Text(
                    "Sin eventos en este viaje.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            viaje.eventos.forEachIndexed { i, evento ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                FilaEvento(evento)
            }
        }
    }
}

// ============================================================================
// El grafico
// ============================================================================

/**
 * Envuelve el grafico en un scroll horizontal. En el celular hay mucho menos
 * ancho que en la pantalla de un administrador, asi que si el viaje tiene
 * varios eventos el grafico se dibuja mas ancho que la pantalla y se puede
 * deslizar el dedo para separarlos, en vez de apretarlos todos junto.
 */
@Composable
private fun ContenedorGraficoViaje(viaje: Viaje) {
    BoxWithConstraints {
        // Un evento cada 46dp como minimo, para que los puntos no se pisen.
        val anchoMinimo = 76.dp + 46.dp * viaje.eventos.size
        val ancho = maxOf(maxWidth, anchoMinimo)

        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            GraficoViaje(viaje, ancho)
        }
    }
}

/**
 * Cada evento es un palito con un punto, ubicado en el momento en que
 * ocurrio. El eje horizontal va de que arranco el viaje a que termino (o a
 * ahora, si sigue abierto). Nivel 1 queda a media altura y nivel 2 arriba.
 * Es el mismo dibujo que hace la web en dibujarGraficoViaje().
 */
@Composable
private fun GraficoViaje(viaje: Viaje, ancho: Dp) {
    val medidor = rememberTextMeasurer()
    val colorTexto = MaterialTheme.colorScheme.onSurfaceVariant
    val colorLinea = colorTexto.copy(alpha = 0.25f)
    val estilo = TextStyle(fontSize = 11.sp, color = colorTexto)

    // Si el viaje sigue abierto, el eje crece solo cada 30 segundos.
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (viaje.enCurso) {
        LaunchedEffect(viaje.id) {
            while (true) {
                delay(30_000)
                ahora = System.currentTimeMillis()
            }
        }
    }

    Canvas(
        modifier = Modifier
            .width(ancho)
            .height(220.dp)
    ) {
        val t0 = viaje.inicioMs
        if (t0 == null) {
            val r = medidor.measure("No hay datos de tiempo para este viaje.", estilo)
            drawText(r, topLeft = Offset((size.width - r.size.width) / 2, size.height / 2))
            return@Canvas
        }
        val t1 = viaje.finMs ?: ahora
        // Piso de un minuto para que un viaje recien arrancado no quede aplastado.
        val duracion = maxOf(60_000L, t1 - t0).toFloat()

        val izq = 38.dp.toPx()
        val der = 10.dp.toPx()
        val abajo = 26.dp.toPx()
        val arriba = 14.dp.toPx()
        val anchoArea = size.width - izq - der
        val altoArea = size.height - abajo - arriba
        val base = arriba + altoArea

        fun xDe(ms: Long) = izq + ((ms - t0) / duracion) * anchoArea
        fun yDe(nivel: Int) = base - if (nivel >= 2) altoArea * 0.85f else altoArea * 0.45f

        // Lineas guia de los dos niveles, con su rotulo
        listOf(1, 2).forEach { nivel ->
            val y = yDe(nivel)
            drawLine(colorLinea, Offset(izq, y), Offset(size.width - der, y), strokeWidth = 1.dp.toPx())
            val r = medidor.measure("N$nivel", estilo)
            drawText(r, topLeft = Offset(izq - 8.dp.toPx() - r.size.width, y - r.size.height / 2))
        }

        // Linea del piso
        drawLine(colorLinea, Offset(izq, base), Offset(size.width - der, base), strokeWidth = 1.dp.toPx())

        // Cinco marcas de hora
        for (i in 0..4) {
            val ms = t0 + (duracion * i / 4).toLong()
            val r = medidor.measure(horaDelDia(ms), estilo)
            val x = (xDe(ms) - r.size.width / 2)
                .coerceIn(0f, size.width - r.size.width.toFloat())
            drawText(r, topLeft = Offset(x, size.height - r.size.height))
        }

        if (viaje.eventos.isEmpty()) {
            val r = medidor.measure("Sin eventos en este viaje.", estilo)
            drawText(r, topLeft = Offset((size.width - r.size.width) / 2, arriba + altoArea / 2 - r.size.height / 2))
            return@Canvas
        }

        // Un palito con su punto por cada evento
        viaje.eventos.forEach { ev ->
            if (ev.momentoMs <= 0L) return@forEach
            val x = xDe(ev.momentoMs).coerceIn(izq, size.width - der)
            val y = yDe(ev.nivel)
            val color = colorDeNivel(ev.nivel)
            drawLine(color, Offset(x, base), Offset(x, y), strokeWidth = 2.dp.toPx())
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

// ============================================================================
// Piezas
// ============================================================================

/** Tarjeta blanca con hilo fino, como .history-chart-card de la web. */
@Composable
private fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, forma)
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
            .padding(16.dp)
    ) { contenido() }
}

/** Cifra con una franja de color arriba, como las tarjetas KPI de la web. */
@Composable
private fun TarjetaCifra(etiqueta: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            // El clip es lo que evita que la franja de color se salga de la
            // tarjeta: sin esto, una franja tan bajita (3dp) con esquinas
            // redondeadas grandes se dibuja mas ancha que el borde.
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface, forma)
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(color)
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                etiqueta.uppercase(),
                style = EtiquetaMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                valor,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun Leyenda(color: Color, titulo: String, texto: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(
            "$titulo · $texto",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun Pastilla(texto: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
private fun FilaEvento(evento: Evento) {
    val color = colorDeNivel(evento.nivel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            evento.hora,
            style = DatoMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp).width(52.dp)
        )
        Text(
            evento.tipo,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Pastilla("Nivel ${evento.nivel}", color)
    }
}