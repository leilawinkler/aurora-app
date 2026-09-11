package ar.edu.itba.aurora

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
}

/** Traduce los errores de Supabase a algo que entienda un chofer. */
fun mensajeDeError(e: Throwable): String {
    val m = e.message ?: return "No se pudo ingresar."
    return when {
        m.contains("Invalid login credentials", true) -> "Usuario o contraseña incorrectos."
        m.contains("Email not confirmed", true) -> "El usuario todavia no esta habilitado."
        m.contains("Unable to resolve host", true) ||
                m.contains("failed to connect", true) ||
                m.contains("timeout", true) -> "Sin conexion a internet."
        else -> m
    }
}