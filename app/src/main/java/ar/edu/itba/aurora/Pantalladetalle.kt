package ar.edu.itba.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun PantallaDetalle(viaje: Viaje, onVolver: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Encabezado
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                IconButton(onClick = onVolver, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${viaje.origen} -> ${viaje.destino}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "${viaje.fecha} - ${viaje.horario}",
                    style = DatoMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

            // Dos métricas
            Row(modifier = Modifier.fillMaxWidth()) {
                Metrica("duracion", viaje.duracion, Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(80.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
                Metrica("eventos", viaje.eventos.size.toString(), Modifier.weight(1f))
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

            // Gráfico de eventos por hora
            if (viaje.eventosPorHora.isNotEmpty()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "eventos por hora",
                        style = EtiquetaMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    GraficoBarras(viaje.eventosPorHora)
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        viaje.etiquetasHora.forEach {
                            Text(it, style = EtiquetaMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (viaje.resumen.isNotBlank()) {
                        Text(
                            viaje.resumen,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            Text(
                "linea de tiempo",
                style = EtiquetaMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

            viaje.eventos.forEach { evento ->
                FilaEvento(evento)
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            }

            Box(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Metrica(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(etiqueta, style = EtiquetaMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            valor,
            style = NumeroMediano,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun GraficoBarras(valores: List<Int>) {
    val maximo = (valores.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        valores.forEach { valor ->
            val proporcion = valor.toFloat() / maximo
            val color = when {
                proporcion > 0.75f -> Nivel3
                proporcion > 0.5f -> Nivel2
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(proporcion.coerceAtLeast(0.05f))
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun FilaEvento(evento: Evento) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .background(colorDeNivel(evento.nivel))
        )
        Row(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                evento.hora,
                style = DatoMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "   ${evento.tipo}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}