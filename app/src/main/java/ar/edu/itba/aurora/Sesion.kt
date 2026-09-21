package ar.edu.itba.aurora

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Ficha del usuario logueado, tal como esta en la tabla "usuarios".
// @Serializable = "esta clase se puede armar a partir del JSON que devuelve
// Supabase". @SerialName sirve cuando el nombre de la columna y el del campo
// en Kotlin no coinciden (empresa_id -> empresaId).
@Serializable
data class Perfil(
    val id: String,
    val rol: String,
    val nombre: String,
    val apellido: String,
    val usuario: String,
    @SerialName("empresa_id") val empresaId: String? = null,
    @SerialName("debe_cambiar_password") val debeCambiarPassword: Boolean = false
) {
    val nombreCompleto: String get() = "$nombre $apellido"
}

// Todo lo que tiene que ver con entrar, salir y saber quien esta logueado.
/** Resultado de un ingreso: quien entro y si fue con o sin señal. */
data class Ingreso(val perfil: Perfil, val sinConexion: Boolean)

/** Resultado de abrir la app: entra directo, o va a la pantalla de ingreso. */
sealed class Arranque {
    data class Adentro(val perfil: Perfil, val sinConexion: Boolean) : Arranque()
    data class Afuera(val motivo: String?) : Arranque()
}

object Sesion {

    // Dias que puede pasar un chofer entrando sin señal. Pasado ese plazo
    // necesita entrar una vez con señal: es la forma de enterarse, por
    // ejemplo, de que el administrador lo dio de baja.
    const val DIAS_VALIDEZ_SIN_SENAL = 7
    private const val INTENTOS_ANTES_DE_BLOQUEAR = 5
    private const val MINUTOS_DE_BLOQUEO = 5

    // La contraseña que escribio el chofer al entrar, SOLO en memoria (nunca
    // en disco). Sirve para abrir la sesion con Supabase sin molestarlo
    // cuando vuelve la señal. Se pierde si Android cierra la app.
    @Volatile
    private var claveEnMemoria: String? = null

    /** Espera a que la libreria termine de leer la sesion guardada en el dispositivo. */
    suspend fun esperarInicio() = supabase.auth.awaitInitialization()

    /**
     * Ingreso con usuario + contraseña.
     *
     * Primero intenta con señal, como siempre. Si falla por falta de señal
     * (y solo en ese caso: una contraseña mal con señal es una contraseña
     * mal), prueba sin señal contra la huella guardada en el telefono.
     */
    suspend fun entrar(usuario: String, clave: String): Result<Ingreso> {
        val u = usuario.trim().lowercase()
        val conSenal = runCatching { entrarConSenal(u, clave) }
        val error = conSenal.exceptionOrNull() ?: return Result.success(Ingreso(conSenal.getOrThrow(), false))
        if (!esErrorDeRed(error)) return Result.failure(error)
        return entrarSinSenal(u, clave).map { Ingreso(it, sinConexion = true) }
    }

    private suspend fun entrarConSenal(usuario: String, clave: String): Perfil {
        val mail = usuario + "@" + DOMINIO_LOGIN

        supabase.auth.signInWith(Email) {
            email = mail
            password = clave
        }

        val p = perfil()
        if (p == null) {
            supabase.auth.signOut()
            error("El usuario no tiene ficha cargada en el sistema.")
        }

        // Mismo orden que la web: ficha -> cuenta habilitada -> rol.
        val motivo = motivoDeBloqueo()
        if (motivo != null) {
            supabase.auth.signOut()
            error(motivo)
        }

        if (p.rol != "chofer") {
            supabase.auth.signOut()
            error("Esta app es solo para conductores. Ingresá desde la web.")
        }

        recordarIngreso(p, clave)
        return p
    }

    /** Despues de un ingreso con señal: deja todo listo para entrar sin señal. */
    private suspend fun recordarIngreso(p: Perfil, clave: String) {
        claveEnMemoria = clave
        Preferencias.perfilGuardado = p
        val sal = ClaveSinSenal.salNueva()
        Preferencias.guardarCredencial(
            CredencialSinSenal(
                usuario = p.usuario.lowercase(),
                perfil = p,
                sal = sal,
                huella = ClaveSinSenal.huella(clave, sal),
                iteraciones = ClaveSinSenal.ITERACIONES,
                ultimoIngresoConSenalMs = System.currentTimeMillis()
            )
        )
    }

    /** Ingreso sin señal, contra la huella guardada en el telefono. */
    private suspend fun entrarSinSenal(usuario: String, clave: String): Result<Perfil> = runCatching {
        val cred = Preferencias.credencialDe(usuario)
            ?: error(
                "No hay conexión. Para entrar sin señal, primero tenés que haber " +
                    "ingresado una vez con señal en este teléfono."
            )

        val ahora = System.currentTimeMillis()
        if (ahora < cred.bloqueadoHastaMs) {
            val minutos = ((cred.bloqueadoHastaMs - ahora) / 60_000) + 1
            error("Demasiados intentos. Probá de nuevo en $minutos minutos.")
        }

        val vence = cred.ultimoIngresoConSenalMs + DIAS_VALIDEZ_SIN_SENAL * 24L * 3_600_000L
        if (ahora > vence) {
            error(
                "Pasaron más de $DIAS_VALIDEZ_SIN_SENAL días sin que entres con señal. " +
                    "Necesitás conexión para volver a ingresar."
            )
        }

        if (!ClaveSinSenal.coincide(clave, cred)) {
            val fallos = cred.fallos + 1
            val bloqueo = fallos >= INTENTOS_ANTES_DE_BLOQUEAR
            Preferencias.guardarCredencial(
                cred.copy(
                    fallos = if (bloqueo) 0 else fallos,
                    bloqueadoHastaMs = if (bloqueo) ahora + MINUTOS_DE_BLOQUEO * 60_000L else 0L
                )
            )
            error("Usuario o contraseña incorrecta")
        }

        Preferencias.guardarCredencial(cred.copy(fallos = 0, bloqueadoHastaMs = 0L))
        claveEnMemoria = clave
        Preferencias.perfilGuardado = cred.perfil
        cred.perfil
    }

    /**
     * Decide que hacer al abrir la app. Si el chofer no cerro sesion, entra
     * directo, haya señal o no: la app lo conoce por la ficha guardada.
     */
    suspend fun alAbrir(): Arranque {
        esperarInicio()
        val guardado = Preferencias.perfilGuardado

        // Con señal y sesion de Supabase: se refresca la ficha y se hacen
        // los mismos controles que al ingresar.
        if (supabase.auth.currentUserOrNull() != null) {
            val enLinea = runCatching { perfil() }.getOrNull()
            if (enLinea != null) {
                val motivo = motivoDeBloqueo()
                if (motivo != null) {
                    // La cuenta fue dada de baja: esto no es un corte de
                    // señal, es una decision del administrador.
                    expulsar()
                    return Arranque.Afuera(motivo)
                }
                if (enLinea.rol != "chofer") {
                    salir()
                    return Arranque.Afuera(null)
                }
                Preferencias.perfilGuardado = enLinea
                return Arranque.Adentro(enLinea, sinConexion = false)
            }
        }

        // Sin señal (o con la sesion de Supabase sin renovar): vale la ficha.
        if (guardado != null) return Arranque.Adentro(guardado, sinConexion = true)
        return Arranque.Afuera(null)
    }

    /**
     * Intenta tener sesion con Supabase para poder subir. La llama el
     * sincronizador. Nunca saca al chofer de la app: si no puede, devuelve
     * false y se reintenta mas tarde.
     */
    suspend fun reconectar(): Boolean {
        if (supabase.auth.currentUserOrNull() != null) return true

        // 1) La sesion guardada, renovada ahora que quizas hay señal
        runCatching { supabase.auth.loadFromStorage() }
        if (supabase.auth.currentUserOrNull() != null) return true

        // 2) La contraseña que escribio al entrar, si sigue en memoria
        val clave = claveEnMemoria ?: return false
        val p = Preferencias.perfilGuardado ?: return false
        val intento = runCatching {
            supabase.auth.signInWith(Email) {
                email = p.usuario.lowercase() + "@" + DOMINIO_LOGIN
                password = clave
            }
            recordarIngreso(p, clave)
            true
        }
        // Si fallo por algo que no es la señal (por ejemplo, le cambiaron la
        // contraseña mientras estaba sin señal), la de memoria ya no sirve:
        // se descarta para que la app le pida la nueva.
        val error = intento.exceptionOrNull()
        if (error != null && !esErrorDeRed(error)) claveEnMemoria = null
        return intento.getOrDefault(false)
    }

    /** true si hace falta pedirle la contraseña al chofer para poder subir. */
    val necesitaClave: Boolean
        get() = claveEnMemoria == null && supabase.auth.currentUserOrNull() == null

    /**
     * El chofer escribe su contraseña desde el aviso de "hay viajes sin
     * subir". Necesita señal.
     */
    suspend fun reingresarClave(clave: String): Result<Unit> = runCatching {
        val p = Preferencias.perfilGuardado ?: error("No hay un chofer adentro de la app.")
        supabase.auth.signInWith(Email) {
            email = p.usuario.lowercase() + "@" + DOMINIO_LOGIN
            password = clave
        }
        recordarIngreso(p, clave)
    }

    /**
     * Pregunta a la base si la cuenta sigue habilitada. Es la misma funcion
     * que usa la web ("cuenta_habilitada"): la decide el servidor porque el
     * telefono no puede ver, por ejemplo, si el administrador de la empresa
     * fue dado de baja.
     *
     * Devuelve el texto para mostrarle al chofer si NO puede entrar, o null
     * si esta todo bien. Si no se pudo consultar (sin señal), devuelve null:
     * igual que la web, no se bloquea a nadie por un problema de conexion.
     */
    suspend fun motivoDeBloqueo(): String? {
        val estado = runCatching {
            supabase.postgrest.rpc("cuenta_habilitada").data.trim().trim('"')
        }.getOrNull() ?: return null

        return when (estado) {
            "activa", "" -> null
            "usuario_inactivo" -> "Tu cuenta se encuentra desactivada. Comunicate con tu administrador."
            "empresa_inactiva", "empresa_sin_admin" -> "La cuenta de tu empresa se encuentra desactivada."
            "sin_ficha" -> "El usuario no tiene ficha cargada en el sistema."
            else -> "No podés ingresar en este momento."
        }
    }

    /** Ficha del usuario logueado, o null si no hay sesion. */
    suspend fun perfil(): Perfil? {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return null
        return supabase.from("usuarios")
            .select(
                Columns.list(
                    "id", "rol", "nombre", "apellido",
                    "usuario", "empresa_id", "debe_cambiar_password"
                )
            ) {
                filter { eq("id", uid) }
            }
            .decodeSingleOrNull()
    }

    /**
     * Cierra la sesion. Es lo UNICO que saca al chofer de la app, y solo
     * pasa cuando el lo pide (o si el administrador lo dio de baja).
     *
     * La huella para entrar sin señal NO se borra: si cierra sesion en el
     * medio de la ruta, puede volver a entrar sin señal con su contraseña.
     */
    suspend fun salir() {
        claveEnMemoria = null
        Preferencias.perfilGuardado = null
        Preferencias.empresaGuardada = null
        runCatching { supabase.auth.signOut() }
        // Si el aviso al servidor fallo por falta de señal, igual se borra
        // la sesion guardada en el telefono.
        runCatching { supabase.auth.clearSession() }
    }

    /**
     * Saca al chofer porque lo dieron de baja (a el, o al administrador de
     * su empresa). Ademas de cerrar sesion, borra la huella de su contraseña:
     * si no, podria volver a entrar sin señal.
     */
    suspend fun expulsar() {
        Preferencias.perfilGuardado?.usuario?.lowercase()?.let {
            Preferencias.borrarCredencial(it)
        }
        salir()
    }

    /**
     * Cambia la contraseña del chofer logueado y marca en su ficha que ya
     * no necesita cambiarla.
     */
    suspend fun cambiarPassword(nueva: String): Result<Unit> = runCatching {
        supabase.auth.updateUser { password = nueva }

        // La huella para entrar sin señal pasa a ser la de la nueva.
        Preferencias.perfilGuardado?.let {
            recordarIngreso(it.copy(debeCambiarPassword = false), nueva)
        }

        // Apagar el campo debe_cambiar_password. Se intenta primero con la
        // funcion del servidor (ver supabase-marcar-password.sql) y, si no
        // esta creada, con un update directo. Si ninguna de las dos funciona
        // no importa: la contraseña ya quedo cambiada igual.
        val uid = supabase.auth.currentUserOrNull()?.id
        val porFuncion = runCatching {
            supabase.postgrest.rpc("marcar_password_cambiada")
        }
        if (porFuncion.isFailure && uid != null) {
            runCatching {
                supabase.from("usuarios").update(MarcaPassword(false)) {
                    filter { eq("id", uid) }
                }
            }
        }
        Unit
    }

    /**
     * "Olvide mi contraseña". Llama a la Edge Function recuperar-password con
     * el usuario y el mail de contacto. No hace falta estar logueado.
     *
     * Se usa una conexion HTTP comun (sin librerias nuevas) para no tocar
     * las versiones del proyecto.
     *
     * Devuelve el mensaje para mostrar si salio bien, o el error si no.
     */
    suspend fun recuperarPassword(usuario: String, mail: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cuerpo = JSONObject()
                    .put("usuario", usuario.trim().lowercase())
                    .put("mail", mail.trim().lowercase())
                    .toString()

                val conexion = URL("$SUPABASE_URL/functions/v1/recuperar-password")
                    .openConnection() as HttpURLConnection
                try {
                    conexion.requestMethod = "POST"
                    conexion.connectTimeout = 15_000
                    conexion.readTimeout = 20_000
                    conexion.doOutput = true
                    conexion.setRequestProperty("Content-Type", "application/json")
                    conexion.setRequestProperty("apikey", SUPABASE_CLAVE)
                    conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }

                    val codigo = conexion.responseCode
                    val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                    val texto = flujo?.bufferedReader()?.use { it.readText() } ?: ""
                    val respuesta = runCatching { JSONObject(texto) }.getOrNull()

                    if (codigo in 200..299) {
                        respuesta?.optString("mensaje")?.takeIf { it.isNotBlank() }
                            ?: "Si el usuario y el mail coinciden, te llega un mail."
                    } else {
                        error(
                            respuesta?.optString("error")?.takeIf { it.isNotBlank() }
                                ?: "No se pudo enviar el pedido (codigo $codigo)."
                        )
                    }
                } finally {
                    conexion.disconnect()
                }
            }
        }
}

@Serializable
private data class MarcaPassword(
    @SerialName("debe_cambiar_password") val debeCambiarPassword: Boolean
)

/** Traduce los errores de Supabase a algo que entienda un chofer. */
/**
 * true si el error es por falta de señal (y no, por ejemplo, una contraseña
 * incorrecta). Recorre la cadena de causas porque la libreria envuelve los
 * errores de red en otros.
 */
fun esErrorDeRed(e: Throwable): Boolean {
    var actual: Throwable? = e
    while (actual != null) {
        if (actual is java.io.IOException) return true
        val m = actual.message?.lowercase() ?: ""
        if ("unable to resolve host" in m || "failed to connect" in m ||
            "timeout" in m || "timed out" in m || "network is unreachable" in m ||
            "connection refused" in m || "software caused connection abort" in m
        ) return true
        actual = actual.cause
    }
    return false
}

fun mensajeDeError(e: Throwable): String {
    val m = e.message ?: return "No se pudo completar la operación."
    val t = m.lowercase()
    return when {
        t.contains("invalid login credentials") -> "Usuario o contraseña incorrecta"
        t.contains("at least") -> "La contraseña es muy corta (mínimo 6 caracteres)."
        t.contains("should be different") -> "La contraseña nueva tiene que ser distinta a la actual."
        t.contains("email not confirmed") -> "El usuario todavía no está habilitado."
        t.contains("unable to resolve host") || t.contains("failed to connect") ||
            t.contains("timeout") || t.contains("network") ->
            "No hay conexión. Probá de nuevo en un rato."
        t.contains("permission") || t.contains("policy") || t.contains("row-level") ->
            "No tenés permiso para hacer esa operación."
        t.contains("jwt") || t.contains("401") -> "La sesión expiró, volvé a entrar."
        else -> m
    }
}
