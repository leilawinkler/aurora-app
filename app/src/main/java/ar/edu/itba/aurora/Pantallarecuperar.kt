package ar.edu.itba.aurora

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

/**
 * "Olvide mi contraseña". El chofer escribe su usuario y su mail de contacto;
 * si coinciden, le llega por mail una contraseña temporal nueva.
 *
 * Se comporta igual que en la web: una vez enviado el pedido, los campos
 * quedan bloqueados y el boton desaparece, para que no se pidan varias
 * contraseñas seguidas (cada pedido invalida la anterior).
 */
@Composable
fun PantallaRecuperar(
    temaOscuro: Boolean = true,
    onCambiarTema: (Boolean) -> Unit = {},
    usuarioInicial: String = "",
    cargando: Boolean = false,
    mensajeOk: String? = null,
    error: String? = null,
    onEnviar: (usuario: String, mail: String) -> Unit,
    onVolver: () -> Unit
) {
    var usuario by remember { mutableStateOf(usuarioInicial.trim()) }
    var mail by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }

    val enviado = mensajeOk != null
    val bloqueado = cargando || enviado

    EscenaLogin(temaOscuro = temaOscuro, onCambiarTema = onCambiarTema) {

        ExplicacionLogin(
            "Ingresá tu usuario y el mail con el que te dieron de alta. " +
                "Si coinciden, te mandamos una contraseña temporal nueva.",
            temaOscuro
        )

        AvisoMail(temaOscuro)

        CampoLogin(
            oscuro = temaOscuro,
            etiqueta = "Usuario",
            valor = usuario,
            onCambio = { usuario = it; aviso = null },
            habilitado = !bloqueado,
            placeholder = "Ej: mgomez"
        )

        CampoLogin(
            oscuro = temaOscuro,
            etiqueta = "Mail",
            valor = mail,
            onCambio = { mail = it; aviso = null },
            habilitado = !bloqueado,
            placeholder = "nombre@ejemplo.com",
            teclado = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        val problema = aviso ?: error
        if (problema != null && !enviado) MensajeLogin(problema, TipoMensaje.ERROR, temaOscuro)
        if (mensajeOk != null) MensajeLogin(mensajeOk, TipoMensaje.OK, temaOscuro)

        if (!enviado) {
            BotonLogin(
                texto = if (cargando) "Enviando..." else "Enviar contraseña nueva",
                habilitado = !cargando,
                onClick = {
                    if (usuario.isBlank() || mail.isBlank()) {
                        aviso = "Completá tu usuario y tu mail."
                    } else {
                        aviso = null
                        onEnviar(usuario.trim().lowercase(), mail.trim().lowercase())
                    }
                }
            )
        }

        EnlaceLogin("Volver al ingreso", onClick = onVolver, habilitado = !cargando)
    }
}
