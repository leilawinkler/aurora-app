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

val AcentoClaro = Color(0xFF0F8F6B)
val AcentoOscuro = Color(0xFF2DD4A7)

val Nivel1 = Color(0xFF3B82C4)
val Nivel2 = Color(0xFFE0A02A)
val Nivel3 = Color(0xFFD9534F)
val VerdeOk = Color(0xFF1D9E75)

private val EsquemaClaro = lightColorScheme(
    primary = AcentoClaro,
    onPrimary = Color.White,
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF15161A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15161A),
    onSurfaceVariant = Color(0xFF63666E),
    outline = Color(0xFFD9DAD6),
    error = Nivel3
)

private val EsquemaOscuro = darkColorScheme(
    primary = AcentoOscuro,
    onPrimary = Color(0xFF04231A),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFEDEEF0),
    surface = Color(0xFF171B21),
    onSurface = Color(0xFFEDEEF0),
    onSurfaceVariant = Color(0xFF9BA1AB),
    outline = Color(0xFF2C323B),
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