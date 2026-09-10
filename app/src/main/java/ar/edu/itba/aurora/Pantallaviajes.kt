package ar.edu.itba.aurora

import androidx.compose.foundation.background
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PantallaViajes(
    viajeEnCurso: Viaje?,
    viajes: List<Viaje>,
    temaOscuro: Boolean,
    onCambiarTema: (Boolean) -> Unit,
    onNuevoViaje: () -> Unit,
    onAbrirViaje: (Viaje) -> Unit,
    onIrAConfiguracion: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BarraInferior(
                seleccion = 0,
                onViajes = {},
                onConfiguracion = onIrAConfiguracion
            )
        }
    ) { relleno ->
        Column(modifier = Modifier.padding(relleno)) {

            // Encabezado
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("aurora", style = MarcaChica, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Ruben Gonzalez",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                IconButton(onClick = { onCambiarTema(!temaOscuro) }) {
                    Icon(
                        imageVector = if (temaOscuro) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Cambiar tema",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(onClick = onNuevoViaje) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuevo viaje")
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (viajeEnCurso != null) {
                    item { TarjetaViajeEnCurso(viajeEnCurso) }
                }

                item {
                    Text(
                        "anteriores",
                        style = EtiquetaMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                    )
                }

                items(viajes) { viaje ->
                    TarjetaViaje(viaje = viaje, onClick = { onAbrirViaje(viaje) })
                }
            }
        }
    }
}

@Composable
private fun TarjetaViajeEnCurso(viaje: Viaje) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Column(modifier = Modifier.padding(18.dp)) {
                Text("en curso", style = EtiquetaMono, color = MaterialTheme.colorScheme.secondary)
                Text(
                    "${viaje.origen} ➜ ${viaje.destino}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    viaje.duracion,
                    style = NumeroGrande,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "${viaje.eventos.size} eventos - AURORA-01 conectado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TarjetaViaje(viaje: Viaje, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(colorDeNivel(viaje.nivelMaximo))
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${viaje.origen} ➜ ${viaje.destino}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        viaje.fecha,
                        style = EtiquetaMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${viaje.duracion} h - ${viaje.eventos.size} eventos - max N${viaje.nivelMaximo}",
                    style = DatoMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (!viaje.sincronizado) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.width(16.dp)
                        )
                        Text(
                            "Sin subir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BarraInferior(
    seleccion: Int,
    onViajes: () -> Unit,
    onConfiguracion: () -> Unit
) {
    val coloresItem = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = seleccion == 0,
            onClick = onViajes,
            icon = { Icon(Icons.Filled.Route, contentDescription = null) },
            label = { Text("Viajes") },
            colors = coloresItem
        )
        NavigationBarItem(
            selected = seleccion == 1,
            onClick = onConfiguracion,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Config") },
            colors = coloresItem
        )
    }
}