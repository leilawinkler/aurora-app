package ar.edu.itba.aurora

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// Paleta AURORA — la misma de la web (style.css, bloque :root)
// ============================================================================
// Sale del logo (rosa y crema), del cielo nocturno del login y de los dos
// niveles de alerta que mide el dispositivo.

// --- Marca ---
val RosaAurora = Color(0xFFF2778D)       // --primary-color
val RosaIntenso = Color(0xFFE05A75)      // --primary-hover
val RosaSuave = Color(0xFFFDEEF1)        // --primary-light
val CremaLogo = Color(0xFFFFD7D2)        // la espiral del logo
val VioletaAurora = Color(0xFF7C5CFF)    // banda violeta del login
val AmbarAurora = Color(0xFFF9A00F)      // banda ambar del login

// --- Estados del aparato ---
val Nivel1 = Color(0xFFE0983A)           // --warning-color: precaucion
val Nivel2 = Color(0xFFD9435F)           // --danger-color, un poco mas vivo para que se lea de noche
val Nivel3 = Nivel2                      // no se usa: el aparato solo manda nivel 1 y 2
val VerdeOk = Color(0xFF2E7D6B)          // --normal-color: sereno

// --- Modo dia: papel calido, tarjetas blancas, hilos finos ---
private val EsquemaClaro = lightColorScheme(
    primary = RosaAurora,
    onPrimary = Color.White,
    primaryContainer = RosaSuave,
    onPrimaryContainer = Color(0xFF7D1F35),
    background = Color(0xFFF8F5F2),          // --bg-main: porcelana calida
    onBackground = Color(0xFF1C1B2E),        // --text-primary
    surface = Color.White,                   // --bg-card
    onSurface = Color(0xFF1C1B2E),
    surfaceVariant = Color.White,            // tarjetas de viajes anteriores
    onSurfaceVariant = Color(0xFF56526A),    // --text-secondary
    surfaceContainerHigh = RosaSuave,        // tarjeta del viaje en curso
    outline = Color(0xFFE8E2DB),             // --border-color
    outlineVariant = Color(0xFFD6CDC2),      // --border-strong
    secondary = RosaIntenso,                 // etiqueta y barrita "en curso"
    onSecondary = Color.White,
    tertiary = Nivel1,                       // "sin subir"
    secondaryContainer = RosaSuave,          // seleccion de la barra inferior
    onSecondaryContainer = Color(0xFFB8324F),
    error = Color(0xFFB8324F)                // --danger-color
)

// --- Modo noche: el cielo del login, sin encandilar ---
private val EsquemaOscuro = darkColorScheme(
    primary = RosaAurora,
    onPrimary = Color(0xFF2A0C16),
    primaryContainer = Color(0xFF3A1A2A),
    onPrimaryContainer = CremaLogo,
    background = Color(0xFF0E1126),          // --noche
    onBackground = Color(0xFFF2EEF4),
    surface = Color(0xFF161A34),
    onSurface = Color(0xFFF2EEF4),
    surfaceVariant = Color(0xFF161A34),
    onSurfaceVariant = Color(0xFFA7A1BC),
    surfaceContainerHigh = Color(0xFF2A1B36),
    outline = Color(0xFF2A2F52),
    outlineVariant = Color(0xFF3A4070),
    secondary = Color(0xFFFF9CAF),
    onSecondary = Color(0xFF2A0C16),
    tertiary = Color(0xFFF0B25A),
    secondaryContainer = Color(0xFF2E2347),
    onSecondaryContainer = CremaLogo,
    error = Color(0xFFFF6F8E)
)

// ---------- Formas: los mismos radios de la web (9 / 12 / 18) ----------

val FormasAurora = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

// ---------- Estilos de texto ----------

val EtiquetaMono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    letterSpacing = 1.sp
)

val DatoMono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp
)

val NumeroGrande = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 40.sp,
    fontWeight = FontWeight.Normal
)

val NumeroMediano = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 26.sp
)

// "aurora" del login: bold y apretado, como el titulo de la web
val Marca = TextStyle(
    fontSize = 30.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.5).sp
)

val MarcaChica = TextStyle(
    fontSize = 19.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.2).sp
)

// ---------- Utilidad ----------

fun colorDeNivel(nivel: Int): Color = if (nivel >= 2) Nivel2 else Nivel1

/**
 * El "rastro de aurora" que la web pone debajo del header: tres manchas de
 * color muy suaves arriba de la pantalla (rosa, violeta y ambar).
 */
fun Modifier.rastroAurora(oscuro: Boolean): Modifier = drawBehind {
    val alto = 320.dp.toPx()
    val manchas = listOf(
        Triple(0.10f, RosaAurora, if (oscuro) 0.26f else 0.20f),
        Triple(0.50f, VioletaAurora, if (oscuro) 0.22f else 0.15f),
        Triple(0.90f, AmbarAurora, if (oscuro) 0.14f else 0.14f)
    )
    manchas.forEach { (x, color, alfa) ->
        drawRect(
            brush = Brush.radialGradient(
                colorStops = *arrayOf(
                    0f to color.copy(alpha = alfa),
                    0.72f to color.copy(alpha = 0f)
                ),
                center = Offset(size.width * x, 0f),
                radius = maxOf(size.width * 0.75f, alto)
            ),
            size = size.copy(height = minOf(size.height, alto * 1.4f))
        )
    }
}

@Composable
fun AuroraTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        shapes = FormasAurora,
        content = content
    )
}
