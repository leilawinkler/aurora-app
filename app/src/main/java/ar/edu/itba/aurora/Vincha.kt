package ar.edu.itba.aurora

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Un parpadeo que detecto la vincha y llego por Bluetooth. */
data class Parpadeo(
    val numero: Long,        // contador de la vincha (vuelve a 1 si se reinicia)
    val duracionMs: Int,     // duracion al 50 % de la amplitud
    val amplitudMv: Int,     // altura del pico, en mV a la salida del circuito
    val lento: Boolean,      // la vincha lo marco como lento
    val recibidoEnMs: Long   // hora del telefono al recibirlo
)

enum class EstadoVincha { DESCONECTADA, BUSCANDO, CONECTANDO, CONECTADA }

/**
 * La conexion Bluetooth (BLE) con la vincha. Es un objeto unico para toda la
 * app: la conexion sigue viva aunque se cambie de pantalla.
 *
 * Como funciona:
 *  1. "vincular" (desde Configuracion) busca un dispositivo que se anuncie
 *     como AURORA (o con el servicio de AURORA) y se conecta.
 *  2. Activa las notificaciones de la caracteristica de eventos. Desde ahi,
 *     la vincha manda un mensaje por cada parpadeo: "P,12,180,640,N"
 *     (numero, duracion en ms, amplitud en mV, N normal / L lento).
 *  3. Cada mensaje sale por [parpadeos]. Quien lo escucha (MainActivity)
 *     decide que hacer: contarlo en pantalla y, si es lento, guardarlo.
 *  4. Queda VINCULADA (guardado en el telefono) hasta que el chofer toque
 *     "Desvincular" o cierre sesion. Mientras tanto se reconecta sola: si se
 *     corta, al abrir la app y cuando se vuelve a prender el Bluetooth.
 *
 * Los UUID y el formato del mensaje tienen que coincidir con el firmware
 * (AURORA_parpadeos.ino).
 */
// Guarda solo el applicationContext (vive lo mismo que la app): no hay fuga de memoria.
@SuppressLint("StaticFieldLeak")
object Vincha {

    const val NOMBRE = "AURORA"
    private val UUID_SERVICIO: UUID = UUID.fromString("6e7a0001-4a55-4b8e-9f2a-a0b0c0d0e0f0")
    private val UUID_EVENTOS: UUID = UUID.fromString("6e7a0002-4a55-4b8e-9f2a-a0b0c0d0e0f0")
    // Descriptor estandar de Bluetooth para prender las notificaciones
    private val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private const val DURACION_BUSQUEDA_MS = 15_000L
    private const val PAUSA_ENTRE_BUSQUEDAS_MS = 4_000L

    private val _estado = MutableStateFlow(EstadoVincha.DESCONECTADA)
    val estado: StateFlow<EstadoVincha> = _estado.asStateFlow()

    // Mensaje para el chofer cuando algo no anda (null = todo bien)
    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    // true = el chofer la vinculo y no la desvinculo (aunque ahora este desconectada)
    private val _vinculada = MutableStateFlow(false)
    val vinculada: StateFlow<Boolean> = _vinculada.asStateFlow()

    // Si el Bluetooth del telefono esta prendido (para el renglon de estado)
    private val _bluetoothActivo = MutableStateFlow(true)
    val bluetoothActivo: StateFlow<Boolean> = _bluetoothActivo.asStateFlow()

    private val _parpadeos = MutableSharedFlow<Parpadeo>(extraBufferCapacity = 64)
    val parpadeos: SharedFlow<Parpadeo> = _parpadeos.asSharedFlow()

    // Siempre se guarda el applicationContext (el de la app entera, que vive
    // lo mismo que este objeto), nunca el de una pantalla: no hay fuga de memoria.
    @SuppressLint("StaticFieldLeak")
    private var contexto: Context? = null
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var buscando = false
    // Igual que _vinculada, para leerlo rapido desde los callbacks de Bluetooth
    @Volatile private var quiereConectado = false
    @Volatile private var ultimoNumero = -1L
    private val hilo = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------
    // Chequeos previos (los usa la tarjeta de la vincha antes de vincular)
    // ------------------------------------------------------------------

    /** Permisos que hay que pedirle al chofer segun la version de Android. */
    fun permisosNecesarios(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
        // Hasta Android 11, buscar dispositivos Bluetooth requiere el permiso de ubicacion
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun tienePermisos(ctx: Context): Boolean = permisosNecesarios().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun adaptador(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hayBluetooth(ctx: Context): Boolean = adaptador(ctx) != null

    fun bluetoothPrendido(ctx: Context): Boolean = adaptador(ctx)?.isEnabled == true

    /** Hasta Android 11, ademas del permiso, la Ubicacion tiene que estar prendida. */
    fun faltaUbicacion(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return !LocationManagerCompat.isLocationEnabled(lm)
    }

    fun avisar(texto: String?) {
        _aviso.value = texto
    }

    // ------------------------------------------------------------------
    // Arranque, vincular y desvincular
    // ------------------------------------------------------------------

    /**
     * Se llama una vez al abrir la app (MainActivity). Si la vincha habia
     * quedado vinculada, intenta reconectarse sola.
     */
    fun iniciar(ctx: Context) {
        if (contexto != null) return
        contexto = ctx.applicationContext
        _bluetoothActivo.value = bluetoothPrendido(ctx)
        // Avisa cuando el chofer prende o apaga el Bluetooth
        ContextCompat.registerReceiver(
            ctx.applicationContext,
            receptorBluetooth,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        if (Preferencias.vinchaVinculada) {
            marcarVinculada(true)
            hilo.post { empezarBusqueda() }
        }
    }

    /** Llamar solo despues de chequear permisos y Bluetooth prendido. */
    fun vincular(ctx: Context) {
        if (contexto == null) contexto = ctx.applicationContext
        marcarVinculada(true)
        _aviso.value = null
        hilo.post { empezarBusqueda() }
    }

    private fun marcarVinculada(valor: Boolean) {
        quiereConectado = valor
        _vinculada.value = valor
        Preferencias.vinchaVinculada = valor
    }

    @SuppressLint("MissingPermission")
    fun desvincular() {
        marcarVinculada(false)
        hilo.removeCallbacksAndMessages(null)
        detenerBusqueda()
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        _estado.value = EstadoVincha.DESCONECTADA
        _aviso.value = null
    }

    // ------------------------------------------------------------------
    // Busqueda
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun empezarBusqueda() {
        if (!quiereConectado || buscando || gatt != null) return
        val ctx = contexto ?: return

        // En estos casos sigue vinculada, pero no puede buscar: avisa y espera.
        if (!tienePermisos(ctx)) {
            _estado.value = EstadoVincha.DESCONECTADA
            _aviso.value = "Falta el permiso de Bluetooth. Tocá Reconectar."
            return
        }
        val escaner = adaptador(ctx)?.bluetoothLeScanner
        if (escaner == null || !bluetoothPrendido(ctx)) {
            // Cuando lo prendan, receptorBluetooth retoma la busqueda solo
            _estado.value = EstadoVincha.DESCONECTADA
            _aviso.value = "El Bluetooth del teléfono está apagado. Prendelo para reconectar la vincha."
            return
        }
        if (faltaUbicacion(ctx)) {
            _estado.value = EstadoVincha.DESCONECTADA
            _aviso.value = "Prendé la Ubicación del teléfono y tocá Reconectar " +
                    "(Android la pide para buscar dispositivos Bluetooth)."
            return
        }

        _estado.value = EstadoVincha.BUSCANDO
        buscando = true
        val ajustes = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { escaner.startScan(null, ajustes, callbackBusqueda) }
            .onFailure {
                buscando = false
                _aviso.value = "No se pudo buscar la vincha: ${it.message}"
                reintentarBusqueda()
                return
            }
        hilo.postDelayed(finDeBusqueda, DURACION_BUSQUEDA_MS)
    }

    @SuppressLint("MissingPermission")
    private fun detenerBusqueda() {
        hilo.removeCallbacks(finDeBusqueda)
        if (!buscando) return
        buscando = false
        val ctx = contexto ?: return
        runCatching { adaptador(ctx)?.bluetoothLeScanner?.stopScan(callbackBusqueda) }
    }

    // Si en 15 s no aparecio, avisa y vuelve a intentar (Android limita
    // cuantas busquedas seguidas se pueden hacer, por eso la pausa).
    private val finDeBusqueda = Runnable {
        if (buscando) {
            detenerBusqueda()
            _aviso.value = "No encuentro la vincha. ¿Está prendida y cerca? Sigo buscando..."
            reintentarBusqueda()
        }
    }

    private fun reintentarBusqueda() {
        if (!quiereConectado) return
        hilo.postDelayed({ empezarBusqueda() }, PAUSA_ENTRE_BUSQUEDAS_MS)
    }

    private val callbackBusqueda = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val registro = result.scanRecord
            val esAurora = registro?.serviceUuids?.any { it.uuid == UUID_SERVICIO } == true ||
                    registro?.deviceName == NOMBRE
            if (esAurora && buscando) {
                detenerBusqueda()
                conectarA(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            buscando = false
            hilo.removeCallbacks(finDeBusqueda)
            _aviso.value = "No se pudo buscar la vincha (error $errorCode). Reintentando..."
            reintentarBusqueda()
        }
    }

    // Bluetooth prendido -> retoma la busqueda. Apagado -> limpia la conexion.
    private val receptorBluetooth = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    _bluetoothActivo.value = true
                    if (quiereConectado) {
                        _aviso.value = null
                        hilo.postDelayed({ empezarBusqueda() }, 1_000L)
                    }
                }
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    _bluetoothActivo.value = false
                    hilo.removeCallbacksAndMessages(null)
                    detenerBusqueda()
                    gatt?.let { runCatching { it.close() } }
                    gatt = null
                    _estado.value = EstadoVincha.DESCONECTADA
                    if (quiereConectado)
                        _aviso.value = "El Bluetooth del teléfono está apagado. Prendelo para reconectar la vincha."
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Conexion
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun conectarA(dispositivo: BluetoothDevice) {
        val ctx = contexto ?: return
        _estado.value = EstadoVincha.CONECTANDO
        ultimoNumero = -1L
        // Android 17 marca esta forma como vieja, pero es la que anda desde Android 7
        @Suppress("DEPRECATION")
        gatt = dispositivo.connectGatt(ctx, false, callbackGatt, BluetoothDevice.TRANSPORT_LE)
    }

    /** Se cayo la conexion (o no se pudo hacer): limpia y, si corresponde, reintenta. */
    @SuppressLint("MissingPermission")
    private fun alPerderConexion(g: BluetoothGatt) {
        runCatching { g.close() }
        if (gatt == g) gatt = null
        _estado.value = EstadoVincha.DESCONECTADA
        if (quiereConectado) {
            _aviso.value = "Se perdió la conexión con la vincha. Reconectando..."
            hilo.postDelayed({ empezarBusqueda() }, 2_000L)
        }
    }

    private val callbackGatt = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, nuevoEstado: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && nuevoEstado == BluetoothProfile.STATE_CONNECTED) {
                // Pequeña espera: algunos telefonos fallan si se pide enseguida
                hilo.postDelayed({ runCatching { g.discoverServices() } }, 300L)
            } else if (nuevoEstado == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                alPerderConexion(g)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val caracteristica = g.getService(UUID_SERVICIO)?.getCharacteristic(UUID_EVENTOS)
            val descriptor = caracteristica?.getDescriptor(UUID_CCCD)
            if (status != BluetoothGatt.GATT_SUCCESS || caracteristica == null || descriptor == null) {
                _aviso.value = "El dispositivo no tiene el servicio de AURORA. ¿Tiene cargado el firmware nuevo?"
                runCatching { g.disconnect() }
                return
            }
            g.setCharacteristicNotification(caracteristica, true)
            val valor = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, valor)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = valor
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (d.uuid != UUID_CCCD) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _estado.value = EstadoVincha.CONECTADA
                _aviso.value = null
            } else {
                _aviso.value = "No se pudieron activar las notificaciones (error $status)."
                runCatching { g.disconnect() }
            }
        }

        // Android 13 en adelante
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            valor: ByteArray
        ) {
            if (c.uuid == UUID_EVENTOS) recibir(valor)
        }

        // Android 12 y anteriores
        @Deprecated("Solo para Android 12 y anteriores")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (c.uuid == UUID_EVENTOS) c.value?.let { recibir(it) }
        }
    }

    // ------------------------------------------------------------------
    // Mensajes de la vincha
    // ------------------------------------------------------------------

    /** "P,12,180,640,N" -> Parpadeo. Lo que no tenga ese formato se ignora. */
    internal fun interpretar(bytes: ByteArray, ahoraMs: Long): Parpadeo? {
        val partes = String(bytes, Charsets.UTF_8).trim().split(",")
        if (partes.size < 5 || partes[0] != "P") return null
        val numero = partes[1].toLongOrNull() ?: return null
        val duracion = partes[2].toIntOrNull() ?: return null
        val amplitud = partes[3].toIntOrNull() ?: return null
        return Parpadeo(numero, duracion, amplitud, partes[4] == "L", ahoraMs)
    }

    private fun recibir(bytes: ByteArray) {
        val p = interpretar(bytes, System.currentTimeMillis()) ?: return
        if (p.numero == ultimoNumero) return   // el mismo mensaje dos veces
        ultimoNumero = p.numero
        _parpadeos.tryEmit(p)
    }
}