package ar.edu.itba.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * DURACION: duracion basal del parpadeo. Una vez por chofer (obligatoria
 *           antes del primer viaje) y desde Configuracion -> Recalibrar. 60 s.
 * AMPLITUD: antes de cada viaje (umbral y polaridad). Hasta 30 s. Si sale
 *           bien, la vincha vibra y el viaje empieza solo.
 */
enum class TipoCalibracion { DURACION, AMPLITUD }

private sealed class EstadoCal {
    data object Instrucciones : EstadoCal()
    // recibio = llego al menos un mensaje de progreso (la vincha esta calibrando)
    // pregunto = ya se le pidio el estado ("S") porque el resultado no llegaba
    data class Midiendo(
        val inicioMs: Long,
        val segundos: Int = 0,
        val parpadeos: Int = 0,
        val recibio: Boolean = false,
        val pregunto: Boolean = false
    ) : EstadoCal()
    data class Lista(val basalMs: Int) : EstadoCal()          // solo DURACION
    data class Fallo(val titulo: String, val detalle: String) : EstadoCal()
}

// Lo que tarda la vincha como mucho (firmware v18.1): la de amplitud suma
// 2 s de base y postura al final. Si a los PREGUNTAR_S de margen no llego el
// resultado, se le pregunta el estado; a los MARGEN_S se da por fallida.
private const val DURACION_S = 60
private const val AMPLITUD_MAX_S = 30
private const val PREGUNTAR_S = 8
private const val MARGEN_S = 20

/** El mensaje para el conductor segun el codigo de error de la vincha. */
private fun falloPara(codigo: String): EstadoCal.Fallo = when (codigo) {
    "POL" -> EstadoCal.Fallo(
        "Date vuelta la vincha",
        "Los parpadeos se detectan al revés: la vincha está puesta al revés. " +
                "Dala vuelta y reintentá la calibración."
    )
    "SINP" -> EstadoCal.Fallo(
        "No se detectaron parpadeos",
        "¿La vincha está encendida y bien puesta?"
    )
    else -> EstadoCal.Fallo("No se pudo calibrar", "Reintentá la calibración.")
}

@Composable
fun PantallaCalibracion(
    tipo: TipoCalibracion,
    basalMs: Int?,                                 // AMPLITUD: el basal del chofer (se le manda a la vincha)
    onDuracionOk: (basalMs: Int, parpadeos: Int) -> Unit = { _, _ -> },
    onAmplitudOk: (umbralMv: Int, parpadeos: Int) -> Unit = { _, _ -> },
    onSalir: () -> Unit                            // volver (AMPLITUD: cancelar el viaje)
) {
    val esDuracion = tipo == TipoCalibracion.DURACION
    var estado by remember { mutableStateOf<EstadoCal>(EstadoCal.Instrucciones) }
    var ahoraMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val estadoVincha by Vincha.estado.collectAsState()
    val conectada = estadoVincha == EstadoVincha.CONECTADA
    val maxS = if (esDuracion) DURACION_S else AMPLITUD_MAX_S

    // Lo que responde la vincha
    LaunchedEffect(Unit) {
        Vincha.calibracion.collect { m ->
            val actual = estado
            if (actual !is EstadoCal.Midiendo) return@collect
            when (m) {
                is MensajeCalibracion.Progreso ->
                    estado = actual.copy(segundos = m.segundos, parpadeos = m.parpadeos, recibio = true)
                // Respuesta a "S" (el resultado no llegaba). Para la del viaje alcanza:
                // si la vincha esta en viaje, la calibracion salio bien.
                is MensajeCalibracion.Estado -> if (!esDuracion && actual.pregunto) {
                    if (m.enViaje) onAmplitudOk(m.umbralMv, actual.parpadeos)
                    else estado = falloPara("ERR")
                }
                is MensajeCalibracion.DuracionOk -> if (esDuracion) {
                    onDuracionOk(m.basalMs, m.parpadeos)
                    estado = EstadoCal.Lista(m.basalMs)
                }
                is MensajeCalibracion.AmplitudOk -> if (!esDuracion) {
                    onAmplitudOk(m.umbralMv, m.parpadeos)   // MainActivity crea el viaje y cambia de pantalla
                }
                is MensajeCalibracion.Error ->
                    if (m.duracion == esDuracion && m.codigo != "CANC") estado = falloPara(m.codigo)
                is MensajeCalibracion.Inicio -> {}
            }
        }
    }

    // Reloj de la barra de progreso y tiempo maximo de espera
    val midiendo = estado as? EstadoCal.Midiendo
    LaunchedEffect(midiendo?.inicioMs) {
        val inicio = midiendo?.inicioMs ?: return@LaunchedEffect
        while (true) {
            ahoraMs = System.currentTimeMillis()
            val actual = estado as? EstadoCal.Midiendo ?: break
            // El resultado no llego a tiempo: se le pregunta a la vincha en que esta
            if (!actual.pregunto && ahoraMs - inicio > (maxS + PREGUNTAR_S) * 1000L) {
                estado = actual.copy(pregunto = true)
                Vincha.enviar("S")
            }
            if (ahoraMs - inicio > (maxS + MARGEN_S) * 1000L) {
                Vincha.enviar("X")
                // Si mando progreso, la vincha si estaba calibrando: lo que no llego fue el final
                estado = if (actual.recibio) falloPara("ERR") else EstadoCal.Fallo(
                    "La vincha no respondió",
                    "Fijate que esté prendida y cerca del teléfono, y reintentá."
                )
                break
            }
            delay(250)
        }
    }

    // Si se corta el Bluetooth en el medio, no tiene sentido seguir esperando
    LaunchedEffect(conectada) {
        if (!conectada && estado is EstadoCal.Midiendo) {
            estado = EstadoCal.Fallo(
                "Se perdió la conexión con la vincha",
                "Fijate que esté prendida y cerca del teléfono, y reintentá."
            )
        }
    }

    val empezar: () -> Unit = {
        val comando = if (esDuracion) "CD" else "CA,${basalMs ?: 0}"
        estado = if (Vincha.enviar(comando)) {
            ahoraMs = System.currentTimeMillis()
            EstadoCal.Midiendo(ahoraMs)
        } else {
            EstadoCal.Fallo(
                "No se pudo hablar con la vincha",
                if (conectada) "La vincha no acepta la calibración: puede que tenga un firmware viejo."
                else "Conectá la vincha y reintentá."
            )
        }
    }

    val salir: () -> Unit = {
        if (estado is EstadoCal.Midiendo) Vincha.enviar("X")
        onSalir()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            IconButton(onClick = salir, modifier = Modifier.padding(top = 8.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (val e = estado) {
                is EstadoCal.Instrucciones -> Instrucciones(
                    esDuracion = esDuracion,
                    conectada = conectada,
                    onEmpezar = empezar
                )

                is EstadoCal.Midiendo -> {
                    val transcurrido = ((ahoraMs - e.inicioMs) / 1000).toInt().coerceAtLeast(0)
                    Midiendo(
                        esDuracion = esDuracion,
                        progreso = (transcurrido.toFloat() / maxS).coerceIn(0f, 1f),
                        terminando = transcurrido >= maxS,
                        segundos = transcurrido.coerceAtMost(maxS),
                        maxS = maxS,
                        parpadeos = e.parpadeos,
                        onCancelar = {
                            Vincha.enviar("X")
                            estado = EstadoCal.Instrucciones
                        }
                    )
                }

                is EstadoCal.Lista -> {
                    Titulo("Calibración lista")
                    Text(
                        "Tu duración de parpadeo de referencia es de ${e.basalMs} ms. " +
                                "Ya podés iniciar viajes.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onSalir,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                    ) { Text("Continuar") }
                }

                is EstadoCal.Fallo -> {
                    Titulo(e.titulo, color = Peligro)
                    Text(
                        e.detalle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!conectada) {
                        Box(modifier = Modifier.padding(top = 20.dp)) {
                            Column { ControlVincha() }
                        }
                    }
                    Button(
                        onClick = empezar,
                        enabled = conectada,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                    ) { Text("Reintentar calibración") }
                    OutlinedButton(
                        onClick = onSalir,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 32.dp)
                    ) { Text(if (esDuracion) "Volver" else "Cancelar viaje") }
                }
            }
        }
    }
}

@Composable
private fun Titulo(texto: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground) {
    Text(
        texto,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier.padding(top = 8.dp)
    )
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 24.dp)
            .width(24.dp)
            .height(3.dp)
            .background(MaterialTheme.colorScheme.primary)
    )
}

@Composable
private fun Instrucciones(esDuracion: Boolean, conectada: Boolean, onEmpezar: () -> Unit) {
    Titulo(if (esDuracion) "Calibración inicial" else "Calibración antes del viaje")
    Text(
        if (esDuracion)
            "Se hace una sola vez. Mide cómo parpadeás cuando estás despierto, para después " +
                    "darnos cuenta si empezás a tener sueño. Hacela cuando estés descansado y bien despierto."
        else
            "Antes de cada viaje, la vincha se ajusta a cómo está puesta hoy. Dura hasta 30 segundos.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )

    val pasos = if (esDuracion) listOf(
        "Ponete la vincha y prendela.",
        "Sentate cómodo y quieto, mirando un punto fijo al frente.",
        "Parpadeá normal, sin forzar ni aguantar los parpadeos.",
        "Dura 1 minuto. Al empezar, dejá el celular: la vincha vibra cuando termina."
    ) else listOf(
        "Ponete la vincha, prendela y sentate en el asiento.",
        "Mirá al frente por el parabrisas y quedate quieto.",
        "Parpadeá normal, sin forzar.",
        "Al empezar, dejá el celular. Cuando la vincha vibre, el viaje arranca solo."
    )
    Column(modifier = Modifier.padding(top = 20.dp)) {
        pasos.forEachIndexed { i, paso ->
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    "${i + 1}",
                    style = DatoMono,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )
                Text(paso, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    // Sin la vincha conectada no se puede calibrar: se puede conectar desde aca
    if (!conectada) {
        Text(
            "Conectá la vincha para empezar.",
            style = MaterialTheme.typography.bodySmall,
            color = Peligro,
            modifier = Modifier.padding(top = 20.dp)
        )
        ControlVincha()
    }

    Button(
        onClick = onEmpezar,
        enabled = conectada,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 32.dp)
    ) { Text("Empezar calibración") }
}

@Composable
private fun Midiendo(
    esDuracion: Boolean,
    progreso: Float,
    terminando: Boolean,
    segundos: Int,
    maxS: Int,
    parpadeos: Int,
    onCancelar: () -> Unit
) {
    Titulo(if (terminando) "Terminando..." else "Calibrando...")
    Text(
        "Dejá el celular, mirá al frente y parpadeá normal.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    LinearProgressIndicator(
        progress = { progreso },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    )
    Row(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            "$segundos / $maxS s",
            style = DatoMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$parpadeos parpadeos",
            style = DatoMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        if (esDuracion) "La vincha vibra cuando termina."
        else "Puede terminar antes si ya juntó suficientes parpadeos. Cuando la vincha vibre, el viaje arranca solo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp)
    )
    TextButton(
        onClick = onCancelar,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 32.dp)
    ) { Text("Cancelar") }
}