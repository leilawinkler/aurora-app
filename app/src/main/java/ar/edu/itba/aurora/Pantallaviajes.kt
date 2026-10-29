package ar.edu.itba.aurora

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PantallaViajes(
    viajeEnCurso: Viaje?,
    viajes: List<Viaje>,
    temaOscuro: Boolean,
    onCambiarTema: (Boolean) -> Unit,
    onNuevoViaje: () -> Unit,
    onAbrirViaje: (Viaje) -> Unit,
    onIrAConfiguracion: () -> Unit,
    nombreChofer: String = "",
    estadoSync: EstadoSync = EstadoSync.SINCRONIZADO,
    pendientesSync: Int = 0,
    hayInternet: Boolean = true,
    estadoVincha: EstadoVincha = EstadoVincha.DESCONECTADA,
    vinchaVinculada: Boolean = false,
    bluetoothActivo: Boolean = true,
    historialCompleto: Boolean = true,
    necesitaClave: Boolean = false,
    onIngresarClave: () -> Unit = {},
    cargando: Boolean = false,
    error: String? = null,
    finalizando: Boolean = false,
    onFinalizarViaje: () -> Unit = {},
    // Lo que llega de la vincha durante el viaje en curso
    parpadeosViaje: Int = 0,
    ultimoParpadeo: Parpadeo? = null,
    vinchaConectada: Boolean = false
) {
    Scaffold(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .rastroAurora(temaOscuro),
        containerColor = Color.Transparent,
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
                        nombreChofer,
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

                FilledIconButton(
                    onClick = onNuevoViaje,
                    enabled = viajeEnCurso == null
                ) {
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
                item {
                    RenglonEstado(
                        estadoSync, hayInternet,
                        estadoVincha, vinchaVinculada, bluetoothActivo
                    )
                }

                // Hay señal pero falta la contraseña para abrir la sesion y
                // subir. No bloquea nada: el chofer puede seguir trabajando.
                if (necesitaClave) {
                    item { AvisoClave(onIngresarClave) }
                }

                if (viajeEnCurso != null) {
                    item {
                        TarjetaViajeEnCurso(
                            viaje = viajeEnCurso,
                            finalizando = finalizando,
                            onFinalizar = onFinalizarViaje,
                            onAbrir = { onAbrirViaje(viajeEnCurso) },
                            parpadeos = parpadeosViaje,
                            ultimoParpadeo = ultimoParpadeo,
                            vinchaConectada = vinchaConectada
                        )
                    }
                }

                item {
                    Text(
                        "anteriores",
                        style = EtiquetaMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                    )
                }

                if (cargando && viajes.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (error != null) {
                    item {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                if (!cargando && error == null && viajes.isEmpty() && historialCompleto) {
                    item {
                        Text(
                            "Todavia no hay viajes registrados. Toca + para iniciar uno.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                items(viajes) { viaje ->
                    TarjetaViaje(viaje = viaje, onClick = { onAbrirViaje(viaje) })
                }

                // Sin señal solo estan los viajes guardados en el telefono (los
                // que no se subieron). El resto del historial esta en la nube,
                // asi que el aviso va debajo de esos viajes.
                // Mientras sincroniza no se muestra, para no hacer parpadear.
                if (!historialCompleto && estadoSync != EstadoSync.SINCRONIZANDO) {
                    item { AvisoHistorial() }
                }
            }
        }
    }
}

@Composable
private fun TarjetaViajeEnCurso(
    viaje: Viaje,
    finalizando: Boolean,
    onFinalizar: () -> Unit,
    onAbrir: () -> Unit,
    parpadeos: Int,
    ultimoParpadeo: Parpadeo?,
    vinchaConectada: Boolean
) {
    Card(
        onClick = onAbrir,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Column(modifier = Modifier.padding(18.dp)) {
                // Se actualiza solo, una vez por segundo.
                val ahora = ahoraQueLate()

                Text("en curso", style = EtiquetaMono, color = MaterialTheme.colorScheme.secondary)
                Text(
                    "${viaje.origen} ➜ ${viaje.destino}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Text(
                    cronometro(viaje.inicioMs, ahora),
                    style = NumeroGrande,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    "al volante",
                    style = EtiquetaMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "hora",
                            style = EtiquetaMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            horaDelDia(ahora),
                            style = NumeroMediano,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "eventos",
                            style = EtiquetaMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            viaje.eventos.size.toString(),
                            style = NumeroMediano,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // --- Parpadeos en vivo, desde la vincha ---
                FilaParpadeos(parpadeos, ultimoParpadeo, vinchaConectada)

                Button(
                    onClick = onFinalizar,
                    enabled = !finalizando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(if (finalizando) "Finalizando..." else "Finalizar viaje")
                }
            }
        }
    }
}

@Composable
private fun TarjetaViaje(viaje: Viaje, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(colorDeNivel(viaje.nivelMaximo))
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${viaje.origen} ➜ ${viaje.destino}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            viaje.fecha,
                            style = EtiquetaMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            viaje.duracion,
                            style = DatoMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
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
                            " Sin subir",
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

/**
 * Parpadeos del viaje en curso: cuantos llegaron de la vincha y como fue el
 * ultimo. El punto se prende un instante con cada parpadeo, para ver en el
 * momento que la vincha los esta detectando.
 * Solo los parpadeos lentos se guardan como eventos (ver MainActivity).
 */
@Composable
private fun FilaParpadeos(cantidad: Int, ultimo: Parpadeo?, conectada: Boolean) {
    var encendido by remember { mutableStateOf(false) }
    LaunchedEffect(ultimo) {
        if (ultimo == null) return@LaunchedEffect
        encendido = true
        delay(350)
        encendido = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "parpadeos",
                    style = EtiquetaMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (encendido) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
            Text(
                cantidad.toString(),
                style = NumeroMediano,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "último",
                style = EtiquetaMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                when {
                    ultimo == null -> "-"
                    ultimo.lento -> "${ultimo.duracionMs} ms · lento"
                    else -> "${ultimo.duracionMs} ms"
                },
                style = DatoMono,
                color = if (ultimo?.lento == true) colorDeNivel(1) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }

    if (!conectada) {
        Text(
            "La vincha no está conectada: no se están registrando parpadeos. " +
                    "Vinculala desde Configuración.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Devuelve la hora actual (en milisegundos) y se refresca cada segundo,
 * asi el cronometro del viaje en curso avanza a la vista.
 */
@Composable
private fun ahoraQueLate(): Long {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            ahora = System.currentTimeMillis()
        }
    }
    return ahora
}

/**
 * Renglon de estado: a la izquierda internet / sincronizacion, a la derecha
 * la vincha (Bluetooth). El chofer no tiene que hacer nada con el: solo le
 * dice como estan las cosas.
 */
@Composable
private fun RenglonEstado(
    estadoSync: EstadoSync,
    hayInternet: Boolean,
    estadoVincha: EstadoVincha,
    vinculada: Boolean,
    bluetoothActivo: Boolean
) {
    val (textoSync, colorSync) = when {
        estadoSync == EstadoSync.SINCRONIZANDO ->
            "sincronizando..." to MaterialTheme.colorScheme.secondary
        !hayInternet || estadoSync == EstadoSync.SIN_CONEXION ->
            "sin internet" to MaterialTheme.colorScheme.error
        else ->
            "sincronizado" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val (textoVincha, colorVincha) = when {
        !bluetoothActivo -> "bluetooth apagado" to MaterialTheme.colorScheme.error
        !vinculada -> "vincha sin vincular" to MaterialTheme.colorScheme.onSurfaceVariant
        estadoVincha == EstadoVincha.CONECTADA ->
            "vincha conectada" to MaterialTheme.colorScheme.secondary
        estadoVincha == EstadoVincha.DESCONECTADA ->
            "vincha desconectada" to MaterialTheme.colorScheme.error
        else -> "buscando vincha..." to MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // En pantallas angostas, si no entra, se corta con "..." en vez de romper la fila
        PuntoYTexto(textoSync, colorSync, Modifier.weight(1f, fill = false))
        Text(
            "|",
            style = EtiquetaMono,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        PuntoYTexto(textoVincha, colorVincha, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun PuntoYTexto(texto: String, color: Color, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            texto,
            style = EtiquetaMono,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Aviso de "sin señal no se puede ver el historial". Mismo formato que el
 * estado vacio de la web: emoji, titulo en negrita y una linea de texto.
 */
@Composable
private fun AvisoHistorial() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp)
    ) {
        Text("🔌", fontSize = 30.sp)
        Text(
            "Historial sin conexión",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            "Conectate a internet para ver tus viajes anteriores.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Aviso de "hay señal pero falta la contraseña para subir". */
@Composable
private fun AvisoClave(onIngresarClave: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, forma)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), forma)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            "Volvió la señal. Para subir tus viajes y ver el historial, " +
                    "escribí tu contraseña.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        TextButton(onClick = onIngresarClave, modifier = Modifier.padding(top = 2.dp)) {
            Text("Escribir contraseña")
        }
    }
}

/**
 * Ventana para escribir la contraseña y abrir la sesion con Supabase.
 * Se puede cerrar sin escribir nada: la app sigue funcionando igual.
 */
@Composable
fun DialogoClave(
    enviando: Boolean,
    error: String?,
    onCancelar: () -> Unit,
    onConfirmar: (String) -> Unit
) {
    var clave by remember { mutableStateOf("") }
    var verClave by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!enviando) onCancelar() },
        title = { Text("Tu contraseña") },
        text = {
            Column {
                Text(
                    "La necesitamos para subir lo que registraste sin señal.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    singleLine = true,
                    enabled = !enviando,
                    visualTransformation =
                        if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { verClave = !verClave }) {
                            Icon(
                                if (verClave) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (verClave) "Ocultar contraseña" else "Ver contraseña"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(clave) },
                enabled = !enviando && clave.isNotBlank()
            ) {
                Text(if (enviando) "Entrando..." else "Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !enviando) { Text("Más tarde") }
        }
    )
}