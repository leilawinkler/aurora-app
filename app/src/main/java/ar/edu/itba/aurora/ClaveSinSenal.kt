package ar.edu.itba.aurora

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * La "huella" de la contraseña para entrar sin señal.
 *
 * Nunca se guarda la contraseña. Se guarda el resultado de pasarla por
 * PBKDF2: una cuenta que va en un solo sentido (de la contraseña sale la
 * huella, pero de la huella no se puede volver a la contraseña) y que es
 * lenta a proposito, para que probar contraseñas al azar contra la huella
 * sea muy costoso.
 *
 * La "sal" es un numero al azar distinto para cada chofer: hace que dos
 * choferes con la misma contraseña tengan huellas distintas.
 *
 * Se usa PBKDF2 con SHA-1 porque es la variante que existe desde Android 7
 * (la de SHA-256 recien aparece en Android 8). Para este uso es igual de
 * segura: lo que la hace fuerte es la cantidad de vueltas.
 */
object ClaveSinSenal {

    // Vueltas de la cuenta. Mas vueltas = mas seguro pero mas lento. Con
    // este numero, en un telefono viejo tarda una fraccion de segundo.
    const val ITERACIONES = 20_000

    private const val ALGORITMO = "PBKDF2WithHmacSHA1"
    private const val LARGO_BITS = 256

    fun salNueva(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Calcula la huella. Corre fuera de la pantalla porque tarda un poco. */
    suspend fun huella(clave: String, sal: String, iteraciones: Int = ITERACIONES): String =
        withContext(Dispatchers.Default) {
            val spec = PBEKeySpec(
                clave.toCharArray(),
                Base64.decode(sal, Base64.NO_WRAP),
                iteraciones,
                LARGO_BITS
            )
            try {
                val bytes = SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                spec.clearPassword()
            }
        }

    /**
     * Compara la huella de lo que escribio el chofer con la guardada.
     * MessageDigest.isEqual tarda lo mismo acierte o no, para no dar pistas.
     */
    suspend fun coincide(clave: String, credencial: CredencialSinSenal): Boolean {
        val calculada = huella(clave, credencial.sal, credencial.iteraciones)
        return MessageDigest.isEqual(
            Base64.decode(calculada, Base64.NO_WRAP),
            Base64.decode(credencial.huella, Base64.NO_WRAP)
        )
    }
}
