package ar.edu.itba.aurora

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Lo que queda guardado para poder entrar sin señal. NO guarda la
 * contraseña: guarda una "huella" de ella (ver ClaveSinSenal.kt), que sirve
 * para comprobar si la que escribe el chofer es correcta, pero a partir de
 * la cual no se puede reconstruir la contraseña.
 */
@Serializable
data class CredencialSinSenal(
    val usuario: String,
    val perfil: Perfil,
    val sal: String,                  // en base64
    val huella: String,               // en base64
    val iteraciones: Int,
    val ultimoIngresoConSenalMs: Long,
    val fallos: Int = 0,
    val bloqueadoHastaMs: Long = 0L
)

/**
 * Datos chicos que quedan guardados en el telefono aunque se cierre la app,
 * se apague el celular o no haya señal. Es un archivo de Android
 * (SharedPreferences), aparte de la base de Room.
 */
object Preferencias {

    private const val ARCHIVO = "aurora_preferencias"
    private const val TEMA_OSCURO = "tema_oscuro"
    private const val PERFIL = "perfil_chofer"
    private const val EMPRESA = "nombre_empresa"
    private const val CREDENCIALES = "credenciales_sin_senal"

    private var prefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun iniciar(contexto: Context) {
        if (prefs == null) {
            prefs = contexto.applicationContext
                .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        }
    }

    /** Modo noche. Si nunca eligio nada, arranca en oscuro. */
    var temaOscuro: Boolean
        get() = prefs?.getBoolean(TEMA_OSCURO, true) ?: true
        set(valor) {
            prefs?.edit()?.putBoolean(TEMA_OSCURO, valor)?.apply()
        }

    /**
     * Quien esta adentro de la app ahora. Existe mientras no cierre sesion:
     * permite abrir la app sin señal y seguir trabajando. Se borra SOLO
     * cuando el chofer toca "Cerrar sesion".
     */
    var perfilGuardado: Perfil?
        get() = prefs?.getString(PERFIL, null)?.let {
            runCatching { json.decodeFromString(Perfil.serializer(), it) }.getOrNull()
        }
        set(valor) {
            val editor = prefs?.edit() ?: return
            if (valor == null) editor.remove(PERFIL)
            else editor.putString(PERFIL, json.encodeToString(Perfil.serializer(), valor))
            editor.apply()
        }

    /** Nombre de la empresa, para mostrarlo en Configuracion aun sin señal. */
    var empresaGuardada: String?
        get() = prefs?.getString(EMPRESA, null)
        set(valor) {
            val editor = prefs?.edit() ?: return
            if (valor.isNullOrBlank()) editor.remove(EMPRESA) else editor.putString(EMPRESA, valor)
            editor.apply()
        }

    // ------------------------------------------------------------------
    // Credenciales para entrar sin señal, una por chofer que uso el telefono.
    // No se borran al cerrar sesion: justamente sirven para volver a entrar.
    // ------------------------------------------------------------------

    private val serializadorLista = ListSerializer(CredencialSinSenal.serializer())

    private fun todasLasCredenciales(): List<CredencialSinSenal> =
        prefs?.getString(CREDENCIALES, null)?.let {
            runCatching { json.decodeFromString(serializadorLista, it) }.getOrNull()
        } ?: emptyList()

    fun credencialDe(usuario: String): CredencialSinSenal? =
        todasLasCredenciales().firstOrNull { it.usuario == usuario }

    fun guardarCredencial(credencial: CredencialSinSenal) {
        val resto = todasLasCredenciales().filter { it.usuario != credencial.usuario }
        prefs?.edit()
            ?.putString(CREDENCIALES, json.encodeToString(serializadorLista, resto + credencial))
            ?.apply()
    }

    /** Borra la huella de un chofer: ya no va a poder entrar sin señal. */
    fun borrarCredencial(usuario: String) {
        val resto = todasLasCredenciales().filter { it.usuario != usuario }
        prefs?.edit()
            ?.putString(CREDENCIALES, json.encodeToString(serializadorLista, resto))
            ?.apply()
    }
}
