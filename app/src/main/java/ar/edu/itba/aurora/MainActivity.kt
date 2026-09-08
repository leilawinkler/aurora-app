package ar.edu.itba.aurora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed class Pantalla {
    data object Login : Pantalla()
    data object Viajes : Pantalla()
    data object NuevoViaje : Pantalla()
    data object Configuracion : Pantalla()
    data class Detalle(val viaje: Viaje) : Pantalla()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppAurora() }
    }
}

@Composable
fun AppAurora() {
    // Estado global de la app (por ahora todo en memoria)
    var temaOscuro by remember { mutableStateOf(true) }
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Login) }
    var viajeEnCurso by remember { mutableStateOf<Viaje?>(DatosDeEjemplo.viajeEnCurso) }

    AuroraTheme(oscuro = temaOscuro) {
        when (val actual = pantalla) {

            is Pantalla.Login -> PantallaLogin(
                onIngresar = { pantalla = Pantalla.Viajes }
            )

            is Pantalla.Viajes -> PantallaViajes(
                viajeEnCurso = viajeEnCurso,
                viajes = DatosDeEjemplo.viajes,
                temaOscuro = temaOscuro,
                onCambiarTema = { temaOscuro = it },
                onNuevoViaje = { pantalla = Pantalla.NuevoViaje },
                onAbrirViaje = { pantalla = Pantalla.Detalle(it) },
                onIrAConfiguracion = { pantalla = Pantalla.Configuracion }
            )

            is Pantalla.NuevoViaje -> PantallaNuevoViaje(
                onVolver = { pantalla = Pantalla.Viajes },
                onIniciar = { origen, destino ->
                    viajeEnCurso = DatosDeEjemplo.viajeEnCurso.copy(
                        origen = origen.ifBlank { "Origen" },
                        destino = destino.ifBlank { "Destino" },
                        duracion = "00:00",
                        eventos = emptyList()
                    )
                    pantalla = Pantalla.Viajes
                }
            )

            is Pantalla.Detalle -> PantallaDetalle(
                viaje = actual.viaje,
                onVolver = { pantalla = Pantalla.Viajes }
            )

            is Pantalla.Configuracion -> PantallaConfiguracion(
                temaOscuro = temaOscuro,
                onCambiarTema = { temaOscuro = it },
                onIrAViajes = { pantalla = Pantalla.Viajes },
                onCerrarSesion = { pantalla = Pantalla.Login }
            )
        }
    }
}