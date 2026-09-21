package ar.edu.itba.aurora

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

// ============================================================================
// Colores de la escena de ingreso, en version noche y version dia
// ============================================================================
// La noche es el login de la web tal cual. El dia es el mismo cielo pero
// "amanecido": mismo rosa, violeta y ambar sobre papel calido, para que al
// sol no encandile un fondo oscuro con una tarjeta blanca en el medio.

private class ColoresEscena(
    val cielo: List<Pair<Float, Color>>,
    val bandas: List<Pair<Color, Float>>,   // color y opacidad de cada banda
    val destello: Color,
    val tarjeta: Color,
    val bordeTarjeta: Color,
    val texto: Color,
    val textoSuave: Color,
    val etiqueta: Color,
    val campoFondo: Color,
    val campoBorde: Color,
    val campoFoco: Color,
    val icono: Color
)

private val EscenaNoche = ColoresEscena(
    cielo = listOf(0f to Color(0xFF1B1235), 0.45f to Color(0xFF0D1026), 1f to Color(0xFF080A18)),
    bandas = listOf(
        RosaAurora to 0.55f, VioletaAurora to 0.50f,
        AmbarAurora to 0.42f, CremaLogo to 0.34f
    ),
    destello = Color(0xFFFDFAF6),
    tarjeta = Color(0x8114172E),
    bordeTarjeta = Color(0x8114172E),
    texto = Color(0xFFF2EEF4),
    textoSuave = Color(0xFFA7A1BC),
    etiqueta = Color(0xFFCFC8DC),
    campoFondo = Color(0xFF1C2040),
    campoBorde = Color(0xFF3A4070),
    campoFoco = Color(0xFFE4899C),
    icono = CremaLogo
)

private val EscenaDia = ColoresEscena(
    cielo = listOf(0f to Color(0xFFFFF4EF), 0.5f to Color(0xFFF8F5F2), 1f to Color(0xFFF1EAE3)),
    bandas = listOf(
        RosaAurora to 0.40f, VioletaAurora to 0.22f,
        AmbarAurora to 0.30f, Color(0xFFFFB59E) to 0.30f
    ),
    destello = RosaAurora,
    tarjeta = Color(0x8BFFFBF2),              // la tarjeta crema de la web
    bordeTarjeta = Color(0x8BFFFBF2),
    texto = Color(0xFF1C1B2E),
    textoSuave = Color(0xFF8B8499),
    etiqueta = Color(0xFF56526A),
    campoFondo = Color(0xFFFDFAF6),
    campoBorde = Color(0xFFDDCFC4),           // mas marcado que en la web, a pedido
    campoFoco = Color(0xFFE4899C),
    icono = Color(0xFF56526A)
)

private fun coloresDe(oscuro: Boolean) = if (oscuro) EscenaNoche else EscenaDia

// ============================================================================
// Reloj de la animacion
// ============================================================================

/**
 * Milisegundos desde que se abrio la pantalla. Todo el fondo se dibuja a
 * partir de este unico numero, asi Compose solo repinta el lienzo y no
 * recalcula la pantalla entera en cada cuadro.
 *
 * Si el telefono tiene las animaciones apagadas (accesibilidad), el reloj
 * queda quieto: es el equivalente de "prefers-reduced-motion" de la web.
 */
@Composable
private fun relojAnimacion(): () -> Long {
    val contexto = LocalContext.current
    val sinAnimaciones = remember {
        runCatching {
            Settings.Global.getFloat(
                contexto.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
    }
    var tiempo by remember { mutableLongStateOf(0L) }
    if (!sinAnimaciones) {
        LaunchedEffect(Unit) {
            val inicio = withFrameMillis { it }
            while (true) {
                withFrameMillis { tiempo = it - inicio }
            }
        }
    }
    return { tiempo }
}

// ============================================================================
// Fondo: cielo, bandas de aurora y destellos
// ============================================================================

private class Banda(
    val x: Float, val y: Float,        // centro, en fraccion de la pantalla
    val radio: Float,                  // en fraccion del ancho
    val periodo: Float,                // segundos que tarda en ir y volver
    val ampX: Float, val ampY: Float   // cuanto se desplaza
)

// Mismas cuatro bandas de la web, acomodadas para una pantalla vertical.
private val BANDAS = listOf(
    Banda(0.15f, 0.12f, 0.95f, 22f, 0.10f, 0.06f),
    Banda(0.95f, 0.42f, 0.90f, 21f, -0.12f, 0.05f),
    Banda(0.40f, 0.92f, 0.95f, 19f, 0.14f, -0.05f),
    Banda(0.80f, 0.74f, 0.55f, 16f, -0.10f, -0.06f)
)

private class Destello(
    val x: Float, val y: Float, val retardo: Float,
    val tam: Float, val intensidad: Float, val periodo: Float
)

// Los doce destellos de la web, con sus mismas posiciones y ritmos.
private val DESTELLOS = listOf(
    Destello(0.14f, 0.22f, 1.5f, 1f, 0.9f, 12f),
    Destello(0.78f, 0.16f, 2.7f, 0.65f, 0.7f, 16f),
    Destello(0.88f, 0.62f, 5.4f, 0.45f, 0.55f, 15f),
    Destello(0.22f, 0.74f, 8.1f, 1f, 0.85f, 20f),
    Destello(0.60f, 0.86f, 10.8f, 0.45f, 0.5f, 12f),
    Destello(0.44f, 0.10f, 13.5f, 0.65f, 0.75f, 14f),
    Destello(0.33f, 0.44f, 1.3f, 0.45f, 0.45f, 16f),
    Destello(0.69f, 0.38f, 4.1f, 0.65f, 0.65f, 11f),
    Destello(0.08f, 0.56f, 6.8f, 0.45f, 0.5f, 12f),
    Destello(0.92f, 0.30f, 9.5f, 0.45f, 0.6f, 17f),
    Destello(0.52f, 0.66f, 12.2f, 0.65f, 0.7f, 14f),
    Destello(0.26f, 0.08f, 16f, 1f, 0.8f, 20f)
)

/**
 * Opacidad y tamaño de un destello en este instante. Copia la animacion
 * "titilar" de la web: aparece rapido, se sostiene un instante y se apaga;
 * el resto del ciclo queda invisible.
 */
private fun titilar(seg: Float, d: Destello): Pair<Float, Float> {
    if (seg < d.retardo) return 0f to 0.6f
    val p = ((seg - d.retardo) % d.periodo) / d.periodo
    return when {
        p < 0.04f -> (p / 0.04f) * d.intensidad to (0.6f + 0.75f * p / 0.04f)
        p < 0.08f -> d.intensidad to 1.35f
        p < 0.15f -> {
            val k = (p - 0.08f) / 0.07f
            d.intensidad * (1 - k) to (1.35f - 0.55f * k)
        }
        else -> 0f to 0.8f
    }
}

@Composable
fun FondoAurora(oscuro: Boolean, modifier: Modifier = Modifier) {
    val c = coloresDe(oscuro)
    val reloj = relojAnimacion()
    val densidad = LocalDensity.current.density

    Canvas(modifier = modifier.fillMaxSize()) {
        val seg = reloj() / 1000f
        val w = size.width
        val h = size.height

        // 1) Cielo de base
        drawRect(
            Brush.radialGradient(
                colorStops = *c.cielo.toTypedArray(),
                center = Offset(w / 2, 0f),
                radius = maxOf(w, h) * 1.1f
            )
        )

        // 2) Bandas: manchas de color que van y vienen lento
        BANDAS.forEachIndexed { i, b ->
            val (color, alfa) = c.bandas[i]
            val fase = (2 * PI * seg / b.periodo).toFloat()
            val cx = w * (b.x + b.ampX * sin(fase))
            val cy = h * (b.y + b.ampY * sin(fase * 0.7f + i))
            val escala = 1f + 0.08f * sin(fase * 1.3f + i)
            val radio = w * b.radio * escala
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = *arrayOf(
                        0f to color.copy(alpha = alfa),
                        0.7f to color.copy(alpha = 0f)
                    ),
                    center = Offset(cx, cy),
                    radius = radio
                ),
                radius = radio,
                center = Offset(cx, cy)
            )
        }

        // 3) Destellos
        DESTELLOS.forEach { d ->
            val (opacidad, escala) = titilar(seg, d)
            if (opacidad <= 0.01f) return@forEach
            val centro = Offset(w * d.x, h * d.y)
            val halo = 9f * d.tam * escala * densidad
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.destello.copy(alpha = opacidad * 0.6f), Color.Transparent),
                    center = centro,
                    radius = halo
                ),
                radius = halo,
                center = centro
            )
            drawCircle(
                color = c.destello.copy(alpha = opacidad),
                radius = 1.6f * d.tam * escala * densidad,
                center = centro
            )
        }
    }
}

// ============================================================================
// La escena completa: fondo + interruptor de tema + tarjeta con el logo
// ============================================================================

@Composable
fun EscenaLogin(
    temaOscuro: Boolean,
    onCambiarTema: (Boolean) -> Unit,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val c = coloresDe(temaOscuro)

    // Entrada suave de la tarjeta, como en la web
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrada.animateTo(1f, tween(700)) }

    Box(modifier = Modifier.fillMaxSize()) {
        FondoAurora(oscuro = temaOscuro)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            val forma = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = entrada.value
                        translationY = (1f - entrada.value) * 14.dp.toPx()
                    }
//                    .shadow(8.dp, forma, clip = false)
                    .background(c.tarjeta, forma)
                    .border(1.dp, c.bordeTarjeta, forma)
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EncabezadoMarca(temaOscuro)
                contenido()
            }
        }

        // Interruptor dia / noche
        IconButton(
            onClick = { onCambiarTema(!temaOscuro) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
        ) {
            Icon(
                imageVector = if (temaOscuro) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (temaOscuro) "Cambiar a modo día" else "Cambiar a modo noche",
                tint = c.icono
            )
        }
    }
}

/** Logo con su resplandor latiendo, "aurora" y el subtitulo. */
@Composable
private fun EncabezadoMarca(oscuro: Boolean) {
    val c = coloresDe(oscuro)
    val reloj = relojAnimacion()

    Box(
        modifier = Modifier
            .padding(bottom = 14.dp)
            .size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        // Resplandor rosa detras del logo (logo-latido de la web, 4.5 s)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fase = (2 * PI * (reloj() / 1000f) / 4.5f).toFloat()
            val k = (1 - kotlin.math.cos(fase)) / 2          // 0 -> 1 -> 0
            val escala = 1f + 0.14f * k
            val radio = size.minDimension * 0.68f * escala
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = *arrayOf(
                        0f to RosaAurora.copy(alpha = 0.34f * (0.85f + 0.15f * k)),
                        0.68f to RosaAurora.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = radio
                ),
                radius = radio
            )
        }
        Image(
            painter = painterResource(id = R.drawable.aurora_logo),
            contentDescription = "Logo de Aurora",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer { translationX = -3.dp.toPx() }
        )
    }

    Text("aurora", style = Marca.copy(fontSize = 26.sp), color = c.texto)
    Text(
        text = "Sistema de detección de somnolencia en conductores",
        style = TextStyle(fontSize = 13.sp),
        color = c.textoSuave,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
    )
}

// ============================================================================
// Piezas del formulario, con el estilo de la tarjeta de la web
// ============================================================================

@Composable
fun CampoLogin(
    oscuro: Boolean,
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    habilitado: Boolean = true,
    placeholder: String? = null,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    transformacion: VisualTransformation = VisualTransformation.None,
    iconoFinal: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = coloresDe(oscuro)
    val foco = remember { MutableInteractionSource() }
    val enfocado by foco.collectIsFocusedAsState()
    val forma = RoundedCornerShape(9.dp)

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            etiqueta,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = c.etiqueta,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = valor,
            onValueChange = onCambio,
            singleLine = true,
            enabled = habilitado,
            placeholder = if (placeholder != null) {
                { Text(placeholder, color = c.textoSuave) }
            } else null,
            keyboardOptions = teclado,
            visualTransformation = transformacion,
            trailingIcon = iconoFinal,
            interactionSource = foco,
            shape = forma,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (oscuro) c.campoFondo else Color.White,
                unfocusedContainerColor = c.campoFondo,
                disabledContainerColor = c.campoFondo,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedTextColor = c.texto,
                unfocusedTextColor = c.texto,
                disabledTextColor = c.textoSuave,
                cursorColor = c.campoFoco
            ),
            textStyle = TextStyle(fontSize = 14.sp, color = c.texto),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                // halo rosa al seleccionar, como el box-shadow de la web
                .then(
                    if (enfocado) Modifier.border(5.dp, c.campoFoco.copy(alpha = 0.18f), forma)
                    else Modifier
                )
                .border(if (enfocado) 2.dp else 1.5.dp, if (enfocado) c.campoFoco else c.campoBorde, forma)
        )
    }
}

@Composable
fun BotonLogin(texto: String, onClick: () -> Unit, habilitado: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RosaAurora,
            contentColor = Color.White,
            disabledContainerColor = RosaAurora.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.85f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(top = 2.dp)
    ) {
        Text(texto, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun EnlaceLogin(texto: String, onClick: () -> Unit, habilitado: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(texto, color = RosaAurora, style = TextStyle(fontSize = 14.sp))
    }
}

enum class TipoMensaje { ERROR, OK }

/** Recuadro de error u ok, con los colores de la web (.login-error / .login-ok). */
@Composable
fun MensajeLogin(texto: String, tipo: TipoMensaje, oscuro: Boolean) {
    val (fondo, borde, letra) = when (tipo) {
        TipoMensaje.ERROR ->
            if (oscuro) Triple(Color(0xFF3A1622), Color(0xFF6E2A3D), Color(0xFFFFB3C2))
            else Triple(Color(0xFFFBEAEE), Color(0xFFEEC2CC), Color(0xFF7D1F35))
        TipoMensaje.OK ->
            if (oscuro) Triple(Color(0xFF12302A), Color(0xFF285A4F), Color(0xFFA9DCCF))
            else Triple(Color(0xFFEAF3F0), Color(0xFFBCD9D1), Color(0xFF1F5A4D))
    }
    val forma = RoundedCornerShape(9.dp)
    Text(
        text = texto,
        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        color = letra,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(fondo, forma)
            .border(1.dp, borde, forma)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/** Explicacion centrada en gris, como .login-explicacion. */
@Composable
fun ExplicacionLogin(texto: String, oscuro: Boolean) {
    Text(
        texto,
        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        color = coloresDe(oscuro).etiqueta,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 14.dp)
    )
}

/** El aviso ambar de la web sobre la demora del mail y el limite de 10 minutos. */
@Composable
fun AvisoMail(oscuro: Boolean) {
    val color = if (oscuro) Color(0xFFF0B25A) else Color(0xFF8A5716)   // --warning-text
    Text(
        text = buildAnnotatedString {
            append("El mail puede tardar unos minutos en llegar y a veces cae en spam. ")
            append("Se puede pedir una contraseña nueva cada ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("10 minutos") }
            append(". Si no te llega, esperá un rato antes de volver a intentar.")
        },
        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

/** Color del icono del ojito, para usarlo desde las pantallas. */
fun colorIconoCampo(oscuro: Boolean): Color = coloresDe(oscuro).textoSuave