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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

sealed class Pantalla {
    data object Cargando : Pantalla()
    data object Login : Pantalla()
    data object Viajes : Pantalla()
    data object NuevoViaje : Pantalla()
    data object Configuracion : Pantalla()
    data class Detalle(val viaje: Viaje) : Pantalla()
    // obligatorio = true cuando entro con una contraseña temporal
    data class CambiarPassword(val obligatorio: Boolean = false) : Pantalla()
    data object Recuperar : Pantalla()
}

/** A donde va el chofer despues de entrar: a cambiar la contraseña si es temporal. */
private fun pantallaAlEntrar(p: Perfil): Pantalla =
    if (p.debeCambiarPassword) Pantalla.CambiarPassword(obligatorio = true)
    else Pantalla.Viajes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prende la base de datos local del telefono (Room).
        Local.iniciar(this)
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
    var usuarioEscrito by remember { mutableStateOf("") }
    var enviandoRecuperar by remember { mutableStateOf(false) }
    var errorRecuperar by remember { mutableStateOf<String?>(null) }
    var mensajeRecuperar by remember { mutableStateOf<String?>(null) }
    val contexto = LocalContext.current
    val estadoSync by Sincronizador.estado.collectAsState()
    val pendientes by Sincronizador.pendientes.collectAsState()
    val cambios by Sincronizador.cambios.collectAsState()

    // El viaje abierto y los ya cerrados salen de la misma lista.
    val viajeEnCurso = viajes.firstOrNull { it.enCurso }
    val viajesAnteriores = viajes.filter { !it.enCurso }

    // Al abrir la app: si ya habia sesion guardada, entra derecho.
    LaunchedEffect(Unit) {
        Sesion.esperarInicio()
        val p = runCatching { Sesion.perfil() }.getOrNull()
        perfil = p
        pantalla = if (p != null) pantallaAlEntrar(p) else Pantalla.Login
    }

    // Cerrar sesion: se usa desde Configuracion y desde el cambio obligatorio.
    val cerrarSesion: () -> Unit = {
        alcance.launch {
            Almacen.limpiarLoSubido()
            Sesion.salir()
        }
        Sincronizador.reiniciar()
        perfil = null
        viajes = emptyList()
        empresa = ""
        passwordCambiada = false
        errorPassword = null
        pantalla = Pantalla.Login
    }

    // Muestra lo que hay guardado en el telefono. Es instantaneo y funciona
    // sin internet; la sincronizacion va por separado.
    LaunchedEffect(perfil?.id, recarga, cambios) {
        val p = perfil ?: return@LaunchedEffect
        cargandoViajes = true
        errorViajes = null
        runCatching { Almacen.viajes(p) }
            .onSuccess { viajes = it }
            .onFailure { errorViajes = mensajeDeError(it) }
        cargandoViajes = false
    }

    // Prende el sincronizador automatico: sube lo pendiente al entrar y queda
    // atento a que aparezca conexion.
    LaunchedEffect(perfil?.id) {
        val p = perfil ?: return@LaunchedEffect
        Sincronizador.arrancar(contexto)
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
                onOlvide = {
                    errorLogin = null
                    errorRecuperar = null
                    mensajeRecuperar = null
                    pantalla = Pantalla.Recuperar
                },
                onIngresar = { usuario, clave ->
                    usuarioEscrito = usuario
                    cargandoLogin = true
                    errorLogin = null
                    alcance.launch {
                        Sesion.entrar(usuario, clave)
                            .onSuccess {
                                perfil = it
                                passwordCambiada = false
                                errorPassword = null
                                pantalla = pantallaAlEntrar(it)
                            }
                            .onFailure { errorLogin = mensajeDeError(it) }
                        cargandoLogin = false
                    }
                }
            )
        }

        is Pantalla.Recuperar -> AuroraTheme(oscuro = true) {
            PantallaRecuperar(
                usuarioInicial = usuarioEscrito,
                cargando = enviandoRecuperar,
                mensajeOk = mensajeRecuperar,
                error = errorRecuperar,
                onVolver = { pantalla = Pantalla.Login },
                onEnviar = { usuario, mail ->
                    usuarioEscrito = usuario
                    enviandoRecuperar = true
                    errorRecuperar = null
                    alcance.launch {
                        Sesion.recuperarPassword(usuario, mail)
                            .onSuccess { mensajeRecuperar = it }
                            .onFailure { errorRecuperar = mensajeDeError(it) }
                        enviandoRecuperar = false
                    }
                }
            )
        }

        is Pantalla.CambiarPassword -> AuroraTheme(oscuro = temaOscuro) {
            PantallaCambiarPassword(
                cargando = cambiandoPassword,
                error = errorPassword,
                listo = passwordCambiada,
                obligatorio = actual.obligatorio,
                onVolver = {
                    passwordCambiada = false
                    pantalla = if (actual.obligatorio) Pantalla.Viajes else Pantalla.Configuracion
                },
                onCerrarSesion = cerrarSesion,
                onGuardar = { nueva ->
                    cambiandoPassword = true
                    errorPassword = null
                    alcance.launch {
                        Sesion.cambiarPassword(nueva)
                            .onSuccess {
                                passwordCambiada = true
                                perfil = perfil?.copy(debeCambiarPassword = false)
                            }
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
                estadoSync = estadoSync,
                pendientesSync = pendientes,
                cargando = cargandoViajes,
                error = errorViajes,
                finalizando = finalizando,
                onSimularEvento = { tipo, nivel ->
                    val p = perfil ?: return@PantallaViajes
                    val abierto = viajeEnCurso ?: return@PantallaViajes
                    alcance.launch {
                        Almacen.registrarEvento(p, abierto.id, tipo, nivel)
                        Sincronizador.huboNovedades()
                    }
                },
                onFinalizarViaje = {
                    val abierto = viajeEnCurso ?: return@PantallaViajes
                    finalizando = true
                    alcance.launch {
                        Almacen.finalizarViaje(abierto.id)
                            .onFailure { errorViajes = mensajeDeError(it) }
                        finalizando = false
                        // Al terminar el viaje se intenta subir todo.
                        Sincronizador.huboNovedades()
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
                        Almacen.iniciarViaje(p, origen, destino)
                            .onSuccess {
                                pantalla = Pantalla.Viajes
                                // Si hay senal, el viaje aparece enseguida en
                                // la web del admin; si no, queda pendiente.
                                Sincronizador.huboNovedades()
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
                estadoSync = estadoSync,
                pendientes = pendientes,
                nombreChofer = perfil?.nombreCompleto ?: "",
                usuario = perfil?.usuario ?: "",
                empresa = empresa,
                onCambiarPassword = {
                    errorPassword = null
                    passwordCambiada = false
                    pantalla = Pantalla.CambiarPassword()
                },
                onCerrarSesion = cerrarSesion
            )
        }
    }
}