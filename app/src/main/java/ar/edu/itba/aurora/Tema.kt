package ar.edu.itba.aurora

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Colores ----------

// Detalles
val AcentoClaro = Color(0xFFFFBA8E)
val AcentoOscuro = Color(0xFF6E9CFC)

// Botones de los viajes anteriores
val SurfaceAnteriorClaro = Color(0xFFFFBA8E)
val SurfaceAnteriorOscuro = Color(0xFF242A33)

// "sin subir" (ícono + texto)
val SinSubirClaro = Color(0xFFF44336)
val SinSubirOscuro = Color(0xFFD5FF62)

// "en curso" (etiqueta + barra izquierda)
val EnCursoClaro = Color(0xFFF44336)
val EnCursoOscuro = Color(0xFFD5FF62)

// fondo de la tarjeta "en curso", independiente del panel de abajo
val FondoEnCursoClaro = Color(0xFFFFD7BA)
val FondoEnCursoOscuro = Color(0xFF232830)

// selección del bottom nav ("Viajes" / "Config")
val SeleccionNavClaro = Color(0xFFFFD566)
val SeleccionNavOscuro = Color(0xFF393B5D)
val OnSeleccionNavClaro = Color(0xFF15161A)
val OnSeleccionNavOscuro = Color(0xFFFFFFFF)

val Nivel1 = Color(0xFF6BBD46)
val Nivel2 = Color(0xFFFFDE39)
val Nivel3 = Color(0xFFFC49BF)
val VerdeOk = Color(0xFF8B4CC2)

private val EsquemaClaro = lightColorScheme(
    primary = AcentoClaro,
    onPrimary = Color.White,
    background = Color(0xFFFFF7E6),
    onBackground = Color(0xFF15161A),
    surface = Color(0xFFFFE8AF),
    surfaceVariant = SurfaceAnteriorClaro,
    surfaceContainerHigh = FondoEnCursoClaro,
    onSurface = Color(0xFF15161A),
    onSurfaceVariant = Color(0xFF2C2B2B),
    outline = Color(0xFFFFF7E6),
    secondary = EnCursoClaro,
    tertiary = SinSubirClaro,
    secondaryContainer = SeleccionNavClaro,
    onSecondaryContainer = OnSeleccionNavClaro,
    error = Nivel3
)

private val EsquemaOscuro = darkColorScheme(
    primary = AcentoOscuro,
    onPrimary = Color(0xFF04231A),
    background = Color(0xFF1F2125),
    onBackground = Color(0xFFEDEEF0),
    surface = Color(0xFF242A33),
    surfaceVariant = SurfaceAnteriorOscuro,
    surfaceContainerHigh = FondoEnCursoOscuro,
    onSurface = Color(0xFFEDEEF0),
    onSurfaceVariant = Color(0xFF6E9CFC),
    outline = Color(0xFF2C323B),
    secondary = EnCursoOscuro,
    tertiary = SinSubirOscuro,
    secondaryContainer = SeleccionNavOscuro,
    onSecondaryContainer = OnSeleccionNavOscuro,
    error = Nivel3
)

// ---------- Formas: esquinas casi rectas ----------

val FormasAurora = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp)
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

val Marca = TextStyle(
    fontSize = 26.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 6.sp
)

val MarcaChica = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 4.sp
)

// ---------- Utilidad ----------

fun colorDeNivel(nivel: Int): Color = when (nivel) {
    3 -> Nivel3
    2 -> Nivel2
    else -> Nivel1
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