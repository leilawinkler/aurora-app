package ar.edu.itba.aurora

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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

    // Se prende cuando HAY internet pero no hay forma de abrir la sesion con
    // Supabase sin la contraseña (por ejemplo, entro sin señal y despues
    // Android cerro la app). La pantalla muestra un aviso para que la
    // escriba. Nunca se cierra la sesion por esto.
    private val _necesitaClave = MutableStateFlow(false)
    val necesitaClave: StateFlow<Boolean> = _necesitaClave

    private var vigilando = false
    // Se llena con el motivo si el servidor avisa que la cuenta ya no esta
    // habilitada (el admin dio de baja al chofer, o el superadmin al admin
    // de su empresa). La pantalla lo ve y manda al chofer al login.
    private val _cuentaBloqueada = MutableStateFlow<String?>(null)
    val cuentaBloqueada: StateFlow<String?> = _cuentaBloqueada

    private val trabajando = AtomicBoolean(false)
    private var contextoApp: Context? = null

    /** Se llama una vez, cuando el chofer ya esta adentro. */
    fun arrancar(contexto: Context) {
        contextoApp = contexto.applicationContext
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
                if (_pendientes.value > 0) sincronizar() else revisarCuenta()
            }
        }
    }

    /** Dispara una sincronizacion. Si ya hay una corriendo, no hace nada. */
    fun sincronizar() {
        alcance.launch {
            // compareAndSet: marca "trabajando" y verifica que no lo estuviera,
            // en un solo paso, para que no arranquen dos a la vez.
            if (!trabajando.compareAndSet(false, true)) return@launch
            try {
                sincronizarAhora()
            } finally {
                trabajando.set(false)
            }
        }
    }

    private suspend fun sincronizarAhora() {
        // Si nadie esta adentro de la app (por ejemplo, justo cerro sesion),
        // no hay nada que hacer.
        if (Preferencias.perfilGuardado == null) return

        _estado.value = EstadoSync.SINCRONIZANDO

        // Sin sesion con Supabase no se puede subir ni bajar nada. No se
        // saca al chofer de la app: lo pendiente espera en el telefono.
        if (!Sesion.reconectar()) {
            _necesitaClave.value = Sesion.necesitaClave && hayInternetConfirmado()
            _pendientes.value = runCatching { Almacen.pendientes() }.getOrDefault(0)
            _estado.value = EstadoSync.SIN_CONEXION
            _cambios.value = _cambios.value + 1
            return
        }
        _necesitaClave.value = false

        // Con sesion y señal: antes de subir, confirmar que la cuenta sigue
        // habilitada. Si la dieron de baja, no se sube nada.
        val motivo = Sesion.motivoDeBloqueo()
        if (motivo != null) {
            _cuentaBloqueada.value = motivo
            return
        }

        val resultado = Almacen.sincronizar()
        _pendientes.value = runCatching { Almacen.pendientes() }.getOrDefault(0)

        _estado.value = if (resultado.isSuccess) {
            EstadoSync.SINCRONIZADO
        } else {
            // La razon realista para que falle es quedarse sin señal.
            EstadoSync.SIN_CONEXION
        }

        _cambios.value = _cambios.value + 1
    }

    /**
     * Revisa solo si la cuenta sigue habilitada: una consulta chiquita al
     * servidor, sin subir ni bajar viajes. Si no hay señal no hace nada: sin
     * señal no se puede saber, y nunca se saca a nadie por falta de señal.
     */
    private fun revisarCuenta() {
        alcance.launch {
            if (Preferencias.perfilGuardado == null) return@launch
            if (!Sesion.reconectar()) return@launch
            val motivo = Sesion.motivoDeBloqueo()
            if (motivo != null) _cuentaBloqueada.value = motivo
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
        _necesitaClave.value = false
        _cuentaBloqueada.value = null
    }

    /**
     * Android marca una red como "validada" solo cuando comprobo que llega a
     * internet. Sirve para no confundir "conectado al hotspot de un celular
     * sin datos" con "hay internet de verdad".
     */
    private fun hayInternetConfirmado(): Boolean {
        val gestor = contextoApp?.getSystemService(ConnectivityManager::class.java) ?: return false
        return runCatching {
            val red = gestor.activeNetwork ?: return false
            val capacidades = gestor.getNetworkCapabilities(red) ?: return false
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
    }
}