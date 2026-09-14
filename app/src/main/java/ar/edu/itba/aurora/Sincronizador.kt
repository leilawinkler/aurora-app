package ar.edu.itba.aurora

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Lo que muestra el cartel de la pantalla principal. */
enum class EstadoSync {
    SINCRONIZADO,
    SINCRONIZANDO,
    SIN_CONEXION
}

/**
 * Se encarga de subir lo pendiente sin que el chofer tenga que hacer nada.
 *
 * Sincroniza en cuatro situaciones:
 *   - al abrir la app
 *   - cuando el sistema avisa que aparecio una red (salio del tunel, llego
 *     a la terminal, prendieron el hotspot)
 *   - cada minuto, mientras quede algo sin subir
 *   - cuando la app avisa que se guardo algo nuevo (viaje o evento)
 */
object Sincronizador {

    private val _estado = MutableStateFlow(EstadoSync.SINCRONIZADO)
    val estado: StateFlow<EstadoSync> = _estado

    private val _pendientes = MutableStateFlow(0)
    val pendientes: StateFlow<Int> = _pendientes

    // Sube de a uno cada vez que algo cambia. Las pantallas lo miran para
    // saber que tienen que volver a leer la lista de viajes.
    private val _cambios = MutableStateFlow(0)
    val cambios: StateFlow<Int> = _cambios

    // Tareas de fondo: no corren en la pantalla, asi no la trancan.
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var vigilando = false
    private var trabajando = false

    /** Se llama una vez, cuando el chofer ya esta logueado. */
    fun arrancar(contexto: Context) {
        sincronizar()
        if (vigilando) return
        vigilando = true

        // 1) Aviso del sistema cuando aparece o se cae la conexion.
        // Si el telefono no deja registrar el aviso, no pasa nada: queda el
        // reintento por tiempo de mas abajo.
        runCatching {
            val gestor = contexto.applicationContext
                .getSystemService(ConnectivityManager::class.java)
            gestor?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        sincronizar()
                    }

                    override fun onLost(network: Network) {
                        if (_pendientes.value > 0) {
                            _estado.value = EstadoSync.SIN_CONEXION
                        }
                    }
                }
            )
        }

        // 2) Red de seguridad: reintento cada minuto mientras falte subir algo.
        alcance.launch {
            while (true) {
                delay(60_000)
                if (_pendientes.value > 0) sincronizar()
            }
        }
    }

    /** Dispara una sincronizacion. Si ya hay una corriendo, no hace nada. */
    fun sincronizar() {
        alcance.launch {
            if (trabajando) return@launch
            trabajando = true
            _estado.value = EstadoSync.SINCRONIZANDO

            val resultado = Almacen.sincronizar()
            _pendientes.value = runCatching { Almacen.pendientes() }.getOrDefault(0)

            _estado.value = if (resultado.isSuccess) {
                EstadoSync.SINCRONIZADO
            } else {
                // La unica razon realista para que falle es quedarse sin senal.
                EstadoSync.SIN_CONEXION
            }

            _cambios.value = _cambios.value + 1
            trabajando = false
        }
    }

    /**
     * La app avisa que guardo algo nuevo en el telefono (un viaje, un evento).
     * Refresca el contador y trata de subirlo enseguida.
     */
    fun huboNovedades() {
        alcance.launch {
            _pendientes.value = runCatching { Almacen.pendientes() }.getOrDefault(0)
            _cambios.value = _cambios.value + 1
        }
        sincronizar()
    }

    /** Al cerrar sesion se vuelve al estado inicial. */
    fun reiniciar() {
        _estado.value = EstadoSync.SINCRONIZADO
        _pendientes.value = 0
    }
}