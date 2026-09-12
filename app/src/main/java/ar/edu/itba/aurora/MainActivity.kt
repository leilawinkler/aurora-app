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
    data object CambiarPassword : Pantalla()
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

    // --- Estado general ---
    var temaOscuro by remember { mutableStateOf(true) }
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Cargando) }
    var perfil by remember { mutableStateOf<Perfil?>(null) }
    var empresa by remember { mutableStateOf("") }

    // --- Viajes traidos de la base ---
    var viajes by remember { mutableStateOf<List<Viaje>>(emptyList()) }
    var cargandoViajes by remember { mutableStateOf(false) }
    var errorViajes by remember { mutableStateOf<String?>(null) }
    // Cada vez que este numero cambia, se vuelven a pedir los viajes.
    var recarga by remember { mutableStateOf(0) }

    // --- Estado de cada accion ---
    var cargandoLogin by remember { mutableStateOf(false) }
    var errorLogin by remember { mutableStateOf<String?>(null) }
    var guardandoViaje by remember { mutableStateOf(false) }
    var errorNuevoViaje by remember { mutableStateOf<String?>(null) }
    var finalizando by remember { mutableStateOf(false) }
    var cambiandoPassword by remember { mutableStateOf(false) }
    var errorPassword by remember { mutableStateOf<String?>(null) }
    var passwordCambiada by remember { mutableStateOf(false) }

    // El viaje abierto y los ya cerrados salen de la misma lista.
    val viajeEnCurso = viajes.firstOrNull { it.enCurso }
    val viajesAnteriores = viajes.filter { !it.enCurso }

    // Al abrir la app: si ya habia sesion guardada, entra derecho.
    LaunchedEffect(Unit) {
        Sesion.esperarInicio()
        val p = runCatching { Sesion.perfil() }.getOrNull()
        perfil = p
        pantalla = if (p != null) Pantalla.Viajes else Pantalla.Login
    }

    // Trae los viajes cada vez que cambia el chofer logueado o se pide recarga.
    LaunchedEffect(perfil?.id, recarga) {
        val p = perfil ?: return@LaunchedEffect
        cargandoViajes = true
        errorViajes = null
        runCatching { Datos.viajesDelChofer() }
            .onSuccess { viajes = it }
            .onFailure { errorViajes = mensajeDeError(it) }
        cargandoViajes = false
        empresa = Datos.nombreEmpresa(p) ?: ""
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

        is Pantalla.CambiarPassword -> AuroraTheme(oscuro = temaOscuro) {
            PantallaCambiarPassword(
                cargando = cambiandoPassword,
                error = errorPassword,
                listo = passwordCambiada,
                onVolver = {
                    passwordCambiada = false
                    pantalla = Pantalla.Configuracion
                },
                onGuardar = { nueva ->
                    cambiandoPassword = true
                    errorPassword = null
                    alcance.launch {
                        Sesion.cambiarPassword(nueva)
                            .onSuccess { passwordCambiada = true }
                            .onFailure { errorPassword = mensajeDeError(it) }
                        cambiandoPassword = false
                    }
                }
            )
        }

        is Pantalla.Viajes -> AuroraTheme(oscuro = temaOscuro) {
            PantallaViajes(
                viajeEnCurso = viajeEnCurso,
                viajes = viajesAnteriores,
                temaOscuro = temaOscuro,
                onCambiarTema = { temaOscuro = it },
                onNuevoViaje = {
                    errorNuevoViaje = null
                    pantalla = Pantalla.NuevoViaje
                },
                onAbrirViaje = { pantalla = Pantalla.Detalle(it) },
                onIrAConfiguracion = { pantalla = Pantalla.Configuracion },
                nombreChofer = perfil?.nombreCompleto ?: "",
                cargando = cargandoViajes,
                error = errorViajes,
                finalizando = finalizando,
                onFinalizarViaje = {
                    val abierto = viajeEnCurso ?: return@PantallaViajes
                    finalizando = true
                    alcance.launch {
                        Datos.finalizarViaje(abierto.id)
                            .onFailure { errorViajes = mensajeDeError(it) }
                        finalizando = false
                        recarga++
                    }
                }
            )
        }

        is Pantalla.NuevoViaje -> AuroraTheme(oscuro = temaOscuro) {
            PantallaNuevoViaje(
                onVolver = { pantalla = Pantalla.Viajes },
                guardando = guardandoViaje,
                error = errorNuevoViaje,
                onIniciar = { origen, destino ->
                    val p = perfil ?: return@PantallaNuevoViaje
                    guardandoViaje = true
                    errorNuevoViaje = null
                    alcance.launch {
                        Datos.iniciarViaje(p, origen, destino)
                            .onSuccess {
                                recarga++
                                pantalla = Pantalla.Viajes
                            }
                            .onFailure { errorNuevoViaje = mensajeDeError(it) }
                        guardandoViaje = false
                    }
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
                nombreChofer = perfil?.nombreCompleto ?: "",
                usuario = perfil?.usuario ?: "",
                empresa = empresa,
                onCambiarPassword = {
                    errorPassword = null
                    passwordCambiada = false
                    pantalla = Pantalla.CambiarPassword
                },
                onCerrarSesion = {
                    alcance.launch { Sesion.salir() }
                    perfil = null
                    viajes = emptyList()
                    empresa = ""
                    pantalla = Pantalla.Login
                }
            )
        }
    }
}