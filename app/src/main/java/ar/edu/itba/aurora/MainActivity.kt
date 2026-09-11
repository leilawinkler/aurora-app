package ar.edu.itba.aurora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

sealed class Pantalla {
    data object Cargando : Pantalla()
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
    // "alcance" es donde corren las tareas que tardan (hablar con Supabase).
    val alcance = rememberCoroutineScope()

    // Estado global de la app
    var temaOscuro by remember { mutableStateOf(true) }
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Cargando) }
    var perfil by remember { mutableStateOf<Perfil?>(null) }
    var viajeEnCurso by remember { mutableStateOf<Viaje?>(DatosDeEjemplo.viajeEnCurso) }

    // Estado de la pantalla de login
    var cargandoLogin by remember { mutableStateOf(false) }
    var errorLogin by remember { mutableStateOf<String?>(null) }

    // Se ejecuta una sola vez al abrir la app: si ya habia sesion guardada,
    // entra derecho a la pantalla de viajes.
    LaunchedEffect(Unit) {
        Sesion.esperarInicio()
        val p = runCatching { Sesion.perfil() }.getOrNull()
        perfil = p
        pantalla = if (p != null) Pantalla.Viajes else Pantalla.Login
    }

    when (val actual = pantalla) {

        is Pantalla.Cargando -> AuroraTheme(oscuro = true) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        is Pantalla.Login -> AuroraTheme(oscuro = true) {
            PantallaLogin(
                cargando = cargandoLogin,
                error = errorLogin,
                onIngresar = { usuario, clave ->
                    cargandoLogin = true
                    errorLogin = null
                    alcance.launch {
                        Sesion.entrar(usuario, clave)
                            .onSuccess {
                                perfil = it
                                pantalla = Pantalla.Viajes
                            }
                            .onFailure { errorLogin = mensajeDeError(it) }
                        cargandoLogin = false
                    }
                }
            )
        }

        is Pantalla.Viajes -> AuroraTheme(oscuro = temaOscuro) {
            PantallaViajes(
                viajeEnCurso = viajeEnCurso,
                viajes = DatosDeEjemplo.viajes,
                temaOscuro = temaOscuro,
                onCambiarTema = { temaOscuro = it },
                onNuevoViaje = { pantalla = Pantalla.NuevoViaje },
                onAbrirViaje = { pantalla = Pantalla.Detalle(it) },
                onIrAConfiguracion = { pantalla = Pantalla.Configuracion }
            )
        }

        is Pantalla.NuevoViaje -> AuroraTheme(oscuro = temaOscuro) {
            PantallaNuevoViaje(
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
        }

        is Pantalla.Detalle -> AuroraTheme(oscuro = temaOscuro) {
            PantallaDetalle(
                viaje = actual.viaje,
                onVolver = { pantalla = Pantalla.Viajes }
            )
        }

        is Pantalla.Configuracion -> AuroraTheme(oscuro = temaOscuro) {
            PantallaConfiguracion(
                temaOscuro = temaOscuro,
                onCambiarTema = { temaOscuro = it },
                onIrAViajes = { pantalla = Pantalla.Viajes },
                onCerrarSesion = {
                    alcance.launch { Sesion.salir() }
                    perfil = null
                    pantalla = Pantalla.Login
                }
            )
        }
    }
}