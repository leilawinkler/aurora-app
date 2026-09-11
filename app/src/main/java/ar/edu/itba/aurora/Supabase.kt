package ar.edu.itba.aurora

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

// Conexion a Supabase. Es el equivalente del config.js de la web.
//
// Estos dos valores estan en el panel de Supabase (boton "Connect" o
// Settings -> API Keys). La clave "publishable" es publica a proposito:
// lo que protege los datos son las reglas de la base (RLS), no esta clave.
//
// La clave secreta (sb_secret_...) NUNCA va aca. Solo la usa la Edge Function
// del lado del servidor.
const val SUPABASE_URL = "https://mdyczweuwhpfyioavngm.supabase.co"
const val SUPABASE_CLAVE = "sb_publishable_1x2LOakgxRB0TFcmIxfJJQ_MZV--9Zy"

// Dominio interno con el que se arma el mail de login a partir del usuario.
// El chofer escribe "jperez" y por debajo se usa "jperez@aurora.local".
const val DOMINIO_LOGIN = "aurora.local"

// Cliente unico para toda la app.
//   Auth      -> login y sesion (la guarda sola en el dispositivo)
//   Postgrest -> consultas a las tablas (usuarios, viajes, eventos)
val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_CLAVE
) {
    install(Auth)
    install(Postgrest)
}