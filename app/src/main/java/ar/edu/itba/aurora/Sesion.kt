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
object Sesion {

    /** Espera a que la libreria termine de leer la sesion guardada en el dispositivo. */
    suspend fun esperarInicio() = supabase.auth.awaitInitialization()

    /**
     * Login con usuario + contraseña (no con mail).
     * Devuelve el perfil si salio bien, o el error si algo fallo.
     */
    suspend fun entrar(usuario: String, clave: String): Result<Perfil> = runCatching {
        val mail = usuario.trim().lowercase() + "@" + DOMINIO_LOGIN

        supabase.auth.signInWith(Email) {
            email = mail
            password = clave
        }

        val p = perfil()
        if (p == null) {
            supabase.auth.signOut()
            error("Tu usuario no tiene ficha cargada en el sistema.")
        }
        if (p.rol != "chofer") {
            supabase.auth.signOut()
            error("Este usuario no es conductor. La app es solo para conductores.")
        }
        p
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

    suspend fun salir() = supabase.auth.signOut()

    /**
     * Cambia la contraseña del chofer logueado y marca en su ficha que ya
     * no necesita cambiarla.
     */
    suspend fun cambiarPassword(nueva: String): Result<Unit> = runCatching {
        supabase.auth.updateUser { password = nueva }

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
fun mensajeDeError(e: Throwable): String {
    val m = e.message ?: return "No se pudo ingresar."
    return when {
        m.contains("Invalid login credentials", true) -> "Usuario o contraseña incorrectos."
        m.contains("at least", true) -> "La contraseña es muy corta (minimo 6 caracteres)."
        m.contains("should be different", true) -> "La contraseña nueva tiene que ser distinta a la actual."
        m.contains("Email not confirmed", true) -> "El usuario todavia no esta habilitado."
        m.contains("Unable to resolve host", true) ||
                m.contains("failed to connect", true) ||
                m.contains("timeout", true) -> "Sin conexion a internet."
        else -> m
    }
}