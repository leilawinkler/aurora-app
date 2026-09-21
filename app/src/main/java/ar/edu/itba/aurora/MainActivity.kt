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

/**
 * A donde va el chofer despues de entrar: a cambiar la contraseña si es
 * temporal. Salvo sin señal: ahi no se puede cambiar, y trabarlo en esa
 * pantalla le impediria iniciar el viaje. Se la pide la proxima vez que
 * entre con señal.
 */
private fun pantallaAlEntrar(p: Perfil, sinConexion: Boolean = false): Pantalla =
    if (p.debeCambiarPassword && !sinConexion) Pantalla.CambiarPassword(obligatorio = true)
    else Pantalla.Viajes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prende la base de datos local del telefono (Room).
        Local.iniciar(this)
        // Y lo que quedo guardado: modo dia/noche, quien esta adentro, y las
        // huellas para entrar sin señal.
        Preferencias.iniciar(this)
        setContent { AppAurora() }
    }
}

@Composable
fun AppAurora() {
    // "alcance" es donde corren las tareas que tardan (hablar con Supabase).
    val alcance = rememberCoroutineScope()

    // --- Estado general ---
    // Arranca con lo ultimo que eligio el usuario, no con un valor fijo.
    var temaOscuro by remember { mutableStateOf(Preferencias.temaOscuro) }

    // Cambiar el tema siempre pasa por aca, asi queda guardado en el telefono.
    val cambiarTema: (Boolean) -> Unit = {
        temaOscuro = it
        Preferencias.temaOscuro = it
    }
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
    val necesitaClave by Sincronizador.necesitaClave.collectAsState()
    var historialCompleto by remember { mutableStateOf(false) }
    var mostrarDialogoClave by remember { mutableStateOf(false) }
    var enviandoClave by remember { mutableStateOf(false) }
    var errorClave by remember { mutableStateOf<String?>(null) }

    // El viaje abierto y los ya cerrados salen de la misma lista.
    val viajeEnCurso = viajes.firstOrNull { it.enCurso }
    val viajesAnteriores = viajes.filter { !it.enCurso }

    // Al abrir la app: si el chofer no cerro sesion, entra directo, haya
    // señal o no. La app nunca lo saca por falta de señal (ver Sesion.alAbrir).
    LaunchedEffect(Unit) {
        when (val arranque = Sesion.alAbrir()) {
            is Arranque.Adentro -> {
                perfil = arranque.perfil
                pantalla = pantallaAlEntrar(arranque.perfil, arranque.sinConexion)
            }
            is Arranque.Afuera -> {
                errorLogin = arranque.motivo
                pantalla = Pantalla.Login
            }
        }
    }

    // Cerrar sesion: SOLO cuando el chofer toca el boton (Configuracion o el
    // cambio obligatorio de contraseña). La app nunca lo hace sola. Lo que
    // no se subio queda en el telefono y se sube la proxima vez que entre.
    val cerrarSesion: () -> Unit = {
        alcance.launch {
            Almacen.alCerrarSesion()
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

    // Baja de la cuenta: el administrador dio de baja al chofer, o el
    // superadmin al administrador de su empresa. Es el unico caso en que la
    // app saca al chofer sin que el lo pida. Se detecta apenas hay señal
    // (ver Sincronizador.cuentaBloqueada), incluso en medio de un viaje.
    val cuentaBloqueada by Sincronizador.cuentaBloqueada.collectAsState()
    LaunchedEffect(cuentaBloqueada) {
        val motivo = cuentaBloqueada ?: return@LaunchedEffect
        Almacen.alCerrarSesion()
        // Ademas de cerrar sesion, borra la huella para entrar sin señal.
        Sesion.expulsar()
        Sincronizador.reiniciar()
        perfil = null
        viajes = emptyList()
        empresa = ""
        passwordCambiada = false
        errorPassword = null
        mostrarDialogoClave = false
        errorLogin = motivo
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
        historialCompleto = Almacen.historialCompleto
        cargandoViajes = false
    }

    // Prende el sincronizador automatico: sube lo pendiente al entrar y queda
    // atento a que aparezca conexion.
    LaunchedEffect(perfil?.id) {
        val p = perfil ?: return@LaunchedEffect
        Sincronizador.arrancar(contexto)
        empresa = Preferencias.empresaGuardada ?: ""
        val deLaNube = Datos.nombreEmpresa(p)
        if (deLaNube != null) {
            empresa = deLaNube
            Preferencias.empresaGuardada = deLaNube
        }
    }

    when (val actual = pantalla) {

        is Pantalla.Cargando -> AuroraTheme(oscuro = temaOscuro) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FondoAurora(oscuro = temaOscuro)
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is Pantalla.Login -> AuroraTheme(oscuro = temaOscuro) {
            PantallaLogin(
                temaOscuro = temaOscuro,
                onCambiarTema = cambiarTema,
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
                            .onSuccess { ingreso ->
                                perfil = ingreso.perfil
                                passwordCambiada = false
                                errorPassword = null
                                pantalla = pantallaAlEntrar(ingreso.perfil, ingreso.sinConexion)
                            }
                            .onFailure { errorLogin = mensajeDeError(it) }
                        cargandoLogin = false
                    }
                }
            )
        }

        is Pantalla.Recuperar -> AuroraTheme(oscuro = temaOscuro) {
            PantallaRecuperar(
                temaOscuro = temaOscuro,
                onCambiarTema = cambiarTema,
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
                onCambiarTema = cambiarTema,
                onNuevoViaje = {
                    errorNuevoViaje = null
                    pantalla = Pantalla.NuevoViaje
                },
                onAbrirViaje = { pantalla = Pantalla.Detalle(it) },
                onIrAConfiguracion = { pantalla = Pantalla.Configuracion },
                nombreChofer = perfil?.nombreCompleto ?: "",
                estadoSync = estadoSync,
                pendientesSync = pendientes,
                historialCompleto = historialCompleto,
                necesitaClave = necesitaClave,
                onIngresarClave = {
                    errorClave = null
                    mostrarDialogoClave = true
                },
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
            if (mostrarDialogoClave) {
                DialogoClave(
                    enviando = enviandoClave,
                    error = errorClave,
                    onCancelar = { mostrarDialogoClave = false },
                    onConfirmar = { clave ->
                        enviandoClave = true
                        errorClave = null
                        alcance.launch {
                            Sesion.reingresarClave(clave)
                                .onSuccess {
                                    mostrarDialogoClave = false
                                    Sincronizador.sincronizar()
                                }
                                .onFailure { errorClave = mensajeDeError(it) }
                            enviandoClave = false
                        }
                    }
                )
            }
        }

        is Pantalla.NuevoViaje -> AuroraTheme(oscuro = temaOscuro) {
            PantallaNuevoViaje(
                onVolver = { pantalla = Pantalla.Viajes },
                guardando = guardandoViaje,
                error = errorNuevoViaje,
                onIniciar = { origen, destino ->
                    val p = perfil
                    if (p == null) {
                        errorNuevoViaje = "No se pudo identificar al chofer. Cerrá y abrí la app."
                        return@PantallaNuevoViaje
                    }
                    guardandoViaje = true
                    errorNuevoViaje = null
                    alcance.launch {
                        // Solo escribe en el telefono: no necesita señal.
                        try {
                            Almacen.iniciarViaje(p, origen, destino)
                                .onSuccess {
                                    pantalla = Pantalla.Viajes
                                    // Si hay señal, el viaje aparece enseguida en
                                    // la web del admin; si no, queda pendiente.
                                    Sincronizador.huboNovedades()
                                }
                                .onFailure { errorNuevoViaje = mensajeDeError(it) }
                        } finally {
                            // Pase lo que pase, el boton vuelve a estar disponible.
                            guardandoViaje = false
                        }
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
                onCambiarTema = cambiarTema,
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